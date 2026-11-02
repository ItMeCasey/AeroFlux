package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * The AeroFlux advancement tree. It follows the way the mod is played: Flux Dust first, then the
 * first collector and its two fluxes, pipes, the first machines, tier upgrades and finally the
 * chunk loader. Every step is "have this item", so it also counts for items taken from chests.
 */
final class ModAdvancements implements AdvancementSubProvider {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("gui/advancements/backgrounds/stone");

    private Consumer<AdvancementHolder> output;

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
        this.output = output;
        AdvancementHolder root = step(null, "root", ModItems.FLUX_DUST.get(), AdvancementType.TASK, ModItems.FLUX_DUST.get());

        AdvancementHolder collector = step(root, "collector", ModBlocks.collector(Tier.COPPER).get(), AdvancementType.TASK,
                ModBlocks.collector(Tier.COPPER).get());
        step(collector, "aero_flux", ModItems.AERO_FLUX_BUCKET.get(), AdvancementType.TASK, ModItems.AERO_FLUX_BUCKET.get());
        step(collector, "moon_flux", ModItems.MOON_FLUX_BUCKET.get(), AdvancementType.TASK, ModItems.MOON_FLUX_BUCKET.get());
        step(collector, "tank", ModBlocks.tank(Tier.COPPER).get(), AdvancementType.TASK, ModBlocks.tank(Tier.COPPER).get());

        AdvancementHolder pipe = step(root, "pipe", ModItems.pipe(PipeType.ITEM, Tier.COPPER).get(), AdvancementType.TASK,
                copperPipes());
        step(pipe, "smart_pipes", ModItems.FILTER_UPGRADE.get(), AdvancementType.TASK,
                ModItems.FILTER_UPGRADE.get(), ModItems.PRIORITY_UPGRADE.get());

        AdvancementHolder furnace = step(root, "furnace", ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get(),
                AdvancementType.TASK, tiersOf(MachineType.FURNACE));
        step(furnace, "alloy_smelter", ModBlocks.machine(MachineType.ALLOY_SMELTER, Tier.COPPER).get(), AdvancementType.TASK,
                tiersOf(MachineType.ALLOY_SMELTER));
        step(furnace, "plant_grower", ModBlocks.machine(MachineType.PLANT_GROWER, Tier.COPPER).get(), AdvancementType.TASK,
                tiersOf(MachineType.PLANT_GROWER));
        step(furnace, "enchanter", ModBlocks.machine(MachineType.ENCHANTER, Tier.COPPER).get(), AdvancementType.TASK,
                tiersOf(MachineType.ENCHANTER));
        step(furnace, "speed_upgrade", ModItems.SPEED_UPGRADE.get(), AdvancementType.TASK, ModItems.SPEED_UPGRADE.get());

        AdvancementHolder card = step(furnace, "upgrade_card", ModItems.upgradeCard(Tier.IRON).get(), AdvancementType.TASK,
                cards());
        step(card, "fluxium", ModBlocks.machine(MachineType.FURNACE, Tier.FLUXIUM).get(), AdvancementType.GOAL, fluxiumBlocks());

        step(root, "chunk_loader", ModBlocks.chunkLoader(Tier.COPPER).get(), AdvancementType.TASK, chunkLoaders());
    }

    /**
     * One advancement, done by having any one of {@code items}. Its title and description are the
     * {@code advancements.aeroflux.<name>.title} and {@code .description} translations.
     */
    private AdvancementHolder step(@Nullable AdvancementHolder parent, String name, ItemLike icon, AdvancementType type,
                                   ItemLike... items) {
        String key = "advancements." + AeroFlux.MODID + "." + name;
        Advancement.Builder builder = Advancement.Builder.advancement()
                .display(icon, Component.translatable(key + ".title"), Component.translatable(key + ".description"),
                        parent == null ? BACKGROUND : null, type, parent != null, parent != null, false);
        if (parent != null) {
            builder.parent(parent);
        }
        for (ItemLike item : items) {
            builder.addCriterion(item.asItem().toString(), InventoryChangeTrigger.TriggerInstance.hasItems(item));
        }
        return builder.requirements(AdvancementRequirements.Strategy.OR).save(output, AeroFlux.MODID + ":" + name);
    }

    private static ItemLike[] copperPipes() {
        List<ItemLike> pipes = new ArrayList<>();
        for (PipeType type : List.of(PipeType.FLUID, PipeType.ITEM, PipeType.ENERGY, PipeType.REDSTONE)) {
            pipes.add(ModItems.pipe(type, Tier.COPPER).get());
        }
        return pipes.toArray(ItemLike[]::new);
    }

    private static ItemLike[] tiersOf(MachineType machine) {
        List<ItemLike> blocks = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            blocks.add(ModBlocks.machine(machine, tier).get());
        }
        return blocks.toArray(ItemLike[]::new);
    }

    private static ItemLike[] cards() {
        List<ItemLike> cards = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            if (tier != Tier.COPPER) {
                cards.add(ModItems.upgradeCard(tier).get());
            }
        }
        return cards.toArray(ItemLike[]::new);
    }

    private static ItemLike[] fluxiumBlocks() {
        List<ItemLike> blocks = new ArrayList<>();
        blocks.add(ModBlocks.collector(Tier.FLUXIUM).get());
        blocks.add(ModBlocks.tank(Tier.FLUXIUM).get());
        for (MachineType machine : MachineType.values()) {
            blocks.add(ModBlocks.machine(machine, Tier.FLUXIUM).get());
        }
        return blocks.toArray(ItemLike[]::new);
    }

    private static ItemLike[] chunkLoaders() {
        List<ItemLike> loaders = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            loaders.add(ModBlocks.chunkLoader(tier).get());
        }
        return loaders.toArray(ItemLike[]::new);
    }
}
