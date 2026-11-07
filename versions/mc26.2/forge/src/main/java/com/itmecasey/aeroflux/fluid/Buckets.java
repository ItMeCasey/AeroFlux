package com.itmecasey.aeroflux.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Buckets on AeroFlux blocks. Forge 26.1 has item fluid handlers switched off for now (its
 * {@code FluidUtil.getFluidHandler(ItemStack)} always answers empty), so its bucket helpers do
 * nothing. This covers plain buckets, which is what a player carries Flux and water around in.
 */
public final class Buckets {
    private Buckets() {
    }

    /** The fluid in a filled bucket, a bucket's worth of it, or empty for anything else. */
    public static FluidStack contained(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket && bucket.getContent() != Fluids.EMPTY) {
            return new FluidStack(bucket.getContent(), FluidType.BUCKET_VOLUME);
        }
        return FluidStack.EMPTY;
    }

    /**
     * Pours the held bucket into the block, or fills an empty one from it, a whole bucket at a time.
     *
     * @param side the face the block is used from, or {@code null} for no face in particular
     * @return true if the bucket was used, so the click does nothing else
     */
    public static boolean interact(Player player, InteractionHand hand, Level level, BlockPos pos, @Nullable Direction side) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BucketItem bucket)) {
            return false;
        }
        IFluidHandler handler = FluidUtil.getFluidHandler(level, pos, side).resolve().orElse(null);
        if (handler == null) {
            return false;
        }
        // The client does not know what the block holds; it only keeps the bucket from also
        // placing or picking up fluid while the server decides.
        if (level.isClientSide()) {
            return true;
        }
        Fluid content = bucket.getContent();
        if (content != Fluids.EMPTY) {
            FluidStack pour = new FluidStack(content, FluidType.BUCKET_VOLUME);
            if (handler.fill(pour, IFluidHandler.FluidAction.SIMULATE) != FluidType.BUCKET_VOLUME) {
                return false;
            }
            handler.fill(pour, IFluidHandler.FluidAction.EXECUTE);
            player.setItemInHand(hand, BucketItem.getEmptySuccessItem(held, player));
            playSound(level, pos, content, true);
            return true;
        }
        FluidStack taken = handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
        Item filled = taken.isEmpty() ? Items.AIR : taken.getFluid().getBucket();
        if (taken.getAmount() != FluidType.BUCKET_VOLUME || filled == Items.AIR) {
            return false;
        }
        handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(filled)));
        playSound(level, pos, taken.getFluid(), false);
        return true;
    }

    private static void playSound(Level level, BlockPos pos, Fluid fluid, boolean emptying) {
        SoundEvent sound = fluid.getFluidType().getSound(emptying ? SoundActions.BUCKET_EMPTY : SoundActions.BUCKET_FILL);
        if (sound == null) {
            sound = emptying ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL;
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
