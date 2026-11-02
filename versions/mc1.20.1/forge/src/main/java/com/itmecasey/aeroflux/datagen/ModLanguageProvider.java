package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Locale;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Generates the American English ({@code en_us}) translations. */
public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, AeroFlux.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + AeroFlux.MODID, "AeroFlux");

        addItem(ModItems.FLUX_DUST, "Flux Dust");
        addItem(ModItems.AERO_FLUX_BUCKET, "Aero Flux Bucket");
        addItem(ModItems.MOON_FLUX_BUCKET, "Moon Flux Bucket");

        addBlock(ModBlocks.AERO_FLUX, "Aero Flux");
        addBlock(ModBlocks.MOON_FLUX, "Moon Flux");

        for (Tier tier : Tier.values()) {
            String name = displayName(tier);
            addBlock(ModBlocks.collector(tier), name + " Flux Collector");
            addBlock(ModBlocks.tank(tier), name + " Flux Tank");
        }

        addConduitTranslations();
        addMachineTranslations();

        // Fluid type names show in fluid gauges and tooltips from other mods.
        add("fluid_type." + AeroFlux.MODID + ".aero_flux", "Aero Flux");
        add("fluid_type." + AeroFlux.MODID + ".moon_flux", "Moon Flux");
    }

    /** Names of the machines and the new items, and every text of the machine screens. */
    private void addMachineTranslations() {
        addItem(ModItems.AERO_CRYSTAL, "Aero Crystal");
        addItem(ModItems.MOON_CRYSTAL, "Moon Crystal");
        addItem(ModItems.FLUXSTEEL_INGOT, "Fluxsteel Ingot");
        addItem(ModItems.AEROGOLD_INGOT, "Aerogold Ingot");
        addItem(ModItems.SAWDUST, "Sawdust");

        for (MachineType machine : MachineType.values()) {
            for (Tier tier : Tier.values()) {
                // The first tier is just "Flux Furnace"; the others put the tier in front.
                String name = tier == Tier.COPPER ? machine.displayName() : displayName(tier) + " " + machine.displayName();
                addBlock(ModBlocks.machine(machine, tier), name);
            }
        }

        add("gui.aeroflux.gauge.name", "Flux gauge");
        add("gui.aeroflux.gauge.empty", "Empty");
        add("gui.aeroflux.gauge.amount", "%s / %s mB");
        add("gui.aeroflux.gauge.percent", "%s%% full");
        add("gui.aeroflux.gauge.using", "Using %s mB/t");
        add("gui.aeroflux.progress.name", "Progress");
        add("gui.aeroflux.progress.percent", "Progress: %s%%");
        add("gui.aeroflux.progress.left", "%s s left");
        add("gui.aeroflux.status.name", "Machine status");

        String status = "machine." + AeroFlux.MODID + ".status.";
        add(status + "idle", "Idle");
        add(status + "idle.tooltip", "Nothing to work on.");
        add(status + "running", "Running");
        add(status + "running.tooltip", "Working. Burning Flux.");
        add(status + "no_flux", "Out of Flux");
        add(status + "no_flux.tooltip", "Not enough of the right Flux to keep working. Progress is kept.");
        add(status + "output_full", "Output full");
        add(status + "output_full.tooltip", "The output slots have no room. Take something out to continue.");

        String mode = "machine_mode." + AeroFlux.MODID + ".";
        add(mode + "import", "Import");
        add(mode + "import.tooltip", "Things may go in through this face.");
        add(mode + "export", "Export");
        add(mode + "export.tooltip", "Things may come out through this face.");
        add(mode + "open", "Both");
        add(mode + "open.tooltip", "Things may go in and come out through this face.");
        add(mode + "off", "Off");
        add(mode + "off.tooltip", "Pipes can attach, but nothing passes.");
        add(mode + "blocked", "Blocked");
        add(mode + "blocked.tooltip", "Hidden from pipes and hoppers on this face.");

        for (SideResource resource : SideResource.values()) {
            add("side_resource." + AeroFlux.MODID + "." + resource.id(), capitalize(resource.name()));
        }
        for (FaceLabel label : FaceLabel.values()) {
            add("face_label." + AeroFlux.MODID + "." + label.id(), capitalize(label.name()));
            add("face_label." + AeroFlux.MODID + "." + label.id() + ".short", switch (label) {
                case FRONT -> "Fr";
                case BACK -> "Bk";
                case LEFT -> "Lf";
                case RIGHT -> "Rt";
                case TOP -> "Tp";
                case BOTTOM -> "Bm";
            });
        }

        String side = "screen." + AeroFlux.MODID + ".side_config.";
        add(side + "title", "Side configuration");
        add(side + "toggle_hint", "Choose what each face of the machine does.");
        add(side + "all_off", "All Off");
        add(side + "all_off.tooltip", "Turns every face off for this kind of resource.");
        add(side + "copy_all", "Copy");
        add(side + "copy_all.tooltip", "Gives every face the mode of the selected face.");
        add(side + "reset", "Reset");
        add(side + "reset.tooltip", "Puts every face back to its default.");
        add(side + "face_mode", "%s: %s");
        add(side + "neighbor", "Next to: %s");
        add(side + "nothing", "Nothing");
    }

    /** Names for the conduit bundle, the pipes, and the wrench's messages. */
    private void addConduitTranslations() {
        addBlock(ModBlocks.CONDUIT_BUNDLE, "Conduit Bundle");
        addBlock(ModBlocks.PIPE_NODE, "Pipe Node");
        addItem(ModItems.FLUX_WRENCH, "Flux Wrench");

        for (PipeType type : PipeType.values()) {
            String name = pipeName(type);
            if (PipeTiers.isTiered(type)) {
                for (Tier tier : Tier.values()) {
                    addItem(ModItems.pipe(type, tier), displayName(tier) + " " + name + " Pipe");
                }
            } else {
                addItem(ModItems.pipe(type, Tier.COPPER), name + " Pipe");
            }
            add("pipe." + AeroFlux.MODID + "." + type.id(), name);
        }

        addItem(ModItems.SPEED_UPGRADE, "Speed Upgrade");
        addItem(ModItems.CAPACITY_UPGRADE, "Capacity Upgrade");
        for (PipeUpgrades.Kind kind : PipeUpgrades.Kind.values()) {
            add("upgrade." + AeroFlux.MODID + "." + kind.name().toLowerCase(Locale.ROOT), capitalize(kind.name()));
        }
        add("message." + AeroFlux.MODID + ".upgrade_installed", "%s upgrade added to %s pipe (%s of %s slots)");
        add("message." + AeroFlux.MODID + ".upgrade_full", "%s pipe has no free upgrade slots");

        for (Face face : Face.values()) {
            add("face." + AeroFlux.MODID + "." + face.name().toLowerCase(Locale.ROOT), capitalize(face.name()));
        }
        for (ConnectionMode mode : ConnectionMode.values()) {
            add("connection_mode." + AeroFlux.MODID + "." + mode.name().toLowerCase(Locale.ROOT), capitalize(mode.name()));
        }
        add("message." + AeroFlux.MODID + ".pipe_mode", "%s pipe, %s side: %s");

        add("distribution_mode." + AeroFlux.MODID + ".round_robin", "Round robin");
        add("distribution_mode." + AeroFlux.MODID + ".first_to_last", "First to last");
        add("distribution_mode." + AeroFlux.MODID + ".last_to_first", "Last to first");
        add("distribution_mode." + AeroFlux.MODID + ".random", "Random");
        add("message." + AeroFlux.MODID + ".pipe_distribution", "%s pipe distribution: %s");
        add("message." + AeroFlux.MODID + ".pipe_no_distribution", "%s pipes have no distribution setting");

        addConduitToolTranslations();
        addPipeScreenTranslations();
    }

    /** Texts of the pipe screen: headings, the speed lines and the button tooltips. */
    private void addPipeScreenTranslations() {
        String screen = "screen." + AeroFlux.MODID + ".pipe_config.";
        add(screen + "title", "%s Pipe (%s)");
        add(screen + "title_untiered", "%s Pipe");
        add(screen + "side", "Side: %s");
        add(screen + "modes", "Face mode");
        add(screen + "distribution", "Distribution");
        add(screen + "rate", "Throughput");
        add(screen + "upgrades", "Upgrades (%s of %s)");
        add(screen + "rate_items", "%s items every %s ticks");
        add(screen + "rate_fluid", "%s mB per tick");
        add(screen + "rate_gas", "%s mB per tick");
        add(screen + "rate_energy", "%s FE per tick");
        add(screen + "rate_channels", "%s redstone channels");
        add(screen + "rate_me_channels", "%s channels, handled by AE2");

        String mode = "connection_mode." + AeroFlux.MODID + ".";
        add(mode + "open.tooltip", "Connected. Moves contents both ways.");
        add(mode + "import.tooltip", "Connected. Only pulls from the neighbor into the network.");
        add(mode + "export.tooltip", "Connected. Only pushes from the network into the neighbor.");
        add(mode + "off.tooltip", "Connected but passive. The stub stays visible.");
        add(mode + "blocked.tooltip", "Not connected. Nothing can attach here.");

        String distribution = "distribution_mode." + AeroFlux.MODID + ".";
        add(distribution + "round_robin.tooltip", "Shares evenly and rotates who gets the leftover.");
        add(distribution + "first_to_last.tooltip", "Fills the first destination before moving on to the next.");
        add(distribution + "last_to_first.tooltip", "Fills the last destination first, then works backward.");
        add(distribution + "random.tooltip", "Fills destinations in a different random order each time.");
    }

    /** Names and messages of the Conduit Tool. */
    private void addConduitToolTranslations() {
        addItem(ModItems.CONDUIT_TOOL, "Conduit Tool");
        for (ToolMode mode : ToolMode.values()) {
            add("tool_mode." + AeroFlux.MODID + "." + mode.name().toLowerCase(Locale.ROOT), toolModeName(mode));
        }
        for (Tier tier : Tier.values()) {
            add("tier." + AeroFlux.MODID + "." + tier.id(), displayName(tier));
        }
        add("tooltip." + AeroFlux.MODID + ".tool_mode", "Mode: %s");
        add("message." + AeroFlux.MODID + ".tool_mode", "Conduit Tool: %s");
        add("message." + AeroFlux.MODID + ".tool_removed", "Removed the %s pipe");
        add("message." + AeroFlux.MODID + ".tool_removed_bundle", "Removed the bundle (%s pipes)");
        add("message." + AeroFlux.MODID + ".tool_run_start", "Run started on a %s pipe. Use the tool on the other end of the run.");
        add("message." + AeroFlux.MODID + ".tool_run_invalid", "A run must be a straight line of at most %s blocks");
        add("message." + AeroFlux.MODID + ".tool_run_removed", "Removed %s of the %s pipe along the run");
        add("message." + AeroFlux.MODID + ".tool_tier_replaced", "The %s pipe is now %s");
        add("message." + AeroFlux.MODID + ".tool_tier_none", "Hold a %s pipe of a different tier in your off hand");
        add("message." + AeroFlux.MODID + ".tool_copied", "Copied the settings of %s pipes");
        add("message." + AeroFlux.MODID + ".tool_pasted", "Pasted settings onto %s pipes");
        add("message." + AeroFlux.MODID + ".tool_nothing_copied", "Nothing copied yet");
        add("message." + AeroFlux.MODID + ".tool_no_network", "The %s pipe is not part of a network");
        add("message." + AeroFlux.MODID + ".tool_inspect",
                "%s network: %s bundles, %s sources, %s destinations, speed %s");
        add("message." + AeroFlux.MODID + ".tool_speed_items", "%s items every %s ticks");
        add("message." + AeroFlux.MODID + ".tool_speed_milli", "%s mB per tick");
        add("message." + AeroFlux.MODID + ".tool_speed_energy", "%s FE per tick");
        add("message." + AeroFlux.MODID + ".tool_speed_none", "not limited");
    }

    private static String toolModeName(ToolMode mode) {
        return switch (mode) {
            case EDIT -> "Edit";
            case REMOVE -> "Remove";
            case REMOVE_RUN -> "Remove run";
            case REPLACE_TIER -> "Replace tier";
            case COPY -> "Copy";
            case PASTE -> "Paste";
            case INSPECT -> "Inspect";
        };
    }

    private static String pipeName(PipeType type) {
        return type == PipeType.ME ? "ME" : type == PipeType.DENSE_ME ? "Dense ME" : capitalize(type.name());
    }

    /** "NORTH" becomes "North". */
    private static String capitalize(String upperCase) {
        return upperCase.charAt(0) + upperCase.substring(1).toLowerCase(Locale.ROOT);
    }

    /** "copper" becomes "Copper". */
    private static String displayName(Tier tier) {
        String id = tier.id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
