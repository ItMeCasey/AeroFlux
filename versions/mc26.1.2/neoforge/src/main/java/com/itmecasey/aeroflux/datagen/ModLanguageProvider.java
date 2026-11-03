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
import com.itmecasey.aeroflux.core.configurator.Setting;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.Locale;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

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
        add(subtitle + "furnace", "Flux Furnace running");
        add(subtitle + "grinder", "Flux Grinder running");
        add(subtitle + "smelter", "Alloy Smelter running");
        add(subtitle + "sawmill", "Flux Sawmill running");
        add(subtitle + "infuser", "Flux Infuser running");
        add(subtitle + "enchanter", "Flux Enchanter running");
        add(subtitle + "disenchanter", "Flux Disenchanter running");
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
        add(collector + "offer.making.tooltip", "The sky gives %s now.");
        add(collector + "offer.none", "%s: nothing");
        add(collector + "offer.none.tooltip", "Between day and night the sky gives no Flux.");
        add(collector + "offer.locked", "%s: waiting");
        add(collector + "offer.locked.tooltip", "The sky gives %s, but the buffer still holds the other Flux. Empty it first.");
        add(collector + "sky.open", "Open sky");
        add(collector + "sky.open.tooltip", "Nothing blocks the sky above.");
        add(collector + "sky.blocked", "Sky blocked");
        add(collector + "sky.blocked_by", "Blocked by %s");
        add(collector + "sky.blocked_at", "At %s, %s, %s");
        add(collector + "sky.blocked.tooltip", "A collector needs open sky above it. Glass lets the light through.");
        add(collector + "sky.not_needed", "Sky not needed here");
        add(collector + "sky.not_needed.tooltip", "In this dimension collectors work under a roof.");
        add(collector + "weather.clear", "Clear: %s%%");
        add(collector + "weather.rain", "Rain: %s%%");
        add(collector + "weather.thunder", "Thunder: %s%%");
        add(collector + "weather.tooltip", "Rain halves Aero Flux and thunder cuts it to a quarter. Moon Flux does not mind.");
        add(collector + "weather.ignored", "Weather ignored here");
        add(collector + "weather.ignored.tooltip", "In this dimension the weather changes nothing.");
        add(collector + "moon", "%s: %s%%");
        add(collector + "moon.tooltip", "Moon Flux is strongest at a full moon and weakest at a new moon.");
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
        add(collector + "full.tooltip", "At the current rates, counting what is passed on.");

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
        add("tooltip." + AeroFlux.MODID + ".upgrade_card", "Upgrades a %s machine, collector, tank or converter to %s: sneak and use it on the block");
        add("tooltip." + AeroFlux.MODID + ".sifter", "Runs the sieve recipes of Ex Deorum and Ex Nihilo Sequentia: put a mesh from either in its mesh slot");
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
        add("advancements." + AeroFlux.MODID + ".root.title", "AeroFlux");
        add("advancements." + AeroFlux.MODID + ".root.description", "Make Flux Dust, the start of everything Flux");
        add("advancements." + AeroFlux.MODID + ".collector.title", "Catching Light");
        add("advancements." + AeroFlux.MODID + ".collector.description", "Build a Flux Collector");
        add("advancements." + AeroFlux.MODID + ".aero_flux.title", "Bottled Daylight");
        add("advancements." + AeroFlux.MODID + ".aero_flux.description", "Fill a bucket with Aero Flux");
        add("advancements." + AeroFlux.MODID + ".moon_flux.title", "Moonshine");
        add("advancements." + AeroFlux.MODID + ".moon_flux.description", "Fill a bucket with Moon Flux");
        add("advancements." + AeroFlux.MODID + ".tank.title", "Hold Everything");
        add("advancements." + AeroFlux.MODID + ".tank.description", "Build a Flux Tank");
        add("advancements." + AeroFlux.MODID + ".pipe.title", "Plumbing");
        add("advancements." + AeroFlux.MODID + ".pipe.description", "Craft your first pipe");
        add("advancements." + AeroFlux.MODID + ".smart_pipes.title", "Sorted");
        add("advancements." + AeroFlux.MODID + ".smart_pipes.description", "Make a Filter or Priority Upgrade");
        add("advancements." + AeroFlux.MODID + ".furnace.title", "Flux-Fired");
        add("advancements." + AeroFlux.MODID + ".furnace.description", "Build a Flux Furnace");
        add("advancements." + AeroFlux.MODID + ".alloy_smelter.title", "Better Together");
        add("advancements." + AeroFlux.MODID + ".alloy_smelter.description", "Build an Alloy Smelter");
        add("advancements." + AeroFlux.MODID + ".plant_grower.title", "Green Thumb");
        add("advancements." + AeroFlux.MODID + ".plant_grower.description", "Build a Plant Grower");
        add("advancements." + AeroFlux.MODID + ".enchanter.title", "Spellbound");
        add("advancements." + AeroFlux.MODID + ".enchanter.description", "Build a Flux Enchanter");
        add("advancements." + AeroFlux.MODID + ".speed_upgrade.title", "Overclocked");
        add("advancements." + AeroFlux.MODID + ".speed_upgrade.description", "Make a Speed Upgrade");
        add("advancements." + AeroFlux.MODID + ".upgrade_card.title", "Moving Up");
        add("advancements." + AeroFlux.MODID + ".upgrade_card.description", "Make an Upgrade Card");
        add("advancements." + AeroFlux.MODID + ".fluxium.title", "Top of the Line");
        add("advancements." + AeroFlux.MODID + ".fluxium.description", "Own a Fluxium-tier block");
        add("advancements." + AeroFlux.MODID + ".chunk_loader.title", "Never Sleeps");
        add("advancements." + AeroFlux.MODID + ".chunk_loader.description", "Build a Flux Chunk Loader");
        addItem(ModItems.GUIDEBOOK, "AeroFlux Guide");
        add("guide." + AeroFlux.MODID + ".start.title", "Welcome to AeroFlux");
        add("guide." + AeroFlux.MODID + ".start.body", "AeroFlux gathers Flux from the sky: Aero Flux from the sun by day and Moon Flux from the moon by night. Both are fluids that power every AeroFlux machine.\nChopping trees shakes loose Sawdust now and then; two make a Flux Dust. Then build a Flux Collector, a Flux Tank and a Flux Furnace.");
        add("guide." + AeroFlux.MODID + ".flux.title", "Aero and Moon Flux");
        add("guide." + AeroFlux.MODID + ".flux.body", "A collector makes Aero Flux in daylight and Moon Flux at night, faster in clear weather and, for Moon Flux, on brighter moon phases. A collector holds one kind at a time.\nMost machines take either kind; a few recipes need Moon Flux.");
        add("guide." + AeroFlux.MODID + ".collectors.title", "Collectors and Tanks");
        add("guide." + AeroFlux.MODID + ".collectors.body", "Collectors need open sky above them. Each tier collects faster and holds more.\nTanks store one fluid at a time and show their fill level. Pipe Flux from collectors into tanks, and from tanks into machines.");
        add("guide." + AeroFlux.MODID + ".pipes.title", "Pipes");
        add("guide." + AeroFlux.MODID + ".pipes.body", "Pipes carry fluids, items, energy, redstone, and with the right mods, gas and ME channels. Several kinds share one block: use a pipe on a pipe block to add it.\nEach kind connects to the same kind next to it, and to machines that take what it carries. Right-click a connector for its settings. Use a higher-tier pipe on a pipe to upgrade it in place.");
        add("guide." + AeroFlux.MODID + ".machines.title", "Machines");
        add("guide." + AeroFlux.MODID + ".machines.body", "Every machine runs on Flux: Furnace, Grinder, Sawmill, Alloy Smelter, Infuser, Enchanter, Disenchanter and Plant Grower. The side panel sets what each face takes in or puts out.\nThe front lights up while a machine works.");
        add("guide." + AeroFlux.MODID + ".upgrades.title", "Upgrades and Tiers");
        add("guide." + AeroFlux.MODID + ".upgrades.body", "Speed Upgrades make a machine or pipe faster; Capacity Upgrades let it hold or move more. Filter and Priority Upgrades fine-tune pipe connections.\nEvery block comes in six tiers: copper, iron, gold, diamond, netherite and fluxium. An Upgrade Card raises a placed machine, collector or tank one tier and keeps everything in it: sneak and use it on the block.");
        add("guide." + AeroFlux.MODID + ".chunk_loader.title", "Flux Chunk Loader");
        add("guide." + AeroFlux.MODID + ".chunk_loader.body", "Keeps the chunks around it loaded while it has Flux, from 1x1 at copper to 11x11 at fluxium. Higher tiers cost less Flux per chunk.\nWhen it runs dry it waits a few seconds before letting the chunks go.");
        add("guide." + AeroFlux.MODID + ".dimensions.title", "Other Dimensions");
        add("guide." + AeroFlux.MODID + ".dimensions.body", "By default only the Overworld has Flux to collect. Data packs can change that per dimension, including whether chunk loaders are allowed there.");
        for (ConverterType type : ModBlocks.CONVERTER_TYPES) {
            for (Tier tier : Tier.values()) {
                addBlock(ModBlocks.converter(type, tier), tier == Tier.COPPER ? type.displayName() : displayName(tier) + " " + type.displayName());
            }
        }
        add("gui." + AeroFlux.MODID + ".converter.buffer.fe", "Energy buffer");
        add("gui." + AeroFlux.MODID + ".converter.buffer.ae", "AE buffer");
        add("gui." + AeroFlux.MODID + ".auto_insert", "Auto insert seeds");
        add("gui." + AeroFlux.MODID + ".chunk_loader.show_area", "Show Area");
        for (LampKind kind : LampKind.values()) {
            for (Tier tier : Tier.values()) {
                addBlock(ModBlocks.lamp(kind, tier), tier == Tier.COPPER ? kind.displayName() : displayName(tier) + " " + kind.displayName());
            }
        }
        add("gui." + AeroFlux.MODID + ".magnet_mode", "Filter mode");
        add("gui." + AeroFlux.MODID + ".magnet_mode.on", "Whitelist: only the item in the filter slot is pulled in. Click for blacklist.");
        add("gui." + AeroFlux.MODID + ".magnet_mode.off", "Blacklist: everything but the item in the filter slot is pulled in. Click for whitelist.");
        add("gui." + AeroFlux.MODID + ".sky.day", "Day");
        add("gui." + AeroFlux.MODID + ".sky.day.tooltip", "Moves the clock to morning. Costs Aero Flux.");
        add("gui." + AeroFlux.MODID + ".sky.night", "Night");
        add("gui." + AeroFlux.MODID + ".sky.night.tooltip", "Moves the clock to nightfall. Costs Moon Flux from the second tank.");
        add("gui." + AeroFlux.MODID + ".sky.weather", "Clear / Rain");
        add("gui." + AeroFlux.MODID + ".sky.weather.tooltip", "Stops the rain, or starts it. Costs Aero Flux.");
        add("guide." + AeroFlux.MODID + ".more_machines.title", "More Machines");
        add("guide." + AeroFlux.MODID + ".more_machines.body", "Crafter: lay a recipe in its grid; it keeps one item in each slot and crafts with the rest. Purifier: ore and water into three times the raw metal. Sifter: a block and a mesh from Ex Deorum or Ex Nihilo Sequentia; the mesh decides the drops. Blender: Aero and Moon Flux into Twilight Flux.\nSky Controller: day, night and rain at a button. Magnet: pulls dropped items in. Breeder, Repairer and Moon Spawner do what their names say. Sun and Moon Lamps keep monsters away and help plants grow.");
        add("screen." + AeroFlux.MODID + ".side_config.auto_import", "Auto Import");
        add("screen." + AeroFlux.MODID + ".side_config.auto_import.tooltip",
                "Pulls items and Flux from the blocks next to every Import or Both face, no pipe needed.");
        add("screen." + AeroFlux.MODID + ".side_config.auto_export", "Auto Export");
        add("screen." + AeroFlux.MODID + ".side_config.auto_export.tooltip",
                "Pushes finished products into the blocks next to every Export or Both face, no pipe needed.");
        add("gui." + AeroFlux.MODID + ".chunk_loader.show_area.tooltip", "Traces the borders of the loaded chunks in the world for ten seconds.");
        add("gui." + AeroFlux.MODID + ".auto_insert.on", "On: seeds from the outputs are planted again. Click to stop.");
        add("gui." + AeroFlux.MODID + ".auto_insert.off", "Off: seeds stay in the outputs. Click to replant them.");
        add("gui." + AeroFlux.MODID + ".converter.output_on", "Power goes out here. Click to stop it.");
        add("gui." + AeroFlux.MODID + ".converter.output_off", "No power goes out here. Click to send it.");
        add("tooltip." + AeroFlux.MODID + ".look.converter", "Making %s %s per tick");
        // Jade lists every plugin entry in its settings and needs a name for ours.
        add("config.jade.plugin_" + AeroFlux.MODID + ".look_info", "AeroFlux details");
        add("jei." + AeroFlux.MODID + ".chance", "%s%% chance");
        add("jei." + AeroFlux.MODID + ".time", "%s ticks");
        add("jei." + AeroFlux.MODID + ".time_and_flux", "%s ticks, +%s mB Flux");
        add("jei." + AeroFlux.MODID + ".info.enchanter", "Puts the enchantments of an enchanted book onto a tool or armor piece. Costs lapis and Moon Flux per level. Curses and clashing enchantments are refused.");
        add("jei." + AeroFlux.MODID + ".info.disenchanter", "Moves the enchantments of an item onto a plain book, keeping the item and its curses. Costs Flux per level.");
        add("tooltip." + AeroFlux.MODID + ".look.upgrades", "Upgrades: %s speed, %s capacity");
        add("tooltip." + AeroFlux.MODID + ".look.dimension_off", "Collecting is off in this dimension");

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
        addFluxScreenTranslations();
        addSoundAndCreativeTranslations();

        String status = "machine." + AeroFlux.MODID + ".status.";
        add(status + "idle", "Idle");
        add(status + "idle.tooltip", "Nothing to work on.");
        add(status + "running", "Running");
        add(status + "running.tooltip", "Working. Burning Flux.");
        add(status + "no_flux", "Out of Flux");
        add(status + "no_flux.tooltip", "Not enough of the right Flux to keep working. Progress is kept.");
        add(status + "output_full", "Output full");
        add(status + "output_full.tooltip", "The output slots have no room. Take something out to continue.");
        add(status + "disabled", "Disabled");
        add(status + "disabled.tooltip.high", "Waiting for a redstone signal. Progress is kept.");
        add(status + "disabled.tooltip.low", "Stopped by a redstone signal. Progress is kept.");
        add(status + "disabled.tooltip.pulse", "Waiting for a redstone pulse. Each pulse finishes one job.");

        addRedstoneAndUpgradeTranslations();

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
            add(redstone + mode.id() + ".tooltip", switch (mode) {
                case ALWAYS -> "Works whatever the redstone signal.";
                case HIGH -> "Works only while it gets a redstone signal.";
                case LOW -> "Works only while it gets no redstone signal.";
                case PULSE -> "Finishes one job for each new redstone signal.";
            });
        }
        add("gui.aeroflux.redstone.name", "Redstone control");
        add("gui.aeroflux.redstone.mode", "Redstone: %s");
        add("gui.aeroflux.redstone.hint", "Click for the next mode, right-click for the one before.");

        add("gui.aeroflux.upgrades.name", "Upgrades");
        add("gui.aeroflux.upgrades.title", "Upgrades (%s of %s)");
        add("gui.aeroflux.upgrades.speed", "Speed: %s, working at %s%%, %s mB/t");
        add("gui.aeroflux.upgrades.capacity", "Capacity: %s, tank holds %s mB");
        add("gui.aeroflux.upgrades.hint", "Takes %s Upgrades, one per slot.");
        add("gui.aeroflux.upgrades.efficiency", "Efficiency: %s, burning %s%% of the Flux");
        add("gui.aeroflux.upgrades.fortune", "Fortune: %s, each a %s%% chance of one more");
        add("gui.aeroflux.upgrades.fortune.sifter", "Fortune: %s, every drop chance up %s points");
        add("gui.aeroflux.upgrades.silk_touch.on", "Silk Touch: harvesting whole blocks");
        add("gui.aeroflux.upgrades.silk_touch.off", "Silk Touch: none");
        add("gui.aeroflux.upgrades.purity", "Purity: %s, each a %s%% chance of one more");
        String upgrade = "tooltip." + AeroFlux.MODID + ".machine_upgrade.";
        add(upgrade + "efficiency", "Machines burn less Flux while working");
        add(upgrade + "efficiency.fits", "Fits every machine");
        add(upgrade + "fortune", "Grinder: a chance of one more of the main product. Sifter: better drop chances");
        add(upgrade + "fortune.fits", "Fits the Grinder and the Sifter");
        add(upgrade + "silk_touch", "Plant Grower: harvests whole blocks, such as leaves, melons and mushroom blocks");
        add(upgrade + "silk_touch.fits", "Fits the Plant Grower");
        add(upgrade + "purity", "Purifier: a chance of one more of the main product");
        add(upgrade + "purity.fits", "Fits the Purifier");
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
        add(message + "empty", "The selected preset is empty. Sneak-use a machine to copy it.");
        add(message + "not_machine", "That block has no settings to paste");
        add(message + "pasted", "Pasted onto the %s");
        add(message + "pasted_skipped", "Pasted onto the %s; it has no %s");
        add(message + "corner_first", "First corner marked. Use the Configurator on the opposite corner.");
        add(message + "area_marked", "%s machines in the area. Use again to paste, or sneak-use the air to cancel.");
        add(message + "area_too_large", "An area can be at most %s blocks along each side");
        add(message + "no_area", "No area marked");
        add(message + "area_empty", "There are no machines in the area");
        add(message + "area_limit", "The area holds %s machines; one paste may change at most %s");
        add(message + "area_pasted", "Pasted onto %s machines (%s changed)");
        add(message + "area_pasted_skipped", "Pasted onto %s machines (%s changed); skipped what some lack: %s");
        add(message + "undone", "Undid the last paste on %s machines");
        add(message + "nothing_to_undo", "Nothing to undo. A paste can be undone once, for a minute.");
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
        add(gui + "hint", "Sneak-use a machine to copy it here.");
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

        add(screen + "endpoint_empty", "Click with a %s to install it on this side only.");
        add(screen + "endpoint_installed", "Installed on this side. Click with an empty hand to take it out; its settings are lost.");
        add(screen + "filter.tooltip", "Decides what this side lets through, in both directions.");
        add(screen + "filter_whitelist", "Whitelist");
        add(screen + "filter_whitelist.tooltip", "Only what is listed passes this side. An empty whitelist stops everything.");
        add(screen + "filter_blacklist", "Blacklist");
        add(screen + "filter_blacklist.tooltip", "Everything except what is listed passes this side.");
        add(screen + "filter_slot", "Filter slot");
        add(screen + "filter_slot.item", "Click with an item to add it to the filter.");
        add(screen + "filter_slot.fluid", "Click with a bucket or tank to add its fluid to the filter.");
        add(screen + "filter_slot.clear", "Click with an empty hand to clear this slot.");
        add(screen + "priority", "Priority: %s");
        add(screen + "priority.tooltip", "Higher numbers fill first; sides without this upgrade count as 0. Ranges from -5 to +5.");
        add(screen + "priority_down", "Lower priority");
        add(screen + "priority_down.tooltip", "Lowers this side's priority by one.");
        add(screen + "priority_up", "Raise priority");
        add(screen + "priority_up.tooltip", "Raises this side's priority by one.");

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
        add("message." + AeroFlux.MODID + ".pipe_tier_upgraded", "The %s pipe is now %s");
        add("message." + AeroFlux.MODID + ".pipe_tier_not_higher", "The %s pipe is already %s or better");
        add("message." + AeroFlux.MODID + ".tool_copied", "Copied the settings of %s pipes");
        add("message." + AeroFlux.MODID + ".tool_pasted", "Pasted settings onto %s pipes");
        add("message." + AeroFlux.MODID + ".tool_pasted_added", "Pasted settings onto %s pipes and added %s from your inventory");
        add("message." + AeroFlux.MODID + ".tool_pasted_missing",
                "Pasted settings onto %s pipes and added %s; %s pipes were not in your inventory");
        add("message." + AeroFlux.MODID + ".tool_paste_run_start", "Run started. Sneak-use the other end to paste along it.");
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
        add(status + "active.tooltip", "Keeping its chunks loaded and ticking.");
        add(status + "grace", "Running dry");
        add(status + "grace.tooltip", "Out of Flux. The chunks stay loaded a few more seconds.");
        add(status + "no_flux", "Out of Flux");
        add(status + "no_flux.tooltip", "Add Aero or Moon Flux to load its chunks.");
        add(status + "disabled", "Disabled");
        add(status + "disabled.tooltip", "Chunk loaders are turned off on this server.");
        add(status + "dimension", "Not allowed here");
        add(status + "dimension.tooltip", "This dimension does not allow chunk loaders.");
        add(status + "cap_reached", "Limit reached");
        add(status + "cap_reached.tooltip", "The owner or this dimension already runs as many loaders as the server allows.");
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

        add("jei.aeroflux.imported", "From %s");
        add("jei.aeroflux.sieve.amount", "Amount: %s");
        add("jei.aeroflux.sieve.water", "%s mB water");

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
