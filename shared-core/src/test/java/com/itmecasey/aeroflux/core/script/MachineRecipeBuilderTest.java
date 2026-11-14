package com.itmecasey.aeroflux.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MachineRecipeBuilderTest {
    private static MachineRecipeBuilder grinder(RecipeShape shape) {
        return new MachineRecipeBuilder("grinder", shape)
                .input("#c:ores/iron")
                .output("2x minecraft:raw_iron")
                .output("minecraft:gravel", 1, 0.1)
                .time(300);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> json, String key) {
        return (List<Map<String, Object>>) json.get(key);
    }

    @Test
    void version26WritesStringIngredientsAndStackResults() {
        Map<String, Object> json = grinder(RecipeShape.V26).build();
        assertEquals("aeroflux:grinder", json.get("type"));
        assertEquals(300, json.get("time"));
        assertEquals("#c:ores/iron", list(json, "inputs").get(0).get("ingredient"));
        assertEquals(Map.of("id", "minecraft:raw_iron", "count", 2), list(json, "outputs").get(0).get("item"));
        assertEquals(0.1, list(json, "outputs").get(1).get("chance"));
        assertFalse(list(json, "outputs").get(0).containsKey("chance"));
    }

    @Test
    void version121WritesObjectIngredients() {
        Map<String, Object> json = grinder(RecipeShape.V1_21).build();
        assertEquals(Map.of("tag", "c:ores/iron"), list(json, "inputs").get(0).get("ingredient"));
        assertEquals(Map.of("id", "minecraft:raw_iron", "count", 2), list(json, "outputs").get(0).get("item"));
    }

    @Test
    void version120WritesFlatResults() {
        Map<String, Object> json = new MachineRecipeBuilder("alloy_smelter", RecipeShape.V1_20)
                .input("minecraft:iron_ingot", 3)
                .input("aeroflux:flux_dust")
                .output("aeroflux:fluxsteel_ingot", 4)
                .build();
        assertEquals(3, list(json, "inputs").get(0).get("count"));
        assertEquals(Map.of("item", "minecraft:iron_ingot"), list(json, "inputs").get(0).get("ingredient"));
        assertEquals("aeroflux:fluxsteel_ingot", list(json, "outputs").get(0).get("item"));
        assertEquals(4, list(json, "outputs").get(0).get("count"));
    }

    @Test
    void aCountInFrontOfAnIngredientCounts() {
        Map<String, Object> json = new MachineRecipeBuilder("sawmill", RecipeShape.V26)
                .input("3x minecraft:oak_log").output("minecraft:oak_planks", 6).build();
        assertEquals(3, list(json, "inputs").get(0).get("count"));
        assertEquals("minecraft:oak_log", list(json, "inputs").get(0).get("ingredient"));
    }

    @Test
    void theOptionalKeysAppearOnlyWhenSet() {
        Map<String, Object> plain = grinder(RecipeShape.V26).build();
        assertFalse(plain.containsKey("flux"));
        assertFalse(plain.containsKey("flux_type"));
        assertFalse(plain.containsKey("water"));
        Map<String, Object> full = new MachineRecipeBuilder("purifier", RecipeShape.V26)
                .input("#c:ores/iron").output("3x minecraft:raw_iron").water(100).flux(50).fluxType("AERO").build();
        assertEquals(100, full.get("water"));
        assertEquals(50, full.get("flux"));
        assertEquals("aero", full.get("flux_type"));
    }

    @Test
    void onlyThePlantGrowerHasSilkOutputs() {
        assertThrows(IllegalArgumentException.class,
                () -> grinder(RecipeShape.V26).silkOutput("minecraft:oak_log", 1, 1));
        Map<String, Object> json = new MachineRecipeBuilder("plant_grower", RecipeShape.V26)
                .input("minecraft:oak_sapling").output("3x minecraft:oak_log").silkOutput("2x minecraft:oak_leaves", 1, 1).build();
        assertEquals(1, list(json, "silk_outputs").size());
    }

    @Test
    void badInputIsRefusedWithAPlainMessage() {
        assertThrows(IllegalArgumentException.class, () -> new MachineRecipeBuilder("furnace", RecipeShape.V26));
        assertThrows(IllegalArgumentException.class, () -> new MachineRecipeBuilder("nonsense", RecipeShape.V26));
        assertThrows(IllegalArgumentException.class, () -> grinder(RecipeShape.V26).time(0));
        assertThrows(IllegalArgumentException.class, () -> grinder(RecipeShape.V26).fluxType("purple"));
        assertThrows(IllegalArgumentException.class, () -> grinder(RecipeShape.V26).output("minecraft:gravel", 1, 2.0));
        assertThrows(IllegalArgumentException.class, () -> grinder(RecipeShape.V26).output("#c:gravel"));
        assertThrows(IllegalStateException.class, () -> new MachineRecipeBuilder("grinder", RecipeShape.V26).build());
    }
}
