package com.itmecasey.aeroflux.protect;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.protect.ProtectionAction;
import com.itmecasey.aeroflux.core.protect.ProtectionPolicy;
import com.itmecasey.aeroflux.core.protect.Verdict;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import javax.annotation.Nullable;

/**
 * The protection layer: before a machine acts on a place or an entity, or a tool edits a block,
 * this asks whether the owner (or the player) may. The answers come from the standard permission
 * events, which every claim mod listens to, and from Open Parties and Claims and FTB Chunks directly
 * when they are installed; any denial stops the action. {@link ProtectionPolicy} holds the rules.
 *
 * <p>A machine with no recorded owner is never asked and acts as it always did, and the whole layer
 * has a config switch. Machines keep a short-lived cache of the answers (see
 * {@code ProtectionCache}) so a claim is not asked about every tick.
 */
public final class Protection {
    private static final List<ProtectionProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Set<Class<?>> BROKEN = new HashSet<>();
    private static final String STAND_IN_NAME = "[AeroFlux]";
    private static boolean installed;
    /** Replaces the config switch when not {@code null}; the self-test uses it. */
    @Nullable
    private static Boolean enabledOverride;

    private Protection() {
    }

    // --- Switches and providers ---------------------------------------------------------------

    /** Whether protection is on: the config switch, unless a test replaced it. */
    public static boolean enabled() {
        return enabledOverride != null ? enabledOverride : AeroFluxConfig.protectionEnabled();
    }

    /** Overrides the config switch; pass {@code null} to go back to the config. For tests. */
    public static void setEnabledOverride(@Nullable Boolean enabled) {
        enabledOverride = enabled;
    }

    /** Adds a source of answers, such as another mod's claim system. */
    public static void addProvider(ProtectionProvider provider) {
        PROVIDERS.add(provider);
    }

    public static void removeProvider(ProtectionProvider provider) {
        PROVIDERS.remove(provider);
    }

    /** The built-in providers go in the first time they are needed, when the mod list is final. */
    private static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        PROVIDERS.add(new EventProtection());
        // The claim mod's classes are only touched inside this branch, so a server without it never loads any.
        // FTB Chunks has no Forge build for this Minecraft version, so only Open Parties and Claims is bridged.
        if (ModList.get().isLoaded("openpartiesandclaims")) {
            PROVIDERS.add(new OpacProtection());
        }
    }

    // --- Questions ----------------------------------------------------------------------------

    /**
     * Whether a machine owned by {@code owner} may act on a block position. Always true when the
     * owner is unknown or protection is off.
     */
    public static boolean mayAct(ServerLevel level, @Nullable UUID owner, BlockPos pos, ProtectionAction action) {
        if (!ProtectionPolicy.shouldAsk(enabled(), owner != null)) {
            return true;
        }
        return ProtectionPolicy.machineMayAct(true, true,
                ask(new ProtectionProvider.Request(level, standIn(level, owner), pos, null, action)));
    }

    /** Whether a machine owned by {@code owner} may act on an entity. */
    public static boolean mayTouch(ServerLevel level, @Nullable UUID owner, Entity target) {
        if (!ProtectionPolicy.shouldAsk(enabled(), owner != null)) {
            return true;
        }
        return ProtectionPolicy.machineMayAct(true, true, ask(new ProtectionProvider.Request(
                level, standIn(level, owner), target.blockPosition(), target, ProtectionAction.USE_ENTITY)));
    }

    /** Whether a player may do something to a block with a tool. Players on the client are not asked. */
    public static boolean mayPlayerAct(@Nullable Player player, BlockPos pos, ProtectionAction action) {
        if (player == null || !(player.level() instanceof ServerLevel level) || !enabled()) {
            return true;
        }
        return ProtectionPolicy.playerMayAct(true, ask(new ProtectionProvider.Request(level, player, pos, null, action)));
    }

    private static List<Verdict> ask(ProtectionProvider.Request request) {
        install();
        List<Verdict> answers = new ArrayList<>(PROVIDERS.size());
        for (ProtectionProvider provider : PROVIDERS) {
            try {
                answers.add(provider.check(request));
            } catch (RuntimeException failure) {
                // A claim mod that throws must not take machines down with it; ask the others and say so once.
                if (BROKEN.add(provider.getClass())) {
                    AeroFlux.LOGGER.error("The protection check of {} failed, so it is skipped:", provider.getClass().getSimpleName(), failure);
                }
                answers.add(Verdict.ABSTAIN);
            }
        }
        return answers;
    }

    /**
     * The player the owner acts as: Forge's fake player, which never joins, carrying the owner's id
     * and name so a claim mod judges the machine as the owner. Works whether or not the owner is online.
     */
    private static Player standIn(ServerLevel level, UUID owner) {
        String name = level.getServer().getProfileCache() == null ? STAND_IN_NAME
                : level.getServer().getProfileCache().get(owner).map(GameProfile::getName).orElse(STAND_IN_NAME);
        return FakePlayerFactory.get(level, new GameProfile(owner, name));
    }
}
