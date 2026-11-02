package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.FluxType;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every machine. Each one comes in all six tiers, and what differs between them is the number of
 * slots, which flux powers them, any extra tanks and what they make. Everything here is what the
 * game needs to know before any recipe: the block and screen are built from it.
 */
public enum MachineType {
    /** Smelts with vanilla smelting recipes; paid for with Flux instead of fuel. */
    FURNACE("flux_furnace", "Flux Furnace", 1, 1, EnumSet.of(FluxType.AERO)),
    /** Ore to double raw material, bones to bonemeal and so on. One product and a chance of a second. */
    GRINDER("flux_grinder", "Flux Grinder", 1, 2, EnumSet.of(FluxType.AERO)),
    /** Logs to planks, with a chance of sawdust. */
    SAWMILL("flux_sawmill", "Flux Sawmill", 1, 2, EnumSet.of(FluxType.AERO)),
    /** Two or three ingredients into an alloy. */
    ALLOY_SMELTER("alloy_smelter", "Alloy Smelter", 3, 1, EnumSet.of(FluxType.AERO)),
    /** An item and an additive soaked in Moon Flux become something new. */
    INFUSER("flux_infuser", "Flux Infuser", 2, 1, EnumSet.of(FluxType.MOON)),
    /** Applies the enchantments of a book to an item for lapis and Moon Flux. */
    ENCHANTER("flux_enchanter", "Flux Enchanter", 3, 1, EnumSet.of(FluxType.MOON)),
    /** Strips the enchantments from an item onto a fresh book. The item survives. */
    DISENCHANTER("flux_disenchanter", "Flux Disenchanter", 2, 2, EnumSet.of(FluxType.MOON)),
    /** Grows crops, saplings and flowers from seeds. Runs on either flux. */
    PLANT_GROWER("plant_grower", "Plant Grower", 1, 3, EnumSet.of(FluxType.AERO, FluxType.MOON)),
    /**
     * An auto crafting table. The nine input slots are the crafting grid itself; it always keeps
     * one item in each filled slot, so the pattern stays put while pipes top the slots up.
     */
    CRAFTER("flux_crafter", "Flux Crafter", 9, 2, EnumSet.of(FluxType.AERO)),
    /** Ore, a little water and Flux into three times the raw material. */
    PURIFIER("flux_purifier", "Flux Purifier", 1, 2, EnumSet.of(FluxType.AERO), List.of(ExtraTank.WATER)),
    /** A powered sieve whose tier is its mesh; some recipes need water in its tank. */
    SIFTER("flux_sifter", "Flux Sifter", 1, 6, EnumSet.of(FluxType.AERO), List.of(ExtraTank.WATER)),
    /** Aero Flux and Moon Flux into Twilight Flux; an empty bucket in the slot is filled from it. */
    BLENDER("flux_blender", "Flux Blender", 1, 1, EnumSet.of(FluxType.AERO),
            List.of(ExtraTank.MOON_FLUX, ExtraTank.TWILIGHT_FLUX)),
    /** Day for Aero Flux, night for Moon Flux, and clear or rain, from three buttons. */
    SKY_CONTROLLER("sky_controller", "Sky Controller", 0, 0, EnumSet.of(FluxType.AERO), List.of(ExtraTank.MOON_FLUX)),
    /** Pulls dropped items in its range into its slots. The input slot is a filter, never used up. */
    MAGNET("flux_magnet", "Flux Magnet", 1, 6, EnumSet.of(FluxType.MOON)),
    /** Feeds the animals around it the food in its slot so they breed. */
    BREEDER("animal_breeder", "Animal Breeder", 1, 0, EnumSet.of(FluxType.AERO)),
    /** Repairs a damaged tool or armor piece with Bottles o' Enchanting, keeping its enchantments. */
    REPAIRER("flux_repairer", "Flux Repairer", 2, 1, EnumSet.of(FluxType.MOON)),
    /** Rolls the loot of the mob on the spawn egg in its slot, with no mob in the world. */
    SPAWNER("moon_spawner", "Moon Spawner", 2, 6, EnumSet.of(FluxType.MOON));

    private final String blockId;
    private final String displayName;
    private final int inputSlots;
    private final int outputSlots;
    private final Set<FluxType> acceptedFlux;
    private final List<ExtraTank> extraTanks;

    MachineType(String blockId, String displayName, int inputSlots, int outputSlots, Set<FluxType> acceptedFlux) {
        this(blockId, displayName, inputSlots, outputSlots, acceptedFlux, List.of());
    }

    MachineType(String blockId, String displayName, int inputSlots, int outputSlots, Set<FluxType> acceptedFlux,
                List<ExtraTank> extraTanks) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.acceptedFlux = acceptedFlux;
        this.extraTanks = extraTanks;
    }

    /** Lowercase name used in registry ids, such as {@code "alloy_smelter"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The id of the copper (first tier) block, such as {@code "flux_furnace"}; the art is named after it. */
    public String blockId() {
        return blockId;
    }

    /** The English name without the tier, such as "Flux Furnace"; the translation files use it. */
    public String displayName() {
        return displayName;
    }

    /** Number of input slots; they come first in the machine's inventory. */
    public int inputSlots() {
        return inputSlots;
    }

    /** Number of output slots; they follow the inputs. */
    public int outputSlots() {
        return outputSlots;
    }

    /** The flux types the machine's tank takes. */
    public Set<FluxType> acceptedFlux() {
        return acceptedFlux;
    }

    /** The tanks the machine has besides its Flux tank, in the order the screen shows them. */
    public List<ExtraTank> extraTanks() {
        return extraTanks;
    }

    /** The kinds of resource with face settings: every machine takes items and fluid. */
    public Set<SideResource> resources() {
        return EnumSet.of(SideResource.ITEMS, SideResource.FLUID);
    }

    /** Whether the machine's work comes from data-driven recipes of its own type (the furnace uses vanilla's, the enchanters use rules). */
    public boolean hasOwnRecipes() {
        return this == GRINDER || this == SAWMILL || this == ALLOY_SMELTER || this == INFUSER || this == PLANT_GROWER
                || this == PURIFIER || this == SIFTER;
    }

    /** The machine with this id, or {@code null}. */
    public static MachineType byId(String id) {
        for (MachineType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
