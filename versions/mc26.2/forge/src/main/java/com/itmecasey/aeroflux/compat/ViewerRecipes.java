package com.itmecasey.aeroflux.compat;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import com.itmecasey.aeroflux.machine.MachineRecipes;
import com.itmecasey.aeroflux.machine.RecipeImports;
import com.itmecasey.aeroflux.machine.SieveImports;
import com.itmecasey.aeroflux.machine.SieveRoll;
import com.itmecasey.aeroflux.machine.recipe.FluxRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * What the recipe viewer plugins (JEI and REI) list, worked out once here so they show the same
 * thing: each machine's own recipes followed by the ones imported from other mods, and the Flux
 * Sifter's sieve drops grouped into pages. Nothing in here names a viewer class, so it also runs on
 * a dedicated server, which is where the self-test checks it.
 */
public final class ViewerRecipes {
    /** The most drops one Sifter page shows: five across, three down. */
    public static final int SIEVE_PAGE = 15;
    /** Drops per row on a Sifter page. */
    public static final int SIEVE_COLUMNS = 5;
    /** The prefix {@link RecipeImports} gives the id of every recipe it imports. */
    private static final String IMPORTED = "imported/";

    private ViewerRecipes() {
    }

    /** A recipe with its id, which tells imported recipes from the machine's own. */
    public record Entry(Identifier id, FluxRecipe recipe) {
    }

    /** A machine's recipes as this side knows them: its own first, then the imported ones. */
    public static List<Entry> machine(Level level, MachineType machine) {
        List<Entry> entries = new ArrayList<>();
        for (RecipeHolder<FluxRecipe> holder : MachineRecipes.ownOf(level, machine)) {
            entries.add(new Entry(holder.id().identifier(), holder.value()));
        }
        for (RecipeHolder<FluxRecipe> holder : RecipeImports.forViewer(level, machine)) {
            entries.add(new Entry(holder.id().identifier(), holder.value()));
        }
        return entries;
    }

    /** The mod an imported recipe came from, such as {@code "mekanism"}, or {@code null} for a recipe of the machine's own. */
    public static @Nullable String importedFrom(Identifier id) {
        if (!id.getNamespace().equals(AeroFlux.MODID) || !id.getPath().startsWith(IMPORTED)) {
            return null;
        }
        String rest = id.getPath().substring(IMPORTED.length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : null;
    }

    /** The line a viewer shows under an imported recipe, naming the mod; {@code null} for the machine's own recipes. */
    public static @Nullable Component importedLine(Identifier id) {
        String mod = importedFrom(id);
        if (mod == null) {
            return null;
        }
        String name = ModList.getModContainerById(mod).map(container -> container.getModInfo().getDisplayName()).orElse(mod);
        return Component.translatable("jei.aeroflux.imported", name);
    }

    // --- Sifter ---------------------------------------------------------------------------

    /**
     * One Sifter page: a block, a mesh and what sifting the one through the other gives.
     *
     * @param id       a stable id for the page, for viewers that want one
     * @param material what is sifted
     * @param mesh     the mesh in the mesh slot
     * @param drops    the drops, at most {@link #SIEVE_PAGE}
     * @param water    mB of water a run takes, 0 for none
     */
    public record SievePage(Identifier id, Ingredient material, Ingredient mesh, List<SieveRoll> drops, int water) {
    }

    /** The Sifter's pages as this side knows the sieve recipes. */
    public static List<SievePage> sifter(Level level) {
        return sievePages(SieveImports.forViewer(level));
    }

    /**
     * Groups sieve drops into pages, one block and mesh pair at a time, in the order the drops
     * come. A pair with more drops than fit on a page gets several pages.
     */
    public static List<SievePage> sievePages(List<SieveRoll> rolls) {
        Map<String, List<SieveRoll>> byPair = new LinkedHashMap<>();
        for (SieveRoll roll : rolls) {
            ItemStack material = first(roll.material());
            ItemStack mesh = first(roll.mesh());
            if (material.isEmpty() || mesh.isEmpty()) {
                // An empty tag; nothing a player could put in the slot.
                continue;
            }
            byPair.computeIfAbsent(idPart(material) + "/" + idPart(mesh), key -> new ArrayList<>()).add(roll);
        }
        List<SievePage> pages = new ArrayList<>();
        byPair.forEach((pair, drops) -> {
            for (int start = 0, page = 0; start < drops.size(); start += SIEVE_PAGE, page++) {
                List<SieveRoll> part = List.copyOf(drops.subList(start, Math.min(drops.size(), start + SIEVE_PAGE)));
                int water = 0;
                for (SieveRoll roll : part) {
                    water = Math.max(water, roll.water());
                }
                SieveRoll head = part.getFirst();
                pages.add(new SievePage(Ids.mod("sifter/" + pair + "/" + page), head.material(), head.mesh(), part, water));
            }
        });
        return pages;
    }

    /** The tooltip line for how many of a drop one run gives, such as "Amount: 1-3" or "Amount: 25%". */
    public static Component amountLine(SieveMesh.Amount amount) {
        return Component.translatable("jei.aeroflux.sieve.amount", amountText(amount));
    }

    /** How many of a drop one run gives, written short: {@code 2}, {@code 1-3}, {@code 25%}, {@code 3 x 50%}, or a sum of these. */
    public static String amountText(SieveMesh.Amount amount) {
        return switch (amount) {
            case SieveMesh.Constant constant -> Integer.toString(constant.count());
            case SieveMesh.Uniform uniform -> uniform.min() == uniform.max() ? Integer.toString(uniform.min())
                    : uniform.min() + "-" + uniform.max();
            case SieveMesh.Binomial binomial -> binomial.tries() == 1 ? percent(binomial.chance())
                    : binomial.tries() + " x " + percent(binomial.chance());
            case SieveMesh.Sum sum -> String.join(" + ", sum.parts().stream().map(ViewerRecipes::amountText).toList());
        };
    }

    /** A chance as a percentage with at most one decimal, without a trailing ".0". */
    private static String percent(double chance) {
        String text = String.format(Locale.ROOT, "%.1f", chance * 100.0);
        return (text.endsWith(".0") ? text.substring(0, text.length() - 2) : text) + "%";
    }

    private static ItemStack first(Ingredient ingredient) {
        return ingredient.items().findFirst().map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
    }

    private static String idPart(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace() + "/" + id.getPath();
    }
}
