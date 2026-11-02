package com.itmecasey.aeroflux.machine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.aeroflux.AeroFlux;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every recipe file the data packs hold, by recipe type, as JSON. Minecraft 1.20.1 recipes cannot
 * write themselves back out, so AeroFlux reads other mods' recipes from their files instead (see
 * {@link RecipeImports} and {@link SieveImports}). A file only counts when the game really loaded a
 * recipe under its id ({@link #loaded}), so failed load conditions and recipes a script removed
 * are left out, as they are everywhere else.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class RecipeFiles {
    /** One recipe file: its id and its JSON. */
    public record Entry(ResourceLocation id, JsonObject json) {
    }

    private static volatile Map<String, List<Entry>> byType = Map.of();

    private RecipeFiles() {
    }

    /** The recipe files of one type, such as {@code exdeorum:sieve}, that the game loaded a recipe for. */
    public static List<Entry> loaded(ServerLevel level, String type) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : byType.getOrDefault(type, List.of())) {
            if (level.getRecipeManager().byKey(entry.id()).isPresent()) {
                found.add(entry);
            }
        }
        return found;
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "recipes");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, List<Entry>> grouped = new HashMap<>();
            files.forEach((id, json) -> {
                if (json.isJsonObject() && json.getAsJsonObject().has("type")) {
                    JsonObject object = json.getAsJsonObject();
                    grouped.computeIfAbsent(object.get("type").getAsString(), type -> new ArrayList<>()).add(new Entry(id, object));
                }
            });
            byType = grouped;
        }
    }
}
