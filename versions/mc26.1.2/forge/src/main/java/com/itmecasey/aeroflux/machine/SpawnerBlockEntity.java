package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFluxConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import org.jetbrains.annotations.Nullable;

/**
 * The Moon Spawner: a mob farm with no mobs. Each cycle it makes the mob on the spawn egg in its
 * first slot without putting it in the world, rolls that mob's loot, and drops the loot in its
 * output slots. On NeoForge a weapon in the second slot makes the loot roll as a player kill;
 * Forge 26.1 has no fake players, so here the slot is only kept to match the layout and the
 * loot never counts as a player kill. Bosses never work, and the server can switch spawners off
 * or cap the mobs per cycle in the config.
 */
public class SpawnerBlockEntity extends FluxMachineBlockEntity {
    private static final int SPAWN_TICKS = 400;
    private static final int EGG_SLOT = 0;

    public SpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return slot == EGG_SLOT ? stack.getItem() instanceof SpawnEggItem : stack.isDamageableItem();
    }

    private @Nullable EntityType<?> mob() {
        EntityType<?> type = SpawnEggItem.getType(stackIn(EGG_SLOT));
        return type == null || type.builtInRegistryHolder().is(Tags.EntityTypes.BOSSES) ? null : type;
    }

    /** Mobs per cycle: one for copper and iron, two for gold and diamond, three above, never past the config. */
    private int mobsPerCycle() {
        return Math.min(AeroFluxConfig.spawnerMaxMobsPerCycle(), 1 + tier().ordinal() / 2);
    }

    @Override
    protected @Nullable Job findJob() {
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
        Entity entity = type.create(server, EntitySpawnReason.SPAWNER);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            return List.of();
        }
        living.setPos(Vec3.atCenterOf(worldPosition.above()));
        Optional<ResourceKey<LootTable>> key = living.getLootTable();
        List<ItemStack> loot = new ArrayList<>();
        if (key.isPresent()) {
            LootTable table = server.getServer().reloadableRegistries().getLootTable(key.get());
            LootParams.Builder params = new LootParams.Builder(server)
                    .withParameter(LootContextParams.THIS_ENTITY, living)
                    .withParameter(LootContextParams.ORIGIN, living.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().generic());
            table.getRandomItems(params.create(LootContextParamSets.ENTITY), loot::add);
        }
        living.discard();
        return loot;
    }
}
