package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.EndpointSettings;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.PipeTiers;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.PipeUpgrades;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import com.itmecasey.aeroflux.compat.create.CreateCompat;
import com.itmecasey.aeroflux.core.configurator.Setting;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideResource;
import java.util.Locale;
import com.itmecasey.aeroflux.lamp.LampKind;
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
        addItem(ModItems.TWILIGHT_FLUX_BUCKET, "Twilight Flux Bucket");

        addBlock(ModBlocks.AERO_FLUX, "Aero Flux");
        addBlock(ModBlocks.MOON_FLUX, "Moon Flux");
        addBlock(ModBlocks.TWILIGHT_FLUX, "Twilight Flux");

        for (Tier tier : Tier.values()) {
            String name = displayName(tier);
            addBlock(ModBlocks.collector(tier), name + " Flux Collector");
            addBlock(ModBlocks.tank(tier), name + " Flux Tank");
        }

        addConduitTranslations();
        addMachineTranslations();
        addChunkLoaderTranslations();

        // Fluid type names show in fluid gauges and tooltips from other mods.
        add("fluid_type." + AeroFlux.MODID + ".aero_flux", "Aero Flux");
        add("fluid_type." + AeroFlux.MODID + ".moon_flux", "Moon Flux");
        add("fluid_type." + AeroFlux.MODID + ".twilight_flux", "Twilight Flux");
    }

    /** The machine sound subtitles and the creative-only blocks. */
    private void addSoundAndCreativeTranslations() {
        String subtitle = "subtitles." + AeroFlux.MODID + ".machine.";
        add(subtitle + "furnace", "Furnace running");
        add(subtitle + "grinder", "Grinder running");
        add(subtitle + "smelter", "Alloy Smelter running");
        add(subtitle + "sawmill", "Sawmill running");
        add(subtitle + "infuser", "Infuser running");
        add(subtitle + "enchanter", "Enchanter running");
        add(subtitle + "disenchanter", "Disenchanter running");
        add(subtitle + "grower", "Plant Grower running");
        add(subtitle + "start", "Machine powers up");
        add(subtitle + "stop", "Machine powers down");
        add(subtitle + "done", "Machine finishes");

        addBlock(ModBlocks.CREATIVE_TANK, "Creative Flux Tank");
        add("message." + AeroFlux.MODID + ".creative_tank.flux", "Creative Flux Tank now gives %s");
    }

    /** The flux badge and cost bar on the machine screens, and the collector and tank screens. */
    private void addFluxScreenTranslations() {
        add("gui.aeroflux.badge.name", "Flux type");
        add("gui.aeroflux.badge.takes.aero", "Runs on Aero Flux");
        add("gui.aeroflux.badge.takes.moon", "Runs on Moon Flux");
        add("gui.aeroflux.badge.takes.either", "Runs on Aero Flux or Moon Flux");
        add("gui.aeroflux.badge.has.aero", "Has Aero Flux");
        add("gui.aeroflux.badge.has.moon", "Has Moon Flux");
        add("gui.aeroflux.badge.has.twilight", "Has Twilight Flux");
        add("gui.aeroflux.badge.none", "No Flux in the tank");

        add("gui.aeroflux.cost.name", "Flux cost");
        add("gui.aeroflux.cost.no_job", "Nothing to work on");
        add("gui.aeroflux.cost.per_operation", "Per job: %s mB");
        add("gui.aeroflux.cost.in_tank", "In the tank: %s mB (%s jobs)");
        add("gui.aeroflux.cost.next_tick", "Next tick: %s mB");
        add("gui.aeroflux.cost.short", "Not enough Flux for the next tick");

        String collector = "gui.aeroflux.collector.";
        add(collector + "phase.day", "Day");
        add(collector + "phase.dusk", "Dusk");
        add(collector + "phase.night", "Night");
        add(collector + "phase.dawn", "Dawn");
        add(collector + "phase.always_day", "Always day");
        add(collector + "phase.always_night", "Always night");
        add(collector + "phase.always_both", "Day and night");
        add(collector + "clock", "%s, next change in %s");
        add(collector + "offer.making", "%s: %s");
        add(collector + "offer.none", "%s: nothing");
        add(collector + "offer.locked", "%s: waiting");
        add(collector + "sky.open", "Open sky");
        add(collector + "sky.blocked", "Sky blocked");
        add(collector + "sky.blocked_by", "Blocked by %s");
        add(collector + "sky.blocked_at", "At %s, %s, %s");
        add(collector + "sky.not_needed", "Sky not needed here");
        add(collector + "weather.clear", "Clear: %s%%");
        add(collector + "weather.rain", "Rain: %s%%");
        add(collector + "weather.thunder", "Thunder: %s%%");
        add(collector + "weather.ignored", "Weather ignored here");
        add(collector + "moon", "%s: %s%%");
        add(collector + "moon.0", "Full moon");
        add(collector + "moon.1", "Waning gibbous");
        add(collector + "moon.2", "Last quarter");
        add(collector + "moon.3", "Waning crescent");
        add(collector + "moon.4", "New moon");
        add(collector + "moon.5", "Waxing crescent");
        add(collector + "moon.6", "First quarter");
        add(collector + "moon.7", "Waxing gibbous");
        add(collector + "rate", "Making %s mB/t");
        add(collector + "rate.tooltip", "Made last tick: %s mB");
        add(collector + "out.tooltip", "Passed on to neighbors: %s mB/t");
        add(collector + "full.in", "Full in %s");
        add(collector + "full.now", "Full");
        add(collector + "full.never", "Not filling");

        add("gui.aeroflux.tank.percent", "%s%%");
        add("gui.aeroflux.tank.in", "In: %s mB/t");
        add("gui.aeroflux.tank.out", "Out: %s mB/t");
        add("gui.aeroflux.tank.empty_in", "Empty in %s");
        add("gui.aeroflux.tank.steady", "Steady");
    }

    /** Names of the machines and the new items, and every text of the machine screens. */
    private void addMachineTranslations() {
        addItem(ModItems.AERO_CRYSTAL, "Aero Crystal");
        addItem(ModItems.MOON_CRYSTAL, "Moon Crystal");
        addItem(ModItems.FLUXSTEEL_INGOT, "Fluxsteel Ingot");
        addItem(ModItems.AEROGOLD_INGOT, "Aerogold Ingot");
        addItem(ModItems.SAWDUST, "Sawdust");
        for (Tier tier : Tier.values()) {
            if (tier != Tier.COPPER) {
                addItem(ModItems.upgradeCard(tier), displayName(tier) + " Upgrade Card");
            }
        }
        add("tooltip." + AeroFlux.MODID + ".tank_contents", "Contains %s of %s");
        String holds = "tooltip." + AeroFlux.MODID + ".holds.";
        add(holds + "tank", "Holds %s");
        add(holds + "collector", "Stores up to %s");
        add(holds + "machine", "Flux tank: %s");
        add(holds + "converter_energy", "Flux tank: %s, holds %s FE");
        add(holds + "converter_ae", "Flux tank: %s, holds %s AE");
        add(holds + "chunk_loader", "Flux tank: %s");
        add(holds + "lamp", "Flux tank: %s");
        add("message." + AeroFlux.MODID + ".card_wrong_tier", "This block is %s; this card only upgrades %s");
        add("message." + AeroFlux.MODID + ".card_upgraded", "Upgraded to %s");
        for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
            for (Tier tier : Tier.values()) {
                addBlock(ModBlocks.converter(type, tier), tier == Tier.COPPER ? type.displayName() : displayName(tier) + " " + type.displayName());
            }
        }
        // The Stress converter only exists with Create, so it is named by its registry name here.
        for (Tier tier : Tier.values()) {
            add("block." + AeroFlux.MODID + "." + CreateCompat.converterName(tier), tier == Tier.COPPER
                    ? ConverterType.STRESS.displayName() : displayName(tier) + " " + ConverterType.STRESS.displayName());
        }
        add("gui." + AeroFlux.MODID + ".converter.buffer.fe", "Energy buffer");
        add("gui." + AeroFlux.MODID + ".converter.buffer.ae", "AE buffer");
        add("gui." + AeroFlux.MODID + ".converter.buffer.su", "Stress in use on the shaft's network");
        add("gui." + AeroFlux.MODID + ".converter.shaft_here", "The shaft comes out here.");
        add("gui." + AeroFlux.MODID + ".converter.no_shaft", "No shaft on this face.");
        add("gui." + AeroFlux.MODID + ".auto_insert", "Auto insert seeds");
        add("gui." + AeroFlux.MODID + ".chunk_loader.show_area", "Show Area");
        for (LampKind kind : LampKind.values()) {
            for (Tier tier : Tier.values()) {
                addBlock(ModBlocks.lamp(kind, tier), tier == Tier.COPPER ? kind.displayName() : displayName(tier) + " " + kind.displayName());
            }
        }
        add("gui." + AeroFlux.MODID + ".magnet_mode", "Filter mode");
        add("gui." + AeroFlux.MODID + ".magnet_mode.on", "Whitelist");
        add("gui." + AeroFlux.MODID + ".magnet_mode.off", "Blacklist");
        add("gui." + AeroFlux.MODID + ".sky.day", "Day");
        add("gui." + AeroFlux.MODID + ".sky.night", "Night");
        add("gui." + AeroFlux.MODID + ".sky.weather", "Clear / Rain");
        add("screen." + AeroFlux.MODID + ".side_config.auto_import", "Auto Import");
        add("screen." + AeroFlux.MODID + ".side_config.auto_export", "Auto Export");
        add("gui." + AeroFlux.MODID + ".auto_insert.on", "On");
        add("gui." + AeroFlux.MODID + ".auto_insert.off", "Off");
        add("gui." + AeroFlux.MODID + ".converter.output_on", "Output on");
        add("gui." + AeroFlux.MODID + ".converter.output_off", "Output off");
        add("tooltip." + AeroFlux.MODID + ".look.converter", "Making %s %s per tick");
        add("tooltip." + AeroFlux.MODID + ".look.stress", "Supplying %s SU, %s SU in use");
        // Jade lists every plugin entry in its settings and needs a name for ours.
        add("config.jade.plugin_" + AeroFlux.MODID + ".look_info", "AeroFlux details");
        add("jei." + AeroFlux.MODID + ".chance", "%s%% chance");
        add("jei." + AeroFlux.MODID + ".imported", "From %s");
        add("jei." + AeroFlux.MODID + ".sieve.amount", "Amount: %s");
        add("jei." + AeroFlux.MODID + ".sieve.water", "%s mB water");
        add("jei." + AeroFlux.MODID + ".time", "%s ticks");
        add("jei." + AeroFlux.MODID + ".imported", "From %s");
        add("jei." + AeroFlux.MODID + ".sieve.amount", "Amount: %s");
        add("jei." + AeroFlux.MODID + ".sieve.water", "Needs %s mB of water");
        add("jei." + AeroFlux.MODID + ".time_and_flux", "%s ticks, +%s mB Flux");
        add("jei." + AeroFlux.MODID + ".info.enchanter", "Puts the enchantments of an enchanted book onto a tool or armor piece. Costs lapis and Moon Flux per level. Curses and clashing enchantments are refused.");
        add("jei." + AeroFlux.MODID + ".info.disenchanter", "Moves the enchantments of an item onto a plain book, keeping the item and its curses. Costs Flux per level.");
        add("tooltip." + AeroFlux.MODID + ".look.upgrades", "Upgrades: %s speed, %s capacity");
        add("tooltip." + AeroFlux.MODID + ".look.dimension_off", "Collecting is off in this dimension");

        for (MachineType machine : MachineType.values()) {
            for (Tier tier : Tier.values()) {
                // Every tier is named "<Tier> <Machine>", copper included.
                String name = displayName(tier) + " " + machine.displayName();
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
        addFluxScreenTranslations();
        addSoundAndCreativeTranslations();

        String status = "machine." + AeroFlux.MODID + ".status.";
        add(status + "idle", "Idle");
        add(status + "running", "Running");
        add(status + "no_flux", "Out of Flux");
        add(status + "output_full", "Output full");
        add(status + "disabled", "Disabled");

        addRedstoneAndUpgradeTranslations();

        String mode = "machine_mode." + AeroFlux.MODID + ".";
        add(mode + "import", "Import");
        add(mode + "export", "Export");
        add(mode + "open", "Both");
        add(mode + "off", "Off");
        add(mode + "blocked", "Blocked");

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
        add(side + "all_off", "All Off");
        add(side + "copy_all", "Copy");
        add(side + "reset", "Reset");
        add(side + "face_mode", "%s: %s");
        add(side + "neighbor", "Next to: %s");
        add(side + "nothing", "Nothing");
    }

    /** The machine screen's redstone button and upgrade column. */
    private void addRedstoneAndUpgradeTranslations() {
        String redstone = "redstone_mode." + AeroFlux.MODID + ".";
        for (RedstoneMode mode : RedstoneMode.values()) {
            // The names are the plan's: Always on, On with signal, Off with signal, Pulse.
            add(redstone + mode.id(), switch (mode) {
                case ALWAYS -> "Always on";
                case HIGH -> "On with signal";
                case LOW -> "Off with signal";
                case PULSE -> "Pulse";
            });
        }
        add("gui.aeroflux.redstone.name", "Redstone control");
        add("gui.aeroflux.redstone.mode", "Redstone: %s");

        add("gui.aeroflux.upgrades.name", "Upgrades");
        add("gui.aeroflux.upgrades.title", "Upgrades (%s of %s)");
        add("gui.aeroflux.upgrades.speed", "Speed: %s, working at %s%%, %s mB/t");
        add("gui.aeroflux.upgrades.capacity", "Capacity: %s, tank holds %s mB");
        add("gui.aeroflux.upgrades.efficiency", "Efficiency: %s, burning %s%% of the Flux");
        add("gui.aeroflux.upgrades.fortune", "Fortune: %s, each a %s%% chance of one more");
        add("gui.aeroflux.upgrades.fortune.sifter", "Fortune: %s, every drop chance up %s points");
        add("gui.aeroflux.upgrades.silk_touch.on", "Silk Touch: harvesting whole blocks");
        add("gui.aeroflux.upgrades.silk_touch.off", "Silk Touch: none");
        add("gui.aeroflux.upgrades.purity", "Purity: %s, each a %s%% chance of one more");
    }

    /** Names for the conduit bundle, the pipes, and the wrench's messages. */
    private void addConduitTranslations() {
        addBlock(ModBlocks.CONDUIT_BUNDLE, "Conduit Bundle");
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
        for (MachineUpgrades.Special kind : MachineUpgrades.Special.values()) {
            String name = switch (kind) {
                case EFFICIENCY -> "Efficiency";
                case FORTUNE -> "Fortune";
                case SILK_TOUCH -> "Silk Touch";
                case PURITY -> "Purity";
            };
            addItem(ModItems.machineUpgrade(kind), name + " Upgrade");
            add("upgrade." + AeroFlux.MODID + "." + kind.id(), name);
        }
        for (PipeUpgrades.Kind kind : PipeUpgrades.Kind.values()) {
            add("upgrade." + AeroFlux.MODID + "." + kind.name().toLowerCase(Locale.ROOT), capitalize(kind.name()));
        }
        add("message." + AeroFlux.MODID + ".upgrade_installed", "%s upgrade added to %s pipe (%s of %s slots)");
        add("message." + AeroFlux.MODID + ".upgrade_full", "%s pipe has no free upgrade slots");

        addItem(ModItems.FILTER_UPGRADE, "Filter Upgrade");
        addItem(ModItems.PRIORITY_UPGRADE, "Priority Upgrade");
        for (EndpointSettings.Upgrade upgrade : EndpointSettings.Upgrade.values()) {
            add("upgrade." + AeroFlux.MODID + "." + upgrade.name().toLowerCase(Locale.ROOT), capitalize(upgrade.name()));
        }
        add("message." + AeroFlux.MODID + ".endpoint_upgrade_installed", "%s upgrade added to %s pipe, %s side");
        add("message." + AeroFlux.MODID + ".endpoint_upgrade_present", "%2$s pipe already has a %1$s upgrade on the %3$s side");
        add("message." + AeroFlux.MODID + ".endpoint_upgrade_unsupported", "%2$s pipes cannot take a %1$s upgrade");

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
        addConfiguratorTranslations();
    }

    /** The Flux Configurator: its modes, messages, tooltip and preset screen. */
    private void addConfiguratorTranslations() {
        addItem(ModItems.FLUX_CONFIGURATOR, "Flux Configurator");
        add("configurator_mode." + AeroFlux.MODID + ".paste", "Paste");
        add("configurator_mode." + AeroFlux.MODID + ".area", "Area paste");
        for (Setting setting : Setting.values()) {
            add("setting." + AeroFlux.MODID + "." + setting.id(), settingName(setting));
        }

        String message = "message." + AeroFlux.MODID + ".configurator_";
        add(message + "mode", "Flux Configurator: %s");
        add(message + "copied", "Copied the %s into %s");
        add(message + "empty", "The selected preset is empty");
        add(message + "not_machine", "That block has no settings to paste");
        add(message + "pasted", "Pasted onto the %s");
        add(message + "pasted_skipped", "Pasted onto the %s; it has no %s");
        add(message + "corner_first", "First corner marked");
        add(message + "area_marked", "%s machines in the area");
        add(message + "area_too_large", "An area can be at most %s blocks along each side");
        add(message + "no_area", "No area marked");
        add(message + "area_empty", "There are no machines in the area");
        add(message + "area_limit", "The area holds %s machines; one paste may change at most %s");
        add(message + "area_pasted", "Pasted onto %s machines (%s changed)");
        add(message + "area_pasted_skipped", "Pasted onto %s machines (%s changed); skipped what some lack: %s");
        add(message + "undone", "Undid the last paste on %s machines");
        add(message + "nothing_to_undo", "Nothing to undo");
        add(message + "selected", "Selected %s");
        add(message + "renamed", "Renamed to %s");
        add(message + "rename_failed", "Only a filled preset can be named, and the name cannot be blank");
        add(message + "cleared", "Cleared preset %s");
        add(message + "slot_empty", "That preset is already empty");
        add(message + "bad_slot", "There is no such preset");

        add("tooltip." + AeroFlux.MODID + ".configurator_mode", "Mode: %s");
        add("tooltip." + AeroFlux.MODID + ".configurator_preset", "%s: %s");

        String gui = "gui." + AeroFlux.MODID + ".configurator.";
        add(gui + "slot", "Preset %s");
        add(gui + "empty", "Empty");
        add(gui + "name", "Preset name");
        add(gui + "rename", "Rename");
        add(gui + "clear", "Clear");
        add(gui + "undo", "Undo last paste");
        add(gui + "auto", "Import %s, Export %s");
        add(gui + "on", "on");
        add(gui + "off", "off");
    }

    private static String settingName(Setting setting) {
        return switch (setting) {
            case ITEM_SIDES -> "item faces";
            case FLUID_SIDES -> "fluid faces";
            case ENERGY_SIDES -> "energy faces";
            case GAS_SIDES -> "gas faces";
            case REDSTONE_SIDES -> "redstone faces";
            case REDSTONE_MODE -> "redstone mode";
            case AUTO_IMPORT -> "Auto Import";
            case AUTO_EXPORT -> "Auto Export";
            case AUTO_INSERT -> "seed refill";
            case MACHINE_MODE -> "machine switch";
        };
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

        add(screen + "filter_whitelist", "Whitelist");
        add(screen + "filter_blacklist", "Blacklist");
        add(screen + "filter_slot", "Filter slot");
        add(screen + "channel", "Channel %s: %s");
        add("redstone_channel." + AeroFlux.MODID + ".white", "White");
        add("redstone_channel." + AeroFlux.MODID + ".orange", "Orange");
        add("redstone_channel." + AeroFlux.MODID + ".magenta", "Magenta");
        add("redstone_channel." + AeroFlux.MODID + ".light_blue", "Light Blue");
        add("redstone_channel." + AeroFlux.MODID + ".yellow", "Yellow");
        add("redstone_channel." + AeroFlux.MODID + ".lime", "Lime");
        add("redstone_channel." + AeroFlux.MODID + ".pink", "Pink");
        add("redstone_channel." + AeroFlux.MODID + ".gray", "Gray");
        add("redstone_channel." + AeroFlux.MODID + ".light_gray", "Light Gray");
        add("redstone_channel." + AeroFlux.MODID + ".cyan", "Cyan");
        add("redstone_channel." + AeroFlux.MODID + ".purple", "Purple");
        add("redstone_channel." + AeroFlux.MODID + ".blue", "Blue");
        add("redstone_channel." + AeroFlux.MODID + ".brown", "Brown");
        add("redstone_channel." + AeroFlux.MODID + ".green", "Green");
        add("redstone_channel." + AeroFlux.MODID + ".red", "Red");
        add("redstone_channel." + AeroFlux.MODID + ".black", "Black");
        add(screen + "priority", "Priority: %s");
        add(screen + "priority_down", "Lower priority");
        add(screen + "priority_up", "Raise priority");

        String mode = "connection_mode." + AeroFlux.MODID + ".";

        String distribution = "distribution_mode." + AeroFlux.MODID + ".";
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
        add("message." + AeroFlux.MODID + ".tool_run_start", "Run started on a %s pipe");
        add("message." + AeroFlux.MODID + ".tool_run_invalid", "A run must be a straight line of at most %s blocks");
        add("message." + AeroFlux.MODID + ".tool_run_removed", "Removed %s of the %s pipe along the run");
        add("message." + AeroFlux.MODID + ".pipe_tier_upgraded", "The %s pipe is now %s");
        add("message." + AeroFlux.MODID + ".pipe_tier_not_higher", "The %s pipe is already %s or better");
        add("message." + AeroFlux.MODID + ".tool_copied", "Copied the settings of %s pipes");
        add("message." + AeroFlux.MODID + ".tool_pasted", "Pasted settings onto %s pipes");
        add("message." + AeroFlux.MODID + ".tool_pasted_added", "Pasted settings onto %s pipes and added %s from your inventory");
        add("message." + AeroFlux.MODID + ".tool_pasted_missing",
                "Pasted settings onto %s pipes and added %s; %s pipes were not in your inventory");
        add("message." + AeroFlux.MODID + ".tool_paste_run_start", "Run started");
        add("message." + AeroFlux.MODID + ".tool_paste_run",
                "Pasted onto %s bundles along the run (%s new); %s pipes were not in your inventory");
        add("message." + AeroFlux.MODID + ".tool_nothing_copied", "Nothing copied yet");
        add("message." + AeroFlux.MODID + ".tool_no_network", "The %s pipe is not part of a network");
        add("message." + AeroFlux.MODID + ".tool_inspect",
                "%s network: %s bundles, %s sources, %s destinations, speed %s");
        add("message." + AeroFlux.MODID + ".tool_speed_items", "%s items every %s ticks");
        add("message." + AeroFlux.MODID + ".tool_speed_milli", "%s mB per tick");
        add("message." + AeroFlux.MODID + ".tool_speed_energy", "%s FE per tick");
        add("message." + AeroFlux.MODID + ".tool_speed_none", "not limited");
    }

    /** The chunk loader blocks, their screen, the placing message and the admin command. */
    private void addChunkLoaderTranslations() {
        for (Tier tier : Tier.values()) {
            // Like the machines, the first tier is just "Flux Chunk Loader".
            addBlock(ModBlocks.chunkLoader(tier), tier == Tier.COPPER
                    ? "Flux Chunk Loader" : displayName(tier) + " Flux Chunk Loader");
        }

        String status = "gui.aeroflux.chunk_loader.status.";
        add(status + "active", "Loading");
        add(status + "grace", "Running dry");
        add(status + "no_flux", "Out of Flux");
        add(status + "disabled", "Disabled");
        add(status + "dimension", "Not allowed here");
        add(status + "cap_reached", "Limit reached");
        add("gui.aeroflux.chunk_loader.runtime", "Runtime left at the current draw");
        add("gui.aeroflux.chunk_loader.draw", "%s mB/t for %s chunks");
        add("gui.aeroflux.chunk_loader.chunk", "Chunk %s, %s");
        add("gui.aeroflux.chunk_loader.loaded", "Loaded");
        add("gui.aeroflux.chunk_loader.not_loaded", "Not loaded");
        add("message.aeroflux.chunk_loader_dimension", "Chunk loaders do not work in this dimension");

        String command = "commands.aeroflux.chunkloaders.";
        add(command + "entry", "%s %s, %s, %s: %s, %sx%s chunks, owner %s, %s");
        add(command + "running", "running");
        add(command + "idle", "idle");
        add(command + "no_owner", "nobody");
        add(command + "total", "%s chunk loader(s)");
        add(command + "none", "No chunk loader is registered at %s, %s, %s");
        add(command + "removed", "Removed the chunk loader at %s, %s, %s in %s");

        add("message.aeroflux.protected", "A claim here does not let you change that");
        add("message.aeroflux.protected_skipped", "A claim kept this from changing %s block(s)");

        String profile = "commands.aeroflux.profile.";
        add(profile + "started", "Sampling AeroFlux block entities for %s seconds...");
        add(profile + "busy", "A profile is already running");
        add(profile + "none", "No profile is running");
        add(profile + "cancelled", "Profile cancelled");
        add(profile + "empty", "No AeroFlux block ticked in %s ticks (%s s)");
        add(profile + "header", "AeroFlux over %s ticks (%s s): %s ms per tick in total, %s blocks loaded");
        add(profile + "budget", "Budget for this many blocks: %s ms per tick, %s%% used (%s)");
        add(profile + "within", "within budget");
        add(profile + "over", "over budget");
        add(profile + "group", "%s: %s ms per tick (%s%%), %s blocks");
        add(profile + "type", "  %s: %s ms per tick (%s%%), %s loaded, slowest tick %s us");
        add(profile + "heaviest", "Heaviest blocks:");
        add(profile + "block", "  %s at %s, %s, %s in %s: %s ms per tick");
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
