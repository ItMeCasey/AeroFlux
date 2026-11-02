package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.FluxType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The eight machines of the plan. Each one comes in all six tiers, and what differs between
 * them is the number of slots, which flux powers them and what they make. Everything here is
 * what the game needs to know before any recipe: the block and screen are built from it.
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
    PLANT_GROWER("plant_grower", "Plant Grower", 1, 3, EnumSet.of(FluxType.AERO, FluxType.MOON));

    private final String blockId;
    private final String displayName;
    private final int inputSlots;
    private final int outputSlots;
    private final Set<FluxType> acceptedFlux;

    MachineType(String blockId, String displayName, int inputSlots, int outputSlots, Set<FluxType> acceptedFlux) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.acceptedFlux = acceptedFlux;
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

    /** The kinds of resource with face settings: every machine takes items and fluid. */
    public Set<SideResource> resources() {
        return EnumSet.of(SideResource.ITEMS, SideResource.FLUID);
    }

    /** Whether the machine's work comes from data-driven recipes of its own type (the furnace uses vanilla's, the enchanters use rules). */
    public boolean hasOwnRecipes() {
        return this == GRINDER || this == SAWMILL || this == ALLOY_SMELTER || this == INFUSER || this == PLANT_GROWER;
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
