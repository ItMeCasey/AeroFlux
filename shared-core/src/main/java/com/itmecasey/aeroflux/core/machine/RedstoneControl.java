package com.itmecasey.aeroflux.core.machine;

/**
 * A machine's redstone control: the mode the player picked and the signal the machine last saw,
 * which together say whether it may work this tick.
 *
 * <p>The machine reports the signal whenever its neighbors change ({@link #signal}); a rise from
 * no signal to a signal arms a pulse when the mode is {@link RedstoneMode#PULSE}. A pulse lets the
 * machine finish one job, and {@link #jobFinished} spends it. Only one pulse is kept, so a burst
 * of quick pulses while a job runs still makes one item.
 */
public final class RedstoneControl {
    private RedstoneMode mode = RedstoneMode.ALWAYS;
    private boolean powered;
    private boolean pulseArmed;

    public RedstoneMode mode() {
        return mode;
    }

    /**
     * Picks a mode. A pulse that was waiting is dropped, so switching to pulse mode never starts a
     * job on a signal from before the switch.
     *
     * @return true if the mode changed
     */
    public boolean setMode(RedstoneMode newMode) {
        if (newMode == null || newMode == mode) {
            return false;
        }
        mode = newMode;
        pulseArmed = false;
        return true;
    }

    /** Whether the machine had a redstone signal when it last looked. */
    public boolean powered() {
        return powered;
    }

    /** Whether a pulse arrived that no finished job has used yet. */
    public boolean pulseArmed() {
        return pulseArmed;
    }

    /**
     * Takes the signal as it is now.
     *
     * @return true if it differs from the last one
     */
    public boolean signal(boolean nowPowered) {
        if (nowPowered == powered) {
            return false;
        }
        if (nowPowered && mode == RedstoneMode.PULSE) {
            pulseArmed = true;
        }
        powered = nowPowered;
        return true;
    }

    /** Whether the mode and the signal let the machine work. */
    public boolean mayRun() {
        return switch (mode) {
            case ALWAYS -> true;
            case HIGH -> powered;
            case LOW -> !powered;
            case PULSE -> pulseArmed;
        };
    }

    /** Called when a job finishes; in pulse mode it uses up the pulse that allowed it. */
    public void jobFinished() {
        if (mode == RedstoneMode.PULSE) {
            pulseArmed = false;
        }
    }

    /**
     * Restores saved values. The signal is taken as it was, with no pulse for it, since the
     * machine reads the real signal again once it ticks.
     */
    public void restore(RedstoneMode savedMode, boolean savedPowered, boolean savedPulse) {
        mode = savedMode == null ? RedstoneMode.ALWAYS : savedMode;
        powered = savedPowered;
        pulseArmed = savedPulse && mode == RedstoneMode.PULSE;
    }
}
