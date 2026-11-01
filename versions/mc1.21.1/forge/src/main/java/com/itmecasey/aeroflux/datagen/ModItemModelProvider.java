package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.registry.ModFluids;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Generates the item models. Plain flat items use the standard generated-item model. */
public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AeroFlux.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        flatItem("flux_dust");
        fluidBucket("aero_flux_bucket", ModFluids.AERO_FLUX.get());
        fluidBucket("moon_flux_bucket", ModFluids.MOON_FLUX.get());
    }

    /** Registers a flat item model that uses the texture {@code item/<name>}. */
    private void flatItem(String name) {
        withExistingParent(name, mcLoc("item/generated")).texture("layer0", modLoc("item/" + name));
    }

    /** A bucket that draws the fluid it holds, tinted, through the loader's dynamic container model. */
    private void fluidBucket(String name, Fluid fluid) {
        withExistingParent(name, Ids.of("forge", "item/bucket"))
                .customLoader(DynamicFluidContainerModelBuilder::begin)
                .fluid(fluid);
    }
}
