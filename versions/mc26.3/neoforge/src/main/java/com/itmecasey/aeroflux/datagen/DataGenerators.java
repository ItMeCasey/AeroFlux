package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

/**
 * Entry points for data generation ({@code gradlew runData}). Generated files are written to
 * {@code src/generated/resources} and committed, so normal builds never need to run this.
 * Minecraft 26.1 splits generation in two: assets come from the client run, data from the server run.
 */
@EventBusSubscriber(modid = AeroFlux.MODID)
public final class DataGenerators {
    private DataGenerators() {
    }

    /** Registers the client-side providers: translations and item/block models. */
    @SubscribeEvent
    public static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(ModLanguageProvider::new);
        // A constructor reference, not a lambda: a lambda would become a method of this class that
        // names the client-only model provider, and the dedicated server fails while scanning this
        // class for events.
        event.createProvider(ModModelProvider::new);
    }

    /** Registers the server-side providers: recipes, block tags and loot tables. */
    @SubscribeEvent
    public static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider(ModBlockTagProvider::new);
        event.createProvider(ModFluidTagProvider::new);
    }

    /**
     * Minecraft 26.3 generates recipes and loot tables as datapack registry
     * entries, so they are gathered here and not through providers of their own.
     */
    @SubscribeEvent
    public static void gatherRegistryEntries(GatherDataRegistryEntriesEvent event) {
        event.add(Registries.LOOT_TABLE, new LootTableProvider(Set.of(), List.of(
                        new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK))))
                .recipe(ModRecipeProvider::new);
    }
}
