package com.itmecasey.aeroflux.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * The fluid a Flux Tank item carries: which fluid and how much.
 *
 * <p>Forge's {@link FluidStack} would do, but it counts two stacks of the same fluid as equal
 * whatever their amounts, and item components must compare by value. Two tanks holding different
 * amounts must not stack, so this small record stands in for it.
 */
public record TankContents(Fluid fluid, int amount) {
    public static final Codec<TankContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(TankContents::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(TankContents::amount)
    ).apply(instance, TankContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TankContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), TankContents::fluid,
            ByteBufCodecs.VAR_INT, TankContents::amount,
            TankContents::new);

    /** What a tank holding this stack carries; the stack must not be empty. */
    public static TankContents of(FluidStack stack) {
        return new TankContents(stack.getFluid(), stack.getAmount());
    }

    /** The carried fluid as a Forge stack, ready to go back into a tank. */
    public FluidStack toStack() {
        return new FluidStack(fluid, amount);
    }
}
