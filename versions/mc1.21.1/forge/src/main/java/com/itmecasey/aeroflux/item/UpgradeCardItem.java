package com.itmecasey.aeroflux.item;

import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.lamp.FluxLampBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * A card that raises a placed machine, collector, tank or converter one tier, in place. Sneak and use it on
 * the block one tier below the card's own: the block becomes the next tier and keeps everything,
 * including its contents, progress, side settings, redstone mode, upgrades and name. Plain use
 * still opens the block's screen as usual.
 */
public class UpgradeCardItem extends Item {
    private final Tier tier;

    public UpgradeCardItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The tier this card upgrades a block to. */
    public Tier tier() {
        return tier;
    }

    /** The tier a block must be for this card to apply. */
    private Tier from() {
        return Tier.values()[tier.ordinal() - 1];
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Tier blockTier = tierOf(state.getBlock());
        if (blockTier == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.FAIL;
        }

        Block next = blockTier == from() ? nextTier(state.getBlock()) : null;
        if (next == null) {
            player.displayClientMessage(Component.translatable("message.aeroflux.card_wrong_tier",
                    tierName(blockTier), tierName(from())), true);
            return InteractionResult.FAIL;
        }
        swap((ServerLevel) level, pos, state, next);
        if (!player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.aeroflux.card_upgraded", next.getName()), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Replaces the block with its next tier and moves the block entity's data across. The old
     * block entity is taken out first, so breaking it spills nothing; the new one loads the saved
     * data, and since a higher tier only ever holds more, nothing is lost.
     */
    private static void swap(ServerLevel level, BlockPos pos, BlockState oldState, Block next) {
        BlockEntity old = level.getBlockEntity(pos);
        CompoundTag data = old == null ? null : old.saveWithoutMetadata(level.registryAccess());
        level.removeBlockEntity(pos);

        BlockState newState = next.defaultBlockState();
        for (Property<?> property : oldState.getProperties()) {
            newState = copy(oldState, newState, property);
        }
        level.setBlock(pos, newState, Block.UPDATE_ALL);

        BlockEntity fresh = level.getBlockEntity(pos);
        if (fresh != null && data != null) {
            fresh.loadWithComponents(data, level.registryAccess());
            fresh.setChanged();
        }
        level.sendBlockUpdated(pos, newState, newState, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                12, 0.4, 0.4, 0.4, 0.0);
    }

    /** Copies one property across when the new block has it too (facing and lit, for machines). */
    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    /** The tier of an upgradable block, or {@code null} for anything else. */
    private static @Nullable Tier tierOf(Block block) {
        if (block instanceof FluxMachineBlock machine) {
            return machine.tier();
        }
        if (block instanceof FluxCollectorBlock collector) {
            return collector.tier();
        }
        if (block instanceof FluxTankBlock tank) {
            return tank.tier();
        }
        if (block instanceof FluxConverterBlock converter) {
            return converter.tier();
        }
        if (block instanceof FluxLampBlock lamp) {
            return lamp.tier();
        }
        return null;
    }

    /** The same kind of block one tier up, or {@code null} when it is already the top tier. */
    private static @Nullable Block nextTier(Block block) {
        Tier current = tierOf(block);
        if (current == null || current.ordinal() + 1 >= Tier.values().length) {
            return null;
        }
        Tier up = Tier.values()[current.ordinal() + 1];
        if (block instanceof FluxMachineBlock machine) {
            return ModBlocks.machine(machine.machine(), up).get();
        }
        if (block instanceof FluxCollectorBlock) {
            return ModBlocks.collector(up).get();
        }
        if (block instanceof FluxConverterBlock converter) {
            return ModBlocks.converter(converter.type(), up).get();
        }
        if (block instanceof FluxLampBlock lamp) {
            return ModBlocks.lamp(lamp.kind(), up).get();
        }
        return ModBlocks.tank(up).get();
    }

    private static Component tierName(Tier tier) {
        return Component.translatable("tier.aeroflux." + tier.id().toLowerCase(Locale.ROOT));
    }

    /**
     * Says what the card does. Vanilla marked this hook deprecated in favor of tooltip data
     * components, but the text depends only on the card's tier, and the hook is still called.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.aeroflux.upgrade_card", tierName(from()), tierName(tier)));
    }
}
