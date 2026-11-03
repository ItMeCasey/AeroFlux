package com.itmecasey.aeroflux.core.machine;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Which soils a plant grows on in the Plant Grower, and what a soil gives it, judged from the
 * rules in the data packs. Nothing here knows Minecraft: the caller says whether a plant or soil
 * matches an entry, where an entry is an id such as {@code minecraft:farmland} or a tag written
 * {@code #minecraft:dirt}.
 *
 * <p>Every plant has a default soil (farmland for crops, dirt for everything else; the caller
 * decides which, from the plant's block). A rule can replace that default for the plants it
 * names, accept more soils, and give a bonus on them:
 * <ul>
 *     <li>{@code speed} multiplies how fast the plant grows;</li>
 *     <li>{@code extraSeedChance} is the chance of one more seed in a harvest, which is how
 *     Mystical Agriculture's essence farmland pays out.</li>
 * </ul>
 */
public final class PlantSoilRules {
    private PlantSoilRules() {
    }

    /**
     * One rule from a data pack.
     *
     * @param plants          seed entries it applies to; empty means every plant
     * @param soils           soil entries it accepts
     * @param replacesDefault whether the named plants lose their default soil
     * @param speed           growth speed multiplier on these soils (1 changes nothing)
     * @param extraSeedChance chance of one more seed per harvest on these soils
     * @param tierMatched     whether the rule holds only where the soil suits the plant's tier, as Mystical
     *                        Agriculture's essence farmland does for the crops of its own tier
     */
    public record Rule(List<String> plants, List<String> soils, boolean replacesDefault, double speed,
                       double extraSeedChance, boolean tierMatched) {
        public Rule {
            plants = List.copyOf(plants);
            soils = List.copyOf(soils);
        }

        /** A rule that does not look at tiers. */
        public Rule(List<String> plants, List<String> soils, boolean replacesDefault, double speed,
                    double extraSeedChance) {
            this(plants, soils, replacesDefault, speed, extraSeedChance, false);
        }
    }

    /** How a soil suits a plant: whether it grows there at all, how fast, and the seed bonus. */
    public record Verdict(boolean grows, double speed, double extraSeedChance) {
        /** A soil the plant cannot grow on. */
        public static final Verdict NO = new Verdict(false, 1.0, 0.0);
        /** A soil that works and gives nothing extra. */
        public static final Verdict PLAIN = new Verdict(true, 1.0, 0.0);
    }

    /**
     * Judges a soil for a plant.
     *
     * @param rules         every loaded rule
     * @param defaultSoil   whether the soil is one of the plant's default kind
     * @param plantMatches  tells whether the plant is the given entry
     * @param soilMatches   tells whether the soil is the given entry
     */
    public static Verdict judge(List<Rule> rules, boolean defaultSoil, Predicate<String> plantMatches,
                                Predicate<String> soilMatches) {
        return judge(rules, defaultSoil, plantMatches, soilMatches, () -> false);
    }

    /**
     * Judges a soil for a plant, with a say on tiers.
     *
     * @param tierMatches tells whether the soil is the one its plant's tier calls for; only rules marked
     *                    {@link Rule#tierMatched()} ask
     */
    public static Verdict judge(List<Rule> rules, boolean defaultSoil, Predicate<String> plantMatches,
                                Predicate<String> soilMatches, BooleanSupplier tierMatches) {
        boolean defaultApplies = true;
        boolean accepted = false;
        double speed = 1.0;
        double extraSeeds = 0.0;
        for (Rule rule : rules) {
            if (!rule.plants().isEmpty() && rule.plants().stream().noneMatch(plantMatches)) {
                continue;
            }
            if (rule.replacesDefault()) {
                defaultApplies = false;
            }
            if (rule.soils().stream().anyMatch(soilMatches) && (!rule.tierMatched() || tierMatches.getAsBoolean())) {
                accepted = true;
                speed = Math.max(speed, rule.speed());
                extraSeeds += rule.extraSeedChance();
            }
        }
        if (!accepted && !(defaultApplies && defaultSoil)) {
            return Verdict.NO;
        }
        return new Verdict(true, speed, Math.min(extraSeeds, 1.0));
    }
}
