package com.itmecasey.aeroflux;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * How much each AeroFlux block holds, as the server's config sets it.
 *
 * <p>The sizes live in {@code aeroflux-common.toml}, and every game, client or server, reads its
 * own copy of that file. A client joined to a server must not use its own copy: the tank picture,
 * screens and tooltips would show its sizes instead of the server's. So the server sends its
 * table when a player joins and again when the config reloads (see {@link StorageSizesSync}), and
 * on a client this class answers from that table. Until one arrives, and always on the server,
 * it answers from the config. In single player both are the same file, so they agree.
 */
public final class StorageSizes {
    /** The kinds of storage that have a size per tier, in the order the table is sent. */
    public enum Kind {
        TANK,
        COLLECTOR,
        MACHINE,
        CONVERTER_TANK,
        CONVERTER_FE,
        CONVERTER_AE,
        CHUNK_LOADER
    }

    /** The table the server sent, or {@code null} when there is none (on the server, or before joining). */
    private static volatile @Nullable Table fromServer;

    private StorageSizes() {
    }

    /** A Flux Tank's size, in mB. */
    public static int tank(Tier tier) {
        return size(Kind.TANK, tier);
    }

    /** A collector's buffer, in mB. */
    public static int collector(Tier tier) {
        return size(Kind.COLLECTOR, tier);
    }

    /** A machine's Flux tank (and each of its extra tanks) before Capacity Upgrades, in mB. */
    public static int machine(Tier tier) {
        return size(Kind.MACHINE, tier);
    }

    /** A machine's Flux tank with this many Capacity Upgrades, in mB. */
    public static int machine(Tier tier, int capacityUpgrades) {
        return MachineUpgrades.inputCapacity(machine(tier), capacityUpgrades);
    }

    /** A converter's Flux tank, in mB. */
    public static int converterTank(Tier tier) {
        return size(Kind.CONVERTER_TANK, tier);
    }

    /** A converter's output buffer: FE for the FE converter, AE for the AE one. */
    public static int converterBuffer(Tier tier, ConverterType type) {
        return size(type == ConverterType.AE ? Kind.CONVERTER_AE : Kind.CONVERTER_FE, tier);
    }

    /** A chunk loader's Flux tank, in mB. */
    public static int chunkLoader(Tier tier) {
        return size(Kind.CHUNK_LOADER, tier);
    }

    /** A Sun or Moon Lamp's Flux tank, in mB. */
    public static int lamp() {
        Table table = fromServer;
        return table != null ? table.lamp() : AeroFluxConfig.lampTank();
    }

    private static int size(Kind kind, Tier tier) {
        Table table = fromServer;
        return table != null ? table.size(kind, tier) : fromConfig(kind, tier);
    }

    private static int fromConfig(Kind kind, Tier tier) {
        return switch (kind) {
            case TANK -> AeroFluxConfig.tankCapacity(tier);
            case COLLECTOR -> AeroFluxConfig.collectorBuffer(tier);
            case MACHINE -> AeroFluxConfig.machineTank(tier);
            case CONVERTER_TANK -> AeroFluxConfig.converterTank(tier);
            case CONVERTER_FE -> AeroFluxConfig.converterBuffer(tier, ConverterType.ENERGY);
            case CONVERTER_AE -> AeroFluxConfig.converterBuffer(tier, ConverterType.AE);
            case CHUNK_LOADER -> AeroFluxConfig.chunkLoaderTank(tier);
        };
    }

    /** This game's own sizes, from its config: what a server sends its players. */
    public static Table fromConfig() {
        int[][] sizes = new int[Kind.values().length][Tier.values().length];
        for (Kind kind : Kind.values()) {
            for (Tier tier : Tier.values()) {
                sizes[kind.ordinal()][tier.ordinal()] = fromConfig(kind, tier);
            }
        }
        return new Table(sizes, AeroFluxConfig.lampTank());
    }

    /** Takes the server's table. Called on the client when it arrives, and by the self-test. */
    public static void useServerSizes(Table table) {
        fromServer = table;
    }

    /** Forgets the server's table, so the next world starts from this game's own config again. */
    public static void forgetServerSizes() {
        fromServer = null;
    }

    /** Every size, by kind and tier, plus the lamp's. */
    public record Table(int[][] sizes, int lamp) {
        public int size(Kind kind, Tier tier) {
            return sizes[kind.ordinal()][tier.ordinal()];
        }

        public void write(FriendlyByteBuf buf) {
            for (int[] row : sizes) {
                for (int size : row) {
                    buf.writeVarInt(size);
                }
            }
            buf.writeVarInt(lamp);
        }

        public static Table read(FriendlyByteBuf buf) {
            int[][] sizes = new int[Kind.values().length][Tier.values().length];
            for (int[] row : sizes) {
                for (int i = 0; i < row.length; i++) {
                    row[i] = buf.readVarInt();
                }
            }
            return new Table(sizes, buf.readVarInt());
        }
    }
}
