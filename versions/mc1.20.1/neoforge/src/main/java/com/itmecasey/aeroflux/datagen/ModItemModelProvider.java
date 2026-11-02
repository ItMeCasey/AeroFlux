package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
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
        basicItem(ModItems.FLUX_DUST.get());
        basicItem(ModItems.SAWDUST.get());
        fluidBucket("aero_flux_bucket", ModFluids.AERO_FLUX.get());
        fluidBucket("moon_flux_bucket", ModFluids.MOON_FLUX.get());
        fluidBucket("twilight_flux_bucket", ModFluids.TWILIGHT_FLUX.get());

    }

    /** A bucket that draws the fluid it holds, tinted, through Forge's fluid container model. */
    private void fluidBucket(String name, Fluid fluid) {
        withExistingParent(name, new ResourceLocation("forge", "item/bucket"))
                .customLoader(DynamicFluidContainerModelBuilder::begin)
                .fluid(fluid);
    }
}
