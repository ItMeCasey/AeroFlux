package com.itmecasey.aeroflux.core.machine;

import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * The rules of the Sifter, which runs the sieve recipes of Ex Deorum and Ex Nihilo
 * Sequentia. The player puts a mesh from either mod in its mesh slot; the mesh decides which
 * recipes run, exactly as it does in those mods' own sieves, and the sifter's tier only sets its
 * speed. The mesh is never used up.
 */
public final class SieveMesh {
    /** The slot that holds the block being sifted. */
    public static final int MATERIAL_SLOT = 0;
    /** The slot that holds the mesh. It takes one mesh. */
    public static final int MESH_SLOT = 1;

    private SieveMesh() {
    }

    /**
     * The item id of an Ex Nihilo Sequentia mesh. Its recipes name a mesh by its kind, such as
     * {@code "flint"} or {@code "FLINT"}, and the mod's mesh items are named after the kind.
     */
    public static String nihiloMeshItem(String kind) {
        return "exnihilosequentia:" + kind.toLowerCase(Locale.ROOT).trim() + "_mesh";
    }

    /**
     * How many of a product one sieve run gives. Ex Deorum writes this as a vanilla number provider
     * (a constant, a uniform range, a binomial roll, or a sum of these); an Ex Nihilo Sequentia roll
     * is one item at its chance, a binomial of one try.
     */
    public sealed interface Amount {
        /** How many this run gives, from random numbers between 0 (inclusive) and 1 (exclusive). */
        int roll(DoubleSupplier random);

        /** The same amount with Fortune Upgrades: each one raises a binomial's chance a little. */
        Amount withFortune(int fortuneUpgrades);
    }

    /** Always the same number. */
    public record Constant(int count) implements Amount {
        @Override
        public int roll(DoubleSupplier random) {
            return Math.max(0, count);
        }

        @Override
        public Amount withFortune(int fortuneUpgrades) {
            return this;
        }
    }

    /** Anything from {@code min} to {@code max}, each as likely. */
    public record Uniform(int min, int max) implements Amount {
        @Override
        public int roll(DoubleSupplier random) {
            int low = Math.max(0, Math.min(min, max));
            int high = Math.max(0, Math.max(min, max));
            return low + Math.min(high - low, (int) (random.getAsDouble() * (high - low + 1)));
        }

        @Override
        public Amount withFortune(int fortuneUpgrades) {
            return this;
        }
    }

    /** {@code tries} rolls, each giving one item with chance {@code chance}. */
    public record Binomial(int tries, double chance) implements Amount {
        @Override
        public int roll(DoubleSupplier random) {
            int count = 0;
            for (int i = 0; i < tries; i++) {
                if (random.getAsDouble() < chance) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Amount withFortune(int fortuneUpgrades) {
            return new Binomial(tries, MachineUpgrades.sifterChance(chance, fortuneUpgrades));
        }
    }

    /** Several amounts added together. */
    public record Sum(List<Amount> parts) implements Amount {
        public Sum {
            parts = List.copyOf(parts);
        }

        @Override
        public int roll(DoubleSupplier random) {
            int count = 0;
            for (Amount part : parts) {
                count += part.roll(random);
            }
            return count;
        }

        @Override
        public Amount withFortune(int fortuneUpgrades) {
            return new Sum(parts.stream().map(part -> part.withFortune(fortuneUpgrades)).toList());
        }
    }
}
