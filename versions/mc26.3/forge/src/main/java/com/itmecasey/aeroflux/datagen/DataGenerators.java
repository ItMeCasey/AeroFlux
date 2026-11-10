package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.MultiRegistryBootstrap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.RegistryDataBuilder;
import net.minecraftforge.data.event.GatherDataEvent;

/**
 * Data generation ({@code gradlew runData}). Generated files are written to
 * {@code src/generated/resources} and committed, so normal builds never need to run this.
 * The mod constructor registers {@link #gatherData} on the mod event bus group.
 */
public final class DataGenerators {
    private DataGenerators() {
    }

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new ModLanguageProvider(output));
        generator.addProvider(event.includeClient(), new ModModelProvider(output, existingFileHelper));

        generator.addProvider(event.includeServer(), new ModBlockTagProvider(output, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeServer(), new ModFluidTagProvider(output, lookupProvider, existingFileHelper));

        // Minecraft 26.3 makes recipes, loot tables and advancements datapack registry entries, so they
        // are bootstrapped into a reloadable registry layer and written by its generator.
        RegistryDataBuilder entries = RegistryDataBuilder.of().modid(AeroFlux.MODID).reloadable(registries -> registries
                .add(Registries.LOOT_TABLE, new LootTableProvider(Set.of(), List.of(
                        new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK))))
                .add(Registries.ADVANCEMENT, new AdvancementProvider(List.of(ModAdvancements::new)))
                .add(new MultiRegistryBootstrap() {
                    @Override
                    public Set<ResourceKey<? extends Registry<?>>> requestedRegistries() {
                        return Set.of(Registries.RECIPE, Registries.ADVANCEMENT);
                    }

                    @Override
                    public void run(BootstrapGetter registries) {
                        new ModRecipeProvider(registries.get(Registries.RECIPE), registries.get(Registries.ADVANCEMENT)).generate();
                    }
                }));
        generator.addProvider(event.includeServer(), entries.reloadableGenerator(output));
    }
}
