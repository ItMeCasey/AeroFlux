package com.itmecasey.aeroflux.core.machine;

import com.itmecasey.aeroflux.core.Tier;
import java.util.List;
import java.util.Locale;

/**
 * The Flux Sifter's mesh. The sifter's tier is its mesh, and it runs every sieve recipe whose mesh
 * is at or below its own on the ranking: string, flint, iron, emerald, diamond, netherite. A mesh
 * name the ranking does not know (from another mod) counts as the strongest, so only a fluxium
 * sifter runs it. The fluxium sifter runs everything, a little faster and luckier.
 */
public final class SieveMesh {
    /** The meshes from weakest to strongest, as recipes name them. */
    public static final List<String> RANKING = List.of("string", "flint", "iron", "emerald", "diamond", "netherite");

    /** The extra chance a fluxium sifter adds to every roll, as a fraction of the roll's own chance. */
    public static final double FLUXIUM_CHANCE_BONUS = 0.25;

    private SieveMesh() {
    }

    /**
     * The plain mesh name in a recipe's mesh field: {@code "exdeorum:iron_mesh"} and
     * {@code "IRON"} both become {@code "iron"}. An empty name means "any mesh" and stays empty.
     */
    public static String normalize(String mesh) {
        String name = mesh.toLowerCase(Locale.ROOT).trim();
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (name.endsWith("_mesh")) {
            name = name.substring(0, name.length() - "_mesh".length());
        }
        return name;
    }

    /** Where a mesh ranks, 0 for string; an unknown mesh ranks above every known one. */
    public static int rank(String mesh) {
        String name = normalize(mesh);
        if (name.isEmpty()) {
            return 0;
        }
        int index = RANKING.indexOf(name);
        return index >= 0 ? index : RANKING.size();
    }

    /** The mesh a sifter of this tier has: string, flint, iron, diamond, netherite, then fluxium above all. */
    public static int tierRank(Tier tier) {
        return switch (tier) {
            case COPPER -> RANKING.indexOf("string");
            case IRON -> RANKING.indexOf("flint");
            case GOLD -> RANKING.indexOf("iron");
            case DIAMOND -> RANKING.indexOf("diamond");
            case NETHERITE -> RANKING.indexOf("netherite");
            case FLUXIUM -> Integer.MAX_VALUE;
        };
    }

    /** Whether a sifter of this tier runs a recipe for this mesh. */
    public static boolean runs(Tier tier, String mesh) {
        return rank(mesh) <= tierRank(tier);
    }

    /** A roll's chance in a sifter of this tier: fluxium adds its bonus, never past certain. */
    public static double chance(Tier tier, double chance) {
        return tier == Tier.FLUXIUM ? Math.min(1.0, chance * (1.0 + FLUXIUM_CHANCE_BONUS)) : chance;
    }
}
