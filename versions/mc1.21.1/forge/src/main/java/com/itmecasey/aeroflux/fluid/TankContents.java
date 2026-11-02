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
 * The flux a broken Flux Tank carries on its item: which fluid, and how much of it.
 *
 * <p>Forge 1.21.1 has no ready-made fluid type for item data, and its {@link FluidStack} is mutable
 * and counts two stacks of the same fluid as equal whatever their amounts, so two tank items holding
 * different amounts would stack together. A record compares both and never changes.
 */
public record TankContents(Fluid fluid, int amount) {
    public static final Codec<TankContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(TankContents::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(TankContents::amount)
    ).apply(instance, TankContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TankContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), TankContents::fluid,
            ByteBufCodecs.VAR_INT, TankContents::amount,
            TankContents::new);

    /** The contents of a tank holding this stack. */
    public static TankContents of(FluidStack stack) {
        return new TankContents(stack.getFluid(), stack.getAmount());
    }

    /** The same fluid and amount as a fresh stack, ready to go back into a tank. */
    public FluidStack toStack() {
        return new FluidStack(fluid, amount);
    }
}
