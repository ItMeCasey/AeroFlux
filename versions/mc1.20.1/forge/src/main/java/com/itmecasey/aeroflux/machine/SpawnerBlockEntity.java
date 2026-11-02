package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFluxConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * The Moon Spawner: a mob farm with no mobs. Each cycle it makes the mob on the spawn egg in its
 * first slot without putting it in the world, rolls that mob's loot, and drops the loot in its
 * output slots. A weapon in the second slot counts as the Looting upgrade: the loot is rolled as
 * if a player killed the mob with that weapon, so Looting on it applies and player-only drops (a
 * blaze rod, wither skeleton skulls) can come. Without it there is no player kill credit. Bosses
 * never work, and the server can switch spawners off or cap the mobs per cycle in the config.
 */
public class SpawnerBlockEntity extends FluxMachineBlockEntity {
    private static final int SPAWN_TICKS = 400;
    private static final int EGG_SLOT = 0;
    private static final int WEAPON_SLOT = 1;

    public SpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return slot == EGG_SLOT ? stack.getItem() instanceof SpawnEggItem : stack.isDamageableItem();
    }

    @Nullable
    private EntityType<?> mob() {
        ItemStack egg = stackIn(EGG_SLOT);
        EntityType<?> type = egg.getItem() instanceof SpawnEggItem spawnEgg ? spawnEgg.getType(egg.getTag()) : null;
        return type == null || type.builtInRegistryHolder().is(Tags.EntityTypes.BOSSES) ? null : type;
    }

    /** Mobs per cycle: one for copper and iron, two for gold and diamond, three above, never past the config. */
    private int mobsPerCycle() {
        return Math.min(AeroFluxConfig.spawnerMaxMobsPerCycle(), 1 + tier().ordinal() / 2);
    }

    @Nullable
    @Override
    protected Job findJob() {
        if (!AeroFluxConfig.spawnerEnabled() || mob() == null) {
            return null;
        }
        return new Job() {
            @Override
            public Object key() {
                return "spawn";
            }

            @Override
            public int baseTicks() {
                return SPAWN_TICKS;
            }

            /** Loot varies, so the spawner only needs one free output slot to go on. */
            @Override
            public boolean outputFits() {
                return canFitOutputs(List.of(new ItemStack(Items.BARRIER)));
            }

            @Override
            public void complete() {
                EntityType<?> type = mob();
                if (type == null || !(level instanceof ServerLevel server)) {
                    return;
                }
                List<ItemStack> loot = new ArrayList<>();
                for (int i = 0; i < mobsPerCycle(); i++) {
                    loot.addAll(rollLoot(server, type));
                }
                putOutputs(loot);
            }
        };
    }

    /** The loot of one mob of this kind, as if it died next to the spawner. */
    private List<ItemStack> rollLoot(ServerLevel server, EntityType<?> type) {
        Entity entity = type.create(server);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            return List.of();
        }
        living.setPos(Vec3.atCenterOf(worldPosition.above()));
        ResourceLocation key = living.getLootTable();
        List<ItemStack> loot = new ArrayList<>();
        if (key != null) {
            LootTable table = server.getServer().getLootData().getLootTable(key);
            ItemStack weapon = stackIn(WEAPON_SLOT);
            Player killer = null;
            DamageSource source = server.damageSources().generic();
            if (!weapon.isEmpty()) {
                killer = FakePlayerFactory.getMinecraft(server);
                killer.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
                source = server.damageSources().playerAttack(killer);
            }
            LootParams.Builder params = new LootParams.Builder(server)
                    .withParameter(LootContextParams.THIS_ENTITY, living)
                    .withParameter(LootContextParams.ORIGIN, living.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer);
            if (killer != null) {
                params = params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer).withLuck(killer.getLuck());
            }
            table.getRandomItems(params.create(LootContextParamSets.ENTITY), loot::add);
            if (killer != null) {
                killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        living.discard();
        return loot;
    }
}
