package com.itmecasey.aeroflux.machine.recipe;

import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Reads and writes {@link FluxRecipe}s for one machine, from datapack JSON and over the network.
 * Every machine has its own instance, so the recipe a file defines belongs to the folder it is in.
 */
public final class FluxRecipeSerializer implements RecipeSerializer<FluxRecipe> {
    private final MapCodec<FluxRecipe> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, FluxRecipe> streamCodec;

    public FluxRecipeSerializer(MachineType machine) {
        this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                FluxRecipe.Input.CODEC.listOf().fieldOf("inputs").forGetter(FluxRecipe::inputs),
                FluxRecipe.Output.CODEC.listOf().fieldOf("outputs").forGetter(FluxRecipe::outputs),
                Codec.intRange(1, 1_000_000).optionalFieldOf("time", 200).forGetter(FluxRecipe::time),
                Codec.intRange(0, 1_000_000).optionalFieldOf("flux", 0).forGetter(FluxRecipe::flux),
                Codec.STRING.optionalFieldOf("flux_type").forGetter(recipe ->
                        Optional.ofNullable(recipe.fluxType()).map(type -> type.name().toLowerCase(Locale.ROOT))),
                Codec.intRange(0, 1_000_000).optionalFieldOf("water", 0).forGetter(FluxRecipe::water),
                FluxRecipe.Output.CODEC.listOf().optionalFieldOf("silk_outputs", List.of()).forGetter(FluxRecipe::silkOutputs)
        ).apply(instance, (inputs, outputs, time, flux, fluxType, water, silkOutputs) ->
                new FluxRecipe(machine, inputs, outputs, time, flux, fluxType.map(FluxRecipeSerializer::parseFlux).orElse(null),
                        water, silkOutputs)));

        this.streamCodec = StreamCodec.of(
                (buf, recipe) -> write(buf, recipe),
                buf -> read(machine, buf));
    }

    private static FluxType parseFlux(String name) {
        return FluxType.valueOf(name.toUpperCase(Locale.ROOT));
    }

    @Override
    public MapCodec<FluxRecipe> codec() {
        return codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, FluxRecipe> streamCodec() {
        return streamCodec;
    }

    private static void write(RegistryFriendlyByteBuf buf, FluxRecipe recipe) {
        buf.writeVarInt(recipe.inputs().size());
        for (FluxRecipe.Input input : recipe.inputs()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, input.ingredient());
            buf.writeVarInt(input.count());
        }
        writeOutputs(buf, recipe.outputs());
        buf.writeVarInt(recipe.time());
        buf.writeVarInt(recipe.flux());
        ByteBufCodecs.VAR_INT.encode(buf, recipe.fluxType() == null ? -1 : recipe.fluxType().ordinal());
        buf.writeVarInt(recipe.water());
        writeOutputs(buf, recipe.silkOutputs());
    }

    private static void writeOutputs(RegistryFriendlyByteBuf buf, List<FluxRecipe.Output> outputs) {
        buf.writeVarInt(outputs.size());
        for (FluxRecipe.Output output : outputs) {
            ItemStack.STREAM_CODEC.encode(buf, output.stack());
            buf.writeFloat(output.chance());
        }
    }

    private static List<FluxRecipe.Output> readOutputs(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<FluxRecipe.Output> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            outputs.add(new FluxRecipe.Output(stack, buf.readFloat()));
        }
        return outputs;
    }

    private static FluxRecipe read(MachineType machine, RegistryFriendlyByteBuf buf) {
        int inputCount = buf.readVarInt();
        List<FluxRecipe.Input> inputs = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            inputs.add(new FluxRecipe.Input(ingredient, buf.readVarInt()));
        }
        List<FluxRecipe.Output> outputs = readOutputs(buf);
        int time = buf.readVarInt();
        int flux = buf.readVarInt();
        int type = ByteBufCodecs.VAR_INT.decode(buf);
        int water = buf.readVarInt();
        List<FluxRecipe.Output> silkOutputs = readOutputs(buf);
        return new FluxRecipe(machine, inputs, outputs, time, flux,
                type < 0 || type >= FluxType.values().length ? null : FluxType.values()[type], water, silkOutputs);
    }
}
