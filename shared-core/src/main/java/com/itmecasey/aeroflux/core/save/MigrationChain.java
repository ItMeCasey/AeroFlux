package com.itmecasey.aeroflux.core.save;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A row of migration steps, one per old version, that bring saved data up to date one version at a
 * time. Step {@code n} turns data in the layout of version {@code n} into version {@code n + 1}.
 *
 * @param <D> the data being migrated; steps may return the same object or a new one
 */
public final class MigrationChain<D> {
    private final Map<Integer, UnaryOperator<D>> steps;

    private MigrationChain(Map<Integer, UnaryOperator<D>> steps) {
        this.steps = steps;
    }

    /** Starts a chain. */
    public static <D> Builder<D> builder() {
        return new Builder<>();
    }

    /** Collects the steps of a chain. */
    public static final class Builder<D> {
        private final Map<Integer, UnaryOperator<D>> steps = new HashMap<>();

        /**
         * Adds the step that leaves {@code from} for {@code from + 1}.
         *
         * @throws IllegalStateException if that version already has a step
         */
        public Builder<D> step(int from, UnaryOperator<D> step) {
            if (steps.putIfAbsent(from, step) != null) {
                throw new IllegalStateException("Two migration steps for version " + from);
            }
            return this;
        }

        public MigrationChain<D> build() {
            return new MigrationChain<>(Map.copyOf(steps));
        }
    }

    /** Whether there is a step for every version from {@code from} up to, not including, {@code to}. */
    public boolean covers(int from, int to) {
        for (int version = from; version < to; version++) {
            if (!steps.containsKey(version)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the steps from one version to another.
     *
     * @param from the version the data is in (0 for data from before versioning)
     * @param to   the version to bring it to; if it is not above {@code from}, the data is returned as it is
     * @throws IllegalStateException if a version in between has no step
     */
    public D migrate(D data, int from, int to) {
        D current = data;
        for (int version = Math.max(0, from); version < to; version++) {
            UnaryOperator<D> step = steps.get(version);
            if (step == null) {
                throw new IllegalStateException("No migration step for save version " + version);
            }
            current = step.apply(current);
        }
        return current;
    }
}
