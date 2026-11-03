package com.itmecasey.aeroflux.core.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the AeroFlux Guide says and how it is laid out: chapters, each a run of pages, with the
 * English text for every one. The screens (one per build) only draw it, and each build's
 * datagen writes {@link #translations()} into its language file, so the book is written once
 * for all six builds and translated like any other text.
 *
 * <p>Page one of the book is the contents, which lists the chapters; every other page belongs
 * to a chapter. A body is plain text where {@code \n} starts a new paragraph. Pages that have
 * more on the wiki carry the wiki page's name.
 */
public final class GuideBook {
    /** Key prefix of every guide string in the language files. */
    public static final String PREFIX = "guide.aeroflux.";
    /** Where the wiki lives; a page's slug goes after {@code ?p=}. */
    public static final String WIKI = "https://itmecasey.com/wiki/aeroflux/";

    /** One page: its id, the wiki page with more detail (or {@code null}), its title and its text. */
    public record Page(String id, String wiki, String title, String body) {
        /** The link the wiki button opens, or {@code null} when the page has no wiki entry. */
        public String wikiUrl() {
            return wiki == null ? null : WIKI + "?p=" + wiki;
        }
    }

    /** A named run of pages. */
    public record Chapter(String id, String title, List<Page> pages) {
    }

    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter("basics", "Getting Started", List.of(
                    new Page("start", "getting-started", "Welcome to AeroFlux",
                            "AeroFlux gathers Flux from the sky: Aero Flux from the sun by day and Moon Flux from the moon by night. Both are fluids that power every AeroFlux machine.\n"
                                    + "Chopping trees shakes loose Sawdust now and then; two make a Flux Dust. Then build a Flux Collector, a Flux Tank and a Copper Furnace."),
                    new Page("flux", "flux", "Aero and Moon Flux",
                            "A collector makes Aero Flux in daylight and Moon Flux at night, faster in clear weather and, for Moon Flux, on brighter moon phases. A collector holds one kind at a time.\n"
                                    + "Most machines take either kind; the Infuser, the enchanting machines and a few others need Moon Flux. The Blender mixes both into Twilight Flux for the top recipes."),
                    new Page("dust", "dusts", "Dust and Crystals",
                            "Flux Dust is the first material: craft two Sawdust into one, or put Sawdust in an Infuser with Aero Flux for one dust each. Four dust around a quartz or an amethyst make an Aero or Moon Crystal by hand.\n"
                                    + "The Infuser makes crystals faster, from one dust and Moon Flux. The Alloy Smelter turns them into Fluxsteel and Aerogold."))),
            new Chapter("supply", "Flux Supply", List.of(
                    new Page("collectors", "collectors", "Collectors",
                            "Collectors need open sky above them. Each tier collects faster and holds more; clear weather and a bright moon speed them up.\n"
                                    + "Right-click one to see its screen: the buffer, the rate now, and a sky panel that names what blocks the sky."),
                    new Page("tanks", "tanks", "Tanks",
                            "Tanks store one fluid at a time and show their fill level from outside. Pipe Flux from collectors into tanks, and from tanks into machines.\n"
                                    + "A tank keeps its Flux when you break it: the amount rides on the dropped item and fills the tank again when placed. The Creative Flux Tank never runs dry and is only for creative mode."),
                    new Page("dimensions", "datapacks", "Other Dimensions",
                            "By default only the Overworld has Flux to collect. Data packs can change that per dimension, including how fast collectors run and whether chunk loaders are allowed there."))),
            new Chapter("logistics", "Pipes and Tools", List.of(
                    new Page("pipes", "pipe-types", "Pipes",
                            "Pipes carry fluids, items, energy, redstone, and with the right mods, gas and ME channels. Several kinds share one block: use a pipe on a pipe block to add it.\n"
                                    + "Each kind connects to the same kind next to it, and to machines that take what it carries. Right-click a connector for its settings. Use a higher-tier pipe on a pipe to upgrade it in place."),
                    new Page("wrench", "flux-wrench", "Flux Wrench",
                            "The everyday pipe tool. Use it on a pipe to switch that face to the next mode: open, import, export, off, blocked.\n"
                                    + "Sneak and use it to change the pipe's distribution instead: round robin, first to last, last to first or random. It picks the pipe nearest your crosshair."),
                    new Page("configurator", "flux-configurator", "Flux Configurator",
                            "Saves one machine's face settings, redstone mode and Auto Import and Export, and pastes them onto others. Sneak and use a machine to copy it into the selected preset; use another machine to paste.\n"
                                    + "In Area mode, mark two corners and paste onto every machine in the box. Eight named presets keep your favorites, and the last paste can be undone for a minute."),
                    new Page("conduit_tool", "conduit-tool", "Conduit Tool",
                            "Six modes, switched by sneaking in the air: edit, remove, remove run, copy, paste and inspect. Removing gives the pipe and its upgrades back; sneak to take the whole bundle.\n"
                                    + "A run removes one kind of pipe along a straight line of up to 64 blocks. Copy and paste moves face settings between bundles, and inspect describes a network in chat."))),
            new Chapter("machines", "Machines", List.of(
                    new Page("machines", "machines", "Machines",
                            "Every machine runs on Flux from its own tank and comes in six tiers: copper, iron, gold, diamond, netherite and fluxium. Higher tiers work faster and hold more.\n"
                                    + "The side panel sets what each face takes in or puts out. The front lights up while a machine works."),
                    new Page("processing", "machines", "Processing Machines",
                            "Furnace: smelts without fuel. Grinder: ore into double raw material, bones into bone meal. Sawmill: logs into six planks and sometimes Sawdust.\n"
                                    + "Alloy Smelter: two or three ingredients into an alloy. Infuser: an item and a pinch of Flux Dust soaked in Flux into something new."),
                    new Page("magic", "flux-enchanter", "Magic Machines",
                            "Enchanter: puts the enchantments of a book onto a tool for lapis and Moon Flux. Disenchanter: moves them from an item onto a plain book and keeps the item.\n"
                                    + "Repairer: mends a tool with Bottles o' Enchanting and keeps its enchantments. Moon Spawner: rolls the loot of the mob on a spawn egg without any mob."),
                    new Page("farming", "plant-grower", "Farming Machines",
                            "Plant Grower: seeds or saplings and the soil they like, grown without a field. Any mod's crop works. Breeder: feeds the animals around it so they breed.\n"
                                    + "Sifter: a block and a mesh from Ex Deorum or Ex Nihilo Sequentia; the mesh decides the drops."),
                    new Page("utility", "machines", "Utility Machines",
                            "Crafter: lay a recipe in its grid; it keeps one item in each slot and crafts with the rest. Purifier: ore and water into three times the raw metal. Blender: Aero and Moon Flux into Twilight Flux.\n"
                                    + "Sky Controller: day, night and rain at a button. Magnet: pulls dropped items in, with a filter. Sun and Moon Lamps keep monsters away and help plants grow."))),
            new Chapter("upgrades", "Upgrades and Tiers", List.of(
                    new Page("upgrades", "upgrades", "Upgrades",
                            "Speed Upgrades make a machine or pipe faster; Capacity Upgrades let it hold or move more. Filter and Priority Upgrades fine-tune pipe connections.\n"
                                    + "Drop upgrades into the slots on a machine's screen. Machines take as many as the slots allow."),
                    new Page("machine_upgrades", "machine-upgrades", "Machine Upgrades",
                            "Efficiency: any machine burns less Flux while it works. Fortune: the Grinder and Sifter give more. Silk Touch: the Plant Grower gives whole blocks such as leaves, melons and mushroom blocks. Purity: the Purifier sometimes gives an extra raw metal.\n"
                                    + "They are built on Fluxsteel and only go into machines they do something in."),
                    new Page("tiers", "upgrade-cards", "Tiers and Upgrade Cards",
                            "Every block comes in six tiers: copper, iron, gold, diamond, netherite and fluxium. An Upgrade Card raises a placed machine, collector, tank or converter one tier and keeps everything in it: sneak and use it on the block."))),
            new Chapter("extras", "More to Know", List.of(
                    new Page("chunk_loader", "flux-chunk-loader", "Flux Chunk Loader",
                            "Keeps the chunks around it loaded while it has Flux, from 1x1 at copper to 11x11 at fluxium. Higher tiers cost less Flux per chunk.\n"
                                    + "When it runs dry it waits a few seconds before letting the chunks go. Its screen can show the area."),
                    new Page("converters", "flux-energy-converter", "Converters",
                            "Converters turn Flux into FE for other mods' machines, or into AE2 power. Each face can be set to output or off, and the buffer shows on the screen."),
                    new Page("screens", "side-config", "Machine Screens",
                            "A machine screen shows its tank and progress, a status line that says why it is idle, and a badge for the kind of Flux it uses with a bar for what the current run costs.\n"
                                    + "The button on the side opens the face settings; the redstone button picks when the machine runs."),
                    new Page("sounds", "configuration", "Sounds and Settings",
                            "Machines hum while they work and chime when they finish. The client settings can turn the machine sounds off.\n"
                                    + "Rates, storage sizes and recipe rules live in the config files, and the storage sizes sync from the server."),
                    new Page("compat", "compatibility", "Other Mods",
                            "Jade, The One Probe and WTHIT show Flux levels. JEI, REI and EMI list the recipes, including ones AeroFlux machines run from other mods: Create, Mekanism, Thermal, Immersive Engineering and more.\n"
                                    + "Scripts can add and remove recipes with KubeJS or CraftTweaker where they exist."))));

    private GuideBook() {
    }

    /** The chapters in reading order. */
    public static List<Chapter> chapters() {
        return CHAPTERS;
    }

    /** Every page in reading order, across chapters. */
    public static List<Page> pages() {
        List<Page> all = new ArrayList<>();
        for (Chapter chapter : CHAPTERS) {
            all.addAll(chapter.pages());
        }
        return all;
    }

    /** The chapter a page belongs to. */
    public static Chapter chapterOf(Page page) {
        for (Chapter chapter : CHAPTERS) {
            if (chapter.pages().contains(page)) {
                return chapter;
            }
        }
        throw new IllegalArgumentException(page.id());
    }

    /** The English text of the whole book keyed the way the language files want it. */
    public static Map<String, String> translations() {
        Map<String, String> text = new LinkedHashMap<>();
        text.put(PREFIX + "contents.title", "Contents");
        text.put(PREFIX + "contents.hint", "Pick a chapter to jump to it.");
        text.put(PREFIX + "wiki", "Wiki");
        text.put(PREFIX + "wiki.tooltip", "More on this in the AeroFlux wiki, in your browser");
        text.put(PREFIX + "home.tooltip", "Back to the contents");
        for (Chapter chapter : CHAPTERS) {
            text.put(PREFIX + "chapter." + chapter.id(), chapter.title());
            for (Page page : chapter.pages()) {
                text.put(PREFIX + page.id() + ".title", page.title());
                text.put(PREFIX + page.id() + ".body", page.body());
            }
        }
        return text;
    }
}
