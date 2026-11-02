package com.itmecasey.aeroflux.core.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The fixtures under {@code foreign/} are recipe files copied unchanged from the mods' own jars
 * (Ender IO, Occultism, Oritech, Silent Gear, Mystical Agriculture and Ex Deorum for 26.1; Create,
 * Mekanism, Immersive Engineering, Thermal, Botany Pots, Ars Nouveau and others for 1.21.1 and
 * 1.20.1), so these tests read exactly what those mods ship.
 */
class ForeignRecipeReaderTest {
    private static JsonObject fixture(String path) throws IOException, URISyntaxException {
        Path file = Path.of(ForeignRecipeReaderTest.class.getResource("/foreign/" + path).toURI());
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void everyShippedRecipeReadsAsSomethingInAndSomethingOut() throws IOException, URISyntaxException {
        Path root = Path.of(ForeignRecipeReaderTest.class.getResource("/foreign").toURI());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() > 100, "fixtures missing");
        List<String> unreadable = new java.util.ArrayList<>();
        for (Path file : files) {
            JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            // A recipe that needs a fluid is rightly left out; anything else must read.
            boolean fluid = recipe.toString().contains("\"fluid");
            if (!fluid && !ForeignRecipeReader.read(recipe, keysFor(root.relativize(file))).usable()) {
                unreadable.add(root.relativize(file).toString());
            }
        }
        assertTrue(unreadable.isEmpty(), "unreadable: " + unreadable);
    }

    /**
     * The keys a fixture is read with: the defaults, unless AeroFlux ships an import rule naming
     * other keys for that recipe type (see {@link #NAMED_KEYS}).
     */
    private static ForeignRecipeReader.Keys keysFor(Path relative) {
        String path = relative.toString().replace(java.io.File.separatorChar, '/');
        for (var entry : NAMED_KEYS.entrySet()) {
            if (path.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return ForeignRecipeReader.DEFAULT_KEYS;
    }

    /** Recipe types whose import rules name their own keys, by a piece of their fixture path. */
    private static final java.util.Map<String, ForeignRecipeReader.Keys> NAMED_KEYS = java.util.Map.of();

    @Test
    void enderIoMillingKeepsEveryRolledProduct() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("enderio/sag_milling/allium.json"));
        assertEquals(1, recipe.inputs().size());
        assertEquals("\"minecraft:allium\"", recipe.inputs().get(0).ingredient().toString());
        assertEquals(4, recipe.outputs().size());
        assertEquals(new ForeignRecipeReader.Output("minecraft:magenta_dye", false, 1, 0.8), recipe.outputs().get(0));
        assertEquals(new ForeignRecipeReader.Output("enderio:plant_matter_green", false, 1, 0.1), recipe.outputs().get(3));
    }

    @Test
    void enderIoTagProductsStayTags() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("enderio/sag_milling/aluminum.json"));
        assertEquals(List.of(new ForeignRecipeReader.Output("c:dusts/aluminum", true, 1, 1.0)), recipe.outputs());
    }

    @Test
    void sizedInputsKeepTheirCounts() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("enderio/alloy_smelting/dark_steel_ingot_with_coal.json"));
        assertEquals(3, recipe.inputs().size());
        assertEquals(2, recipe.inputs().get(1).count());
        assertEquals("\"minecraft:coal\"", recipe.inputs().get(1).ingredient().toString());
        assertEquals(List.of(new ForeignRecipeReader.Output("enderio:dark_steel_ingot", false, 2, 1.0)), recipe.outputs());
    }

    @Test
    void occultismTagResults() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("occultism/crushing/allthemodium_dirty_dust_from_clump.json"));
        assertEquals(List.of(new ForeignRecipeReader.Output("c:dirty_dusts/allthemodium", true, 2, 1.0)), recipe.outputs());
    }

    @Test
    void theSameIngredientTwiceCountsAsTwo() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("silentgear/alloy_making/metal/azure_electrum_ingot.json"));
        assertEquals(3, recipe.inputs().size());
        assertEquals(2, recipe.inputs().get(1).count());
        assertEquals(List.of(new ForeignRecipeReader.Output("silentgear:azure_electrum_ingot", false, 3, 1.0)), recipe.outputs());
    }

    @Test
    void anIngredientListMeansAnyOfThem() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("exdeorum/hammer/basalt.json"));
        assertEquals(1, recipe.inputs().size());
        assertTrue(recipe.inputs().get(0).ingredient().isJsonArray());
        assertEquals(List.of(new ForeignRecipeReader.Output("minecraft:basalt", false, 1, 1.0)), recipe.outputs());
    }

    @Test
    void mekanismMainAndExtraInputsBothCount() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("1_21_1/mekanism/combining/black_to_light_gray.json"));
        assertEquals(2, recipe.inputs().size());
        assertEquals(1, recipe.inputs().get(0).count());
        assertEquals(2, recipe.inputs().get(1).count());
        assertEquals(List.of(new ForeignRecipeReader.Output("minecraft:light_gray_dye", false, 6, 1.0)), recipe.outputs());
        // The older spelling, with the count as "amount" around an inner ingredient.
        ForeignRecipeReader.Recipe old = ForeignRecipeReader.read(fixture("1_20_1/mekanism/combining/black_to_light_gray.json"));
        assertEquals(2, old.inputs().size());
        assertEquals(2, old.inputs().get(1).count());
    }

    @Test
    void immersiveEngineeringSizedResultsAndSecondaries() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("1_21_1/immersiveengineering/crusher/rice_panicle.json"));
        assertEquals(List.of(new ForeignRecipeReader.Output("farmersdelight:rice", false, 1, 1.0),
                new ForeignRecipeReader.Output("farmersdelight:straw", false, 1, 1.0)), recipe.outputs());
        ForeignRecipeReader.Recipe alloy = ForeignRecipeReader.read(fixture("1_21_1/immersiveengineering/alloy/brass.json"));
        assertEquals(2, alloy.inputs().size());
        assertEquals(List.of(new ForeignRecipeReader.Output("c:ingots/brass", true, 2, 1.0)), alloy.outputs());
    }

    @Test
    void aRecipeThatNeedsAFluidIsNotUsable() throws IOException, URISyntaxException {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(fixture("1_20_1/create/mixing/peat_mud.json"));
        assertTrue(recipe.needsFluid());
        assertTrue(!recipe.usable());
    }

    @Test
    void aBinomialAmountBecomesACountAndAChance() {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(json("""
                {"ingredient": "minecraft:gravel", "result": {"id": "minecraft:flint"},
                 "result_amount": {"type": "minecraft:binomial", "n": 2, "p": 0.25}}"""));
        assertEquals(List.of(new ForeignRecipeReader.Output("minecraft:flint", false, 2, 0.25)), recipe.outputs());
    }

    @Test
    void aChanceAboveOneIsACertainPartAndARest() {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(json("""
                {"ingredient": {"tag": "c:ores/iron"}, "result": [{"item": "thermal:iron_dust", "chance": 2.5}]}"""));
        assertEquals(List.of(new ForeignRecipeReader.Output("thermal:iron_dust", false, 2, 1.0),
                new ForeignRecipeReader.Output("thermal:iron_dust", false, 1, 0.5)), recipe.outputs());
    }

    @Test
    void immersiveEngineeringSecondariesAndSizedInputs() {
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(json("""
                {"input": {"base_ingredient": {"tag": "c:ores/copper"}, "count": 2},
                 "result": {"tag": "c:dusts/copper", "count": 2},
                 "secondaries": [{"chance": 0.1, "output": {"tag": "c:dusts/gold"}}]}"""));
        assertEquals(1, recipe.inputs().size());
        assertEquals(2, recipe.inputs().get(0).count());
        assertEquals(List.of(new ForeignRecipeReader.Output("c:dusts/copper", true, 2, 1.0),
                new ForeignRecipeReader.Output("c:dusts/gold", true, 1, 0.1)), recipe.outputs());
    }

    @Test
    void namedKeysReplaceTheDefaults() {
        ForeignRecipeReader.Keys keys = new ForeignRecipeReader.Keys(List.of("input0", "input1"), List.of("result"));
        ForeignRecipeReader.Recipe recipe = ForeignRecipeReader.read(json("""
                {"input0": {"tag": "c:ingots/copper", "count": 3}, "input1": "minecraft:zinc",
                 "result": {"id": "create:brass_ingot", "count": 4}}"""), keys);
        assertEquals(2, recipe.inputs().size());
        assertEquals(3, recipe.inputs().get(0).count());
        assertEquals(List.of(new ForeignRecipeReader.Output("create:brass_ingot", false, 4, 1.0)), recipe.outputs());
    }

    @Test
    void numbersAreReadWithAFallback() {
        JsonObject recipe = json("{\"energy\": 2400, \"name\": \"x\"}");
        assertEquals(2400, ForeignRecipeReader.number(recipe, "energy", 0), 1e-9);
        assertEquals(7, ForeignRecipeReader.number(recipe, "name", 7), 1e-9);
        assertEquals(7, ForeignRecipeReader.number(recipe, "missing", 7), 1e-9);
    }
}
