package com.itmecasey.aeroflux.core.script;

/**
 * Read-only snapshots handed to scripts. Each one is a plain object with public fields, taken at the moment of the call;
 * scripts cannot change the game through them.
 */
public final class ScriptInfo {
    private ScriptInfo() {
    }

    /** A machine: what it is, how far along, and the state of its Flux. */
    public static final class Machine {
        public final String id;
        public final String tier;
        /** idle, running, no_flux, output_full or disabled. */
        public final String status;
        /** Ticks into the current job, and ticks the job takes (0 with no job). */
        public final int progress;
        public final int totalTime;
        /** mB of Flux burned per tick while it runs. */
        public final int fluxPerTick;
        public final int speedUpgrades;
        public final int capacityUpgrades;
        public final String redstoneMode;
        public final Flux flux;

        public Machine(String id, String tier, String status, int progress, int totalTime, int fluxPerTick, int speedUpgrades,
                       int capacityUpgrades, String redstoneMode, Flux flux) {
            this.id = id;
            this.tier = tier;
            this.status = status;
            this.progress = progress;
            this.totalTime = totalTime;
            this.fluxPerTick = fluxPerTick;
            this.speedUpgrades = speedUpgrades;
            this.capacityUpgrades = capacityUpgrades;
            this.redstoneMode = redstoneMode;
            this.flux = flux;
        }
    }

    /** The Flux a block holds. */
    public static final class Flux {
        /** What holds it: machine, collector, tank, converter or chunk_loader. */
        public final String holder;
        /** aero, moon or twilight; empty when the tank is empty. */
        public final String type;
        public final long amount;
        public final long capacity;

        public Flux(String holder, String type, long amount, long capacity) {
            this.holder = holder;
            this.type = type;
            this.amount = amount;
            this.capacity = capacity;
        }
    }

    /** One network of pipes of a single kind: fluid, item, energy, gas, redstone. */
    public static final class Network {
        public final String pipe;
        /** Bundles in the network. */
        public final int bundles;
        /** Machine faces that feed it, and machine faces that take from it. */
        public final int sources;
        public final int destinations;
        /** mB (fluid, gas) or FE (energy) per tick; 0 for kinds without a rate. */
        public final long amountPerTick;
        /** For item pipes: how many items move at once, and how many ticks pass between moves. */
        public final int itemsPerOperation;
        public final int ticksPerOperation;
        /** Whether the search stopped at its size limit. */
        public final boolean truncated;

        public Network(String pipe, int bundles, int sources, int destinations, long amountPerTick, int itemsPerOperation,
                       int ticksPerOperation, boolean truncated) {
            this.pipe = pipe;
            this.bundles = bundles;
            this.sources = sources;
            this.destinations = destinations;
            this.amountPerTick = amountPerTick;
            this.itemsPerOperation = itemsPerOperation;
            this.ticksPerOperation = ticksPerOperation;
            this.truncated = truncated;
        }
    }

    /** What the server keeps for one player. */
    public static final class Player {
        public final String name;
        public final String uuid;
        /** Chunk loaders of the player that are running, and how many the server allows (0 for no limit). */
        public final int chunkLoaders;
        public final int chunkLoaderLimit;

        public Player(String name, String uuid, int chunkLoaders, int chunkLoaderLimit) {
            this.name = name;
            this.uuid = uuid;
            this.chunkLoaders = chunkLoaders;
            this.chunkLoaderLimit = chunkLoaderLimit;
        }
    }
}
