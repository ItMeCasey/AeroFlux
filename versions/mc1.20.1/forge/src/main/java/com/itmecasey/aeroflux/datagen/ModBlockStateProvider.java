package com.itmecasey.aeroflux.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Generates block states and block models. */
public class ModBlockStateProvider extends BlockStateProvider {
    private final ExistingFileHelper existingFileHelper;

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AeroFlux.MODID, existingFileHelper);
        this.existingFileHelper = existingFileHelper;
    }

    @Override
    protected void registerStatesAndModels() {
        fluidBlock(ModBlocks.AERO_FLUX.get(), "aero_flux");
        fluidBlock(ModBlocks.MOON_FLUX.get(), "moon_flux");

        // The first-tier machine blocks have their blockstates and models delivered with the art
        // (assets-src) and are left alone. Every higher tier gets a model of its own that reuses
        // the first tier's shape and only swaps in that tier's textures.
        for (MachineType machine : MachineType.values()) {
            Map<String, ResourceLocation> copperTextures = deliveredTextures(machine);
            for (Tier tier : Tier.values()) {
                if (tier != Tier.COPPER) {
                    horizontalBlock(ModBlocks.machine(machine, tier).get(), tierModel(machine, tier, copperTextures));
                }
            }
        }
    }

    /**
     * A child of the delivered first-tier model that overrides nothing but its textures. Each
     * texture becomes its tier variant where the art has one ({@code furnace_front} becomes
     * {@code furnace_front_iron}); the rest stay shared with copper. Every key is written out, so
     * the file lists the full texture set of that tier at a glance.
     */
    private ModelFile tierModel(MachineType machine, Tier tier, Map<String, ResourceLocation> copperTextures) {
        String name = ModBlocks.machineBlockName(machine, tier);
        BlockModelBuilder model = models().withExistingParent("block/" + name, modLoc("block/" + machine.blockId()));

        List<ResourceLocation> missing = new ArrayList<>();
        for (Map.Entry<String, ResourceLocation> entry : copperTextures.entrySet()) {
            ResourceLocation texture = tierTexture(entry.getValue(), tier);
            if (textureExists(texture)) {
                model.texture(entry.getKey(), texture);
            } else {
                missing.add(texture);
            }
        }
        // ModelBuilder would also refuse a missing texture, but one at a time; this names them all.
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Model block/" + name + " names textures that are not in the mod's resources: " + missing);
        }
        return model;
    }

    /**
     * The texture a tier uses in place of a copper one: {@code <name>_<tier>} if the art has it,
     * or {@code <base>_<tier>} for a texture that is already named {@code <base>_copper} (the
     * plant grower's frame). Without a tier variant the copper texture is shared.
     */
    private ResourceLocation tierTexture(ResourceLocation copper, Tier tier) {
        String path = copper.getPath();
        String suffix = "_" + Tier.COPPER.id();
        String base = path.endsWith(suffix) ? path.substring(0, path.length() - suffix.length()) : path;
        ResourceLocation variant = Ids.of(copper.getNamespace(), base + "_" + tier.id());
        return textureExists(variant) ? variant : copper;
    }

    private boolean textureExists(ResourceLocation texture) {
        return existingFileHelper.exists(texture, PackType.CLIENT_RESOURCES, ".png", "textures");
    }

    /**
     * The textures the delivered first-tier model of a machine names, in file order. References to
     * other keys ({@code "#side"}) are skipped: the child inherits them and they follow the swap.
     */
    private Map<String, ResourceLocation> deliveredTextures(MachineType machine) {
        ResourceLocation location = modLoc("block/" + machine.blockId());
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        try (Reader reader = existingFileHelper.getResource(location, PackType.CLIENT_RESOURCES, ".json", "models").openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                    String value = entry.getValue().getAsString();
                    if (!value.startsWith("#")) {
                        textures.put(entry.getKey(), Ids.parse(value));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the delivered model " + location, e);
        }
        if (textures.isEmpty()) {
            throw new IllegalStateException("The delivered model " + location + " names no textures to swap per tier");
        }
        return textures;
    }

    /**
     * A fluid block is drawn by the fluid renderer, so its model only supplies the particle
     * texture (the break and splash particles) from the shared still texture.
     */
    private void fluidBlock(Block block, String name) {
        ModelFile model = models().getBuilder("block/" + name)
                .texture("particle", Ids.mod("block/flux_still"));
        simpleBlock(block, model);
    }
}
