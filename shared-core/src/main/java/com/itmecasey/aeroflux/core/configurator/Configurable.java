package com.itmecasey.aeroflux.core.configurator;

import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideConfigs;

/**
 * A block whose settings the Flux Configurator can read and write. Each game version wraps its
 * machine block entity in one of these, so {@link MachineSettings} can do the copying and pasting
 * without knowing anything about the game.
 *
 * <p>The optional settings come with a {@code has...} method; a block without the setting is
 * never asked for it or told to change it.
 */
public interface Configurable {
    /** The kind of block, such as {@code "furnace"}; machine-only switches carry over only between the same kind. */
    String kind();

    /** The face the block's front points to; face settings are copied relative to it. */
    Face facing();

    /** The block's live face settings. {@link #settingsChanged} is called after they were changed. */
    SideConfigs sides();

    RedstoneMode redstoneMode();

    void setRedstoneMode(RedstoneMode mode);

    boolean autoImport();

    void setAutoImport(boolean on);

    boolean autoExport();

    void setAutoExport(boolean on);

    /** Whether the block has a seed refill switch (only the Plant Grower does). */
    boolean hasAutoInsert();

    boolean autoInsert();

    void setAutoInsert(boolean on);

    /** Whether the block has a switch of its own, such as the Magnet's whitelist. */
    boolean hasMachineMode();

    int machineMode();

    void setMachineMode(int mode);

    /** Saves the block and tells its neighbors after a paste changed something. */
    void settingsChanged();
}
