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
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Builds the serializer of {@link FluxRecipe}s for one machine, which reads and writes them from
 * datapack JSON and over the network. Every machine gets its own, so the recipe a file defines
 * belongs to the folder it is in. Since 26.1 a serializer is a plain pair of codecs rather than
 * an interface to implement, so this is a factory instead of a class.
 */
public final class FluxRecipeSerializer {
    private FluxRecipeSerializer() {
    }

    public static RecipeSerializer<FluxRecipe> create(MachineType machine) {
        MapCodec<FluxRecipe> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                FluxRecipe.Input.CODEC.listOf().fieldOf("inputs").forGetter(FluxRecipe::inputs),
                FluxRecipe.Output.CODEC.listOf().fieldOf("outputs").forGetter(FluxRecipe::outputs),
                Codec.intRange(1, 1_000_000).optionalFieldOf("time", 200).forGetter(FluxRecipe::time),
                Codec.intRange(0, 1_000_000).optionalFieldOf("flux", 0).forGetter(FluxRecipe::flux),
                Codec.STRING.optionalFieldOf("flux_type").forGetter(recipe ->
                        Optional.ofNullable(recipe.fluxType()).map(type -> type.name().toLowerCase(Locale.ROOT)))
        ).apply(instance, (inputs, outputs, time, flux, fluxType) ->
                new FluxRecipe(machine, inputs, outputs, time, flux, fluxType.map(FluxRecipeSerializer::parseFlux).orElse(null))));

        StreamCodec<RegistryFriendlyByteBuf, FluxRecipe> streamCodec = StreamCodec.of(
                FluxRecipeSerializer::write,
                buf -> read(machine, buf));
        return new RecipeSerializer<>(codec, streamCodec);
    }

    private static FluxType parseFlux(String name) {
        return FluxType.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static void write(RegistryFriendlyByteBuf buf, FluxRecipe recipe) {
        buf.writeVarInt(recipe.inputs().size());
        for (FluxRecipe.Input input : recipe.inputs()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, input.ingredient());
            buf.writeVarInt(input.count());
        }
        buf.writeVarInt(recipe.outputs().size());
        for (FluxRecipe.Output output : recipe.outputs()) {
            ItemStackTemplate.STREAM_CODEC.encode(buf, output.item());
            buf.writeFloat(output.chance());
        }
        buf.writeVarInt(recipe.time());
        buf.writeVarInt(recipe.flux());
        ByteBufCodecs.VAR_INT.encode(buf, recipe.fluxType() == null ? -1 : recipe.fluxType().ordinal());
    }

    private static FluxRecipe read(MachineType machine, RegistryFriendlyByteBuf buf) {
        int inputCount = buf.readVarInt();
        List<FluxRecipe.Input> inputs = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            inputs.add(new FluxRecipe.Input(ingredient, buf.readVarInt()));
        }
        int outputCount = buf.readVarInt();
        List<FluxRecipe.Output> outputs = new ArrayList<>();
        for (int i = 0; i < outputCount; i++) {
            ItemStackTemplate item = ItemStackTemplate.STREAM_CODEC.decode(buf);
            outputs.add(new FluxRecipe.Output(item, buf.readFloat()));
        }
        int time = buf.readVarInt();
        int flux = buf.readVarInt();
        int type = ByteBufCodecs.VAR_INT.decode(buf);
        return new FluxRecipe(machine, inputs, outputs, time, flux,
                type < 0 || type >= FluxType.values().length ? null : FluxType.values()[type]);
    }
}
