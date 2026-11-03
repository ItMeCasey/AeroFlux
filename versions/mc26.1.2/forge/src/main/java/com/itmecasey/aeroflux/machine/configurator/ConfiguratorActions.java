package com.itmecasey.aeroflux.machine.configurator;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.core.configurator.ConfigArea;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.core.configurator.MachineSettings;
import com.itmecasey.aeroflux.core.configurator.PasteUndo;
import com.itmecasey.aeroflux.core.configurator.Setting;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigurable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the Flux Configurator does to the world and to its own memory. Like the Conduit Tool's
 * actions, everything here works on a level, positions, the tool stack and a plain key for the
 * undo memory, never on a player, so the self-test can run every case; the item and the network
 * handler only find the target and pass the result on.
 *
 * <p>The undo memory lives on the server, one entry per player, rather than on the item: it
 * holds a copy of every machine an area paste changed, which is more than an item should carry
 * around, and it only matters for a minute anyway.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
public final class ConfiguratorActions {
    private static final Map<UUID, PasteUndo> UNDO = new HashMap<>();

    private ConfiguratorActions() {
    }

    /**
     * What an action did.
     *
     * @param success whether it did what was asked
     * @param key     translation key of the message to show
     * @param args    arguments for the message
     * @param count   how many machines it changed, for the self-test
     */
    public record Result(boolean success, String key, List<Object> args, int count) {
        static Result of(boolean success, String key, Object... args) {
            return new Result(success, key, List.of(args), 0);
        }

        static Result counted(int count, String key, Object... args) {
            return new Result(true, key, List.of(args), count);
        }

        public Component message() {
            return Component.translatable(key, args.toArray());
        }
    }

    // --- Copy and paste ----------------------------------------------------------------------

    /** Copies a machine's settings into the selected preset. */
    public static Result copy(ItemStack tool, FluxMachineBlockEntity machine) {
        ConfiguratorPresets presets = ConfiguratorStackData.presets(tool);
        presets.store(presets.selected(), "", MachineSettings.capture(new MachineConfigurable(machine)));
        ConfiguratorStackData.setPresets(tool, presets);
        return Result.of(true, "message.aeroflux.configurator_copied", machine.getDisplayName(),
                presetLabel(presets, presets.selected()));
    }

    /**
     * Pastes the selected preset onto the machine at {@code pos}.
     *
     * @param undoKey whose undo memory records the paste, or {@code null} to record nothing
     */
    public static Result paste(ItemStack tool, ServerLevel level, BlockPos pos, UUID undoKey) {
        ConfiguratorPresets.Preset preset = ConfiguratorStackData.presets(tool).current();
        if (preset == null) {
            return Result.of(false, "message.aeroflux.configurator_empty");
        }
        if (!(level.getBlockEntity(pos) instanceof FluxMachineBlockEntity machine)) {
            return Result.of(false, "message.aeroflux.configurator_not_machine");
        }
        MachineConfigurable target = new MachineConfigurable(machine);
        MachineSettings before = MachineSettings.capture(target);
        MachineSettings.PasteReport report = preset.settings().applyTo(target);
        if (report.changed()) {
            remember(undoKey, level, List.of(new PasteUndo.Entry(toPos(pos), before)));
        }
        if (!report.skipped().isEmpty()) {
            return Result.counted(report.changed() ? 1 : 0, "message.aeroflux.configurator_pasted_skipped",
                    machine.getDisplayName(), settingList(report.skipped()));
        }
        return Result.counted(report.changed() ? 1 : 0, "message.aeroflux.configurator_pasted", machine.getDisplayName());
    }

    // --- Area paste --------------------------------------------------------------------------

    /**
     * One use in area mode. The first marks a corner, the second marks the other corner and
     * reports how many machines the box holds, and the third pastes onto them all.
     *
     * @param limit the most machines one area paste may change
     */
    public static Result areaUse(ItemStack tool, ServerLevel level, BlockPos pos, UUID undoKey, int limit) {
        return areaUse(tool, level, pos, undoKey, limit, at -> true);
    }

    /** The same, pasting only onto machines at positions {@code allowed} accepts. */
    public static Result areaUse(ItemStack tool, ServerLevel level, BlockPos pos, UUID undoKey, int limit,
                                 Predicate<BlockPos> allowed) {
        String dimension = dimension(level);
        ConfiguratorStackData.Corner first = ConfiguratorStackData.firstCorner(tool);
        ConfiguratorStackData.Corner second = ConfiguratorStackData.secondCorner(tool);
        if (first == null || !first.dimension().equals(dimension)) {
            ConfiguratorStackData.setFirstCorner(tool, new ConfiguratorStackData.Corner(toPos(pos), dimension));
            return Result.of(true, "message.aeroflux.configurator_corner_first");
        }
        if (second == null) {
            ConfigArea area = ConfigArea.between(first.pos(), toPos(pos));
            if (area.tooLarge()) {
                ConfiguratorStackData.clearCorners(tool);
                return Result.of(false, "message.aeroflux.configurator_area_too_large", ConfigArea.MAX_SIDE);
            }
            ConfiguratorStackData.setSecondCorner(tool, new ConfiguratorStackData.Corner(toPos(pos), dimension));
            int machines = machinesIn(level, area).size();
            return new Result(true, "message.aeroflux.configurator_area_marked", List.of(machines), machines);
        }
        return areaPaste(tool, level, undoKey, limit, allowed);
    }

    /** The area both marked corners span, or {@code null} while fewer than two are marked here. */
    public static ConfigArea pendingArea(ItemStack tool, ServerLevel level) {
        ConfiguratorStackData.Corner first = ConfiguratorStackData.firstCorner(tool);
        ConfiguratorStackData.Corner second = ConfiguratorStackData.secondCorner(tool);
        String dimension = dimension(level);
        if (first == null || second == null || !first.dimension().equals(dimension) || !second.dimension().equals(dimension)) {
            return null;
        }
        return ConfigArea.between(first.pos(), second.pos());
    }

    /**
     * Pastes the selected preset onto every machine in the marked area, then forgets the corners.
     * Refused when there is nothing to paste, no complete area, no machine in it, or more machines
     * than {@code limit}.
     */
    public static Result areaPaste(ItemStack tool, ServerLevel level, UUID undoKey, int limit) {
        return areaPaste(tool, level, undoKey, limit, at -> true);
    }

    /**
     * The same, but machines at positions {@code allowed} refuses are left as they are (a claim
     * mod's protection, in the game). They still count in the box, so the limit stays the same.
     */
    public static Result areaPaste(ItemStack tool, ServerLevel level, UUID undoKey, int limit, Predicate<BlockPos> allowed) {
        ConfiguratorPresets.Preset preset = ConfiguratorStackData.presets(tool).current();
        ConfigArea area = pendingArea(tool, level);
        if (area == null) {
            return Result.of(false, "message.aeroflux.configurator_no_area");
        }
        if (preset == null) {
            return Result.of(false, "message.aeroflux.configurator_empty");
        }
        List<FluxMachineBlockEntity> machines = machinesIn(level, area);
        if (!ConfigArea.checkCount(machines.size(), limit)) {
            // A box with too many machines stays marked, so the player can see it and mark a smaller one.
            return machines.isEmpty()
                    ? Result.of(false, "message.aeroflux.configurator_area_empty")
                    : Result.of(false, "message.aeroflux.configurator_area_limit", machines.size(), limit);
        }

        List<PasteUndo.Entry> before = new ArrayList<>();
        Set<Setting> skipped = EnumSet.noneOf(Setting.class);
        int changed = 0;
        for (FluxMachineBlockEntity machine : machines) {
            if (!allowed.test(machine.getBlockPos())) {
                continue;
            }
            MachineConfigurable target = new MachineConfigurable(machine);
            MachineSettings old = MachineSettings.capture(target);
            MachineSettings.PasteReport report = preset.settings().applyTo(target);
            skipped.addAll(report.skipped());
            if (report.changed()) {
                before.add(new PasteUndo.Entry(toPos(machine.getBlockPos()), old));
                changed++;
            }
        }
        ConfiguratorStackData.clearCorners(tool);
        if (!before.isEmpty()) {
            remember(undoKey, level, before);
        }
        if (!skipped.isEmpty()) {
            return Result.counted(changed, "message.aeroflux.configurator_area_pasted_skipped", machines.size(), changed,
                    settingList(skipped));
        }
        return Result.counted(changed, "message.aeroflux.configurator_area_pasted", machines.size(), changed);
    }

    /** Every machine inside an area that is loaded, in the area's own order. */
    public static List<FluxMachineBlockEntity> machinesIn(ServerLevel level, ConfigArea area) {
        List<FluxMachineBlockEntity> machines = new ArrayList<>();
        for (RunPlanner.Pos step : area.positions()) {
            BlockPos at = new BlockPos(step.x(), step.y(), step.z());
            if (level.isLoaded(at) && level.getBlockEntity(at) instanceof FluxMachineBlockEntity machine) {
                machines.add(machine);
            }
        }
        return machines;
    }

    // --- Undo --------------------------------------------------------------------------------

    /** Puts back what the last paste under {@code key} changed, once, within a minute of the paste. */
    public static Result undo(ServerLevel level, UUID key) {
        PasteUndo undo = key == null ? null : UNDO.get(key);
        if (undo == null || !undo.usable(dimension(level), level.getGameTime())) {
            UNDO.remove(key);
            return Result.of(false, "message.aeroflux.configurator_nothing_to_undo");
        }
        UNDO.remove(key);
        int restored = 0;
        for (PasteUndo.Entry entry : undo.before()) {
            BlockPos at = new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            if (level.isLoaded(at) && level.getBlockEntity(at) instanceof FluxMachineBlockEntity machine) {
                entry.settings().applyTo(new MachineConfigurable(machine));
                restored++;
            }
        }
        return Result.counted(restored, "message.aeroflux.configurator_undone", restored);
    }

    /** Whether {@code key} has a paste that can still be undone here. */
    public static boolean canUndo(ServerLevel level, UUID key) {
        PasteUndo undo = key == null ? null : UNDO.get(key);
        return undo != null && undo.usable(dimension(level), level.getGameTime());
    }

    private static void remember(UUID key, ServerLevel level, List<PasteUndo.Entry> before) {
        if (key != null) {
            UNDO.put(key, new PasteUndo(dimension(level), before, level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        UNDO.clear();
    }

    // --- Presets -----------------------------------------------------------------------------

    /** Makes a slot the one copy and paste use. */
    public static Result selectPreset(ItemStack tool, int slot) {
        ConfiguratorPresets presets = ConfiguratorStackData.presets(tool);
        if (!ConfiguratorPresets.validSlot(slot)) {
            return Result.of(false, "message.aeroflux.configurator_bad_slot");
        }
        presets.select(slot);
        ConfiguratorStackData.setPresets(tool, presets);
        return Result.of(true, "message.aeroflux.configurator_selected", presetLabel(presets, slot));
    }

    /** Names a filled slot. */
    public static Result renamePreset(ItemStack tool, int slot, String name) {
        ConfiguratorPresets presets = ConfiguratorStackData.presets(tool);
        if (!presets.rename(slot, name)) {
            return Result.of(false, "message.aeroflux.configurator_rename_failed");
        }
        ConfiguratorStackData.setPresets(tool, presets);
        return Result.of(true, "message.aeroflux.configurator_renamed", presetLabel(presets, slot));
    }

    /** Empties a slot. */
    public static Result clearPreset(ItemStack tool, int slot) {
        ConfiguratorPresets presets = ConfiguratorStackData.presets(tool);
        if (!presets.clear(slot)) {
            return Result.of(false, "message.aeroflux.configurator_slot_empty");
        }
        ConfiguratorStackData.setPresets(tool, presets);
        return Result.of(true, "message.aeroflux.configurator_cleared", slot + 1);
    }

    // --- Names -------------------------------------------------------------------------------

    /**
     * How a slot is shown: its name, or "Preset 3" while it has none. The name a slot gets from a
     * copy is empty on purpose, so it is shown in the player's own language rather than in
     * whatever language the server happened to use.
     */
    public static Component presetLabel(ConfiguratorPresets presets, int slot) {
        ConfiguratorPresets.Preset preset = presets.get(slot);
        if (preset != null && !preset.name().isEmpty()) {
            return Component.literal(preset.name());
        }
        return Component.translatable("gui.aeroflux.configurator.slot", slot + 1);
    }

    /** The machine a preset was copied from, as a block name, or the raw kind if it is not a machine this version knows. */
    public static Component kindName(String kind) {
        MachineType type = MachineType.byId(kind);
        return type == null ? Component.literal(kind) : Component.translatable("block." + AeroFlux.MODID + "." + type.blockId());
    }

    /** A list of settings, such as "Energy faces, Seed refill". */
    public static Component settingList(Set<Setting> settings) {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Setting setting : settings) {
            if (!first) {
                list.append(", ");
            }
            list.append(Component.translatable("setting.aeroflux." + setting.id()));
            first = false;
        }
        return list;
    }

    private static RunPlanner.Pos toPos(BlockPos pos) {
        return new RunPlanner.Pos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
