package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.ConnectionMode;
import com.itmecasey.aeroflux.core.conduit.Face;
import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.core.configurator.ConfigArea;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorMode;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import com.itmecasey.aeroflux.core.machine.FaceLabel;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.machine.RedstoneMode;
import com.itmecasey.aeroflux.core.machine.SideCommand;
import com.itmecasey.aeroflux.core.machine.SideResource;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorActions;
import com.itmecasey.aeroflux.machine.configurator.ConfiguratorStackData;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The Flux Configurator part of the self-test: copy, paste, area paste, presets and undo, all
 * through {@link ConfiguratorActions}, which needs no player. The machines stand in a layer of
 * their own and are never given Flux, so nothing in them runs while the settings are checked.
 */
final class ConfiguratorSelfTest {
    private static final int Y = 292;
    /** The undo memory these checks use, as if it were one player's. */
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000c0f1");

    private static ServerLevel level;
    private static boolean started;
    private static boolean finished;

    private ConfiguratorSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        started = false;
        finished = false;
    }

    /** Runs every check once blocks in the layer tick. */
    static void tick() {
        if (level == null || finished || started || !level.shouldTickBlocksAt(new BlockPos(2, Y, 2))) {
            return;
        }
        started = true;
        checkCopyAndPaste();
        checkOtherMachines();
        checkAreaPaste();
        checkAreaRefusals();
        checkPresets();
        checkSingleUndo();
        finished = true;
    }

    // --- Checks ------------------------------------------------------------------------------

    private static void checkCopyAndPaste() {
        FluxMachineBlockEntity source = configured(machine(MachineType.FURNACE, 2, 2, Direction.NORTH));
        ItemStack tool = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
        SelfTest.check("configurator: a new Configurator pastes in single mode and holds nothing",
                ConfiguratorStackData.mode(tool) == ConfiguratorMode.PASTE && ConfiguratorStackData.presets(tool).current() == null
                        && tool.getMaxStackSize() == 1);

        ConfiguratorActions.Result copied = ConfiguratorActions.copy(tool, source);
        ConfiguratorPresets presets = ConfiguratorStackData.presets(tool);
        SelfTest.check("configurator: sneak-use copies a machine into the selected preset",
                copied.success() && "message.aeroflux.configurator_copied".equals(copied.key())
                        && presets.current() != null && "furnace".equals(presets.current().settings().kind()));

        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, tool).getOrThrow();
        ItemStack reloaded = ItemStack.CODEC.parse(ops, saved).result().orElse(ItemStack.EMPTY);
        SelfTest.check("configurator: the copy survives saving and loading the stack",
                ConfiguratorStackData.presets(reloaded).equals(presets));

        BlockPos targetPos = machine(MachineType.FURNACE, 4, 2, Direction.EAST);
        ConfiguratorActions.Result pasted = ConfiguratorActions.paste(tool, level, targetPos, null);
        FluxMachineBlockEntity target = at(targetPos);
        boolean sameByLabel = true;
        for (FaceLabel label : FaceLabel.values()) {
            sameByLabel &= labeled(target, SideResource.ITEMS, label) == labeled(source, SideResource.ITEMS, label)
                    && labeled(target, SideResource.FLUID, label) == labeled(source, SideResource.FLUID, label);
        }
        SelfTest.check("configurator: a paste lands relative to the target's facing", sameByLabel && pasted.count() == 1
                && target.sides().mode(SideResource.ITEMS, Face.WEST) == ConnectionMode.IMPORT);
        SelfTest.check("configurator: the redstone mode and Auto Import are pasted",
                target.redstoneMode() == RedstoneMode.HIGH && target.autoImport() && !target.autoExport());
        SelfTest.check("configurator: a paste reports the machine it changed",
                "message.aeroflux.configurator_pasted".equals(pasted.key()));
        SelfTest.check("configurator: copying leaves the source as it was",
                source.redstoneMode() == RedstoneMode.HIGH && labeled(source, SideResource.ITEMS, FaceLabel.FRONT) == ConnectionMode.BLOCKED);

        level.setBlock(new BlockPos(6, Y, 2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ConfiguratorActions.Result stone = ConfiguratorActions.paste(tool, level, new BlockPos(6, Y, 2), null);
        SelfTest.check("configurator: a block that is not a machine is refused",
                !stone.success() && "message.aeroflux.configurator_not_machine".equals(stone.key()));
    }

    /** Pasting across kinds of machine: shared fields carry over, machine-only ones are skipped and named. */
    private static void checkOtherMachines() {
        ItemStack tool = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
        ConfiguratorActions.copy(tool, configured(machine(MachineType.FURNACE, 2, 4, Direction.NORTH)));
        BlockPos grinderPos = machine(MachineType.GRINDER, 4, 4, Direction.SOUTH);
        ConfiguratorActions.Result onGrinder = ConfiguratorActions.paste(tool, level, grinderPos, null);
        SelfTest.check("configurator: a furnace's settings paste onto a grinder",
                "message.aeroflux.configurator_pasted".equals(onGrinder.key()) && at(grinderPos).redstoneMode() == RedstoneMode.HIGH
                        && labeled(at(grinderPos), SideResource.ITEMS, FaceLabel.BACK) == ConnectionMode.IMPORT);

        FluxMachineBlockEntity grower = at(machine(MachineType.PLANT_GROWER, 6, 4, Direction.NORTH));
        MachineConfigActions.applyButton(grower, MachineConfigActions.AUTO_INSERT_BUTTON);
        ConfiguratorActions.copy(tool, grower);
        BlockPos furnacePos = machine(MachineType.FURNACE, 8, 4, Direction.NORTH);
        ConfiguratorActions.Result onFurnace = ConfiguratorActions.paste(tool, level, furnacePos, null);
        SelfTest.check("configurator: a field the target lacks is skipped and named",
                "message.aeroflux.configurator_pasted_skipped".equals(onFurnace.key()) && grower.autoInsert());

        FluxMachineBlockEntity magnet = at(machine(MachineType.MAGNET, 10, 4, Direction.NORTH));
        MachineConfigActions.applyButton(magnet, MachineConfigActions.MAGNET_MODE_BUTTON);
        int switched = magnet.machineMode();
        ConfiguratorActions.copy(tool, magnet);
        BlockPos otherMagnet = machine(MachineType.MAGNET, 12, 4, Direction.NORTH);
        int fresh = at(otherMagnet).machineMode();
        ConfiguratorActions.paste(tool, level, otherMagnet, null);
        SelfTest.check("configurator: the Magnet's filter switch carries over to another Magnet",
                fresh != switched && at(otherMagnet).machineMode() == switched);
    }

    /** Four machines inside a box and one outside it. */
    private static void checkAreaPaste() {
        ItemStack tool = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
        ConfiguratorActions.copy(tool, configured(machine(MachineType.FURNACE, 2, 6, Direction.NORTH)));
        int y = Y + 2;
        BlockPos[] inside = {
                machineAt(MachineType.FURNACE, 2, y, 8, Direction.NORTH), machineAt(MachineType.FURNACE, 4, y, 8, Direction.WEST),
                machineAt(MachineType.GRINDER, 6, y, 8, Direction.SOUTH), machineAt(MachineType.SAWMILL, 8, y + 1, 9, Direction.EAST)};
        BlockPos outside = machineAt(MachineType.FURNACE, 12, y, 8, Direction.NORTH);

        ConfiguratorStackData.setMode(tool, ConfiguratorMode.AREA);
        ConfiguratorActions.Result first = ConfiguratorActions.areaUse(tool, level, new BlockPos(1, y, 7), PLAYER, 64);
        SelfTest.check("configurator area: the first use marks a corner",
                "message.aeroflux.configurator_corner_first".equals(first.key()) && ConfiguratorStackData.firstCorner(tool) != null);
        ConfiguratorActions.Result second = ConfiguratorActions.areaUse(tool, level, new BlockPos(9, y + 1, 10), PLAYER, 64);
        SelfTest.check("configurator area: the second use counts the machines in the box",
                "message.aeroflux.configurator_area_marked".equals(second.key()) && second.count() == 4
                        && ConfiguratorActions.pendingArea(tool, level) != null);
        SelfTest.check("configurator area: nothing changes before the paste is confirmed",
                at(inside[0]).redstoneMode() == RedstoneMode.ALWAYS);

        ConfiguratorActions.Result pasted = ConfiguratorActions.areaUse(tool, level, new BlockPos(5, y, 8), PLAYER, 64);
        boolean allPasted = true;
        for (BlockPos pos : inside) {
            allPasted &= at(pos).redstoneMode() == RedstoneMode.HIGH && at(pos).autoImport()
                    && labeled(at(pos), SideResource.ITEMS, FaceLabel.FRONT) == ConnectionMode.BLOCKED;
        }
        SelfTest.check("configurator area: the third use pastes onto every machine inside",
                "message.aeroflux.configurator_area_pasted".equals(pasted.key()) && pasted.count() == 4 && allPasted);
        SelfTest.check("configurator area: a machine outside the box is untouched",
                at(outside).redstoneMode() == RedstoneMode.ALWAYS && !at(outside).autoImport());
        SelfTest.check("configurator area: the corners are forgotten after the paste",
                ConfiguratorStackData.firstCorner(tool) == null && ConfiguratorActions.pendingArea(tool, level) == null);

        SelfTest.check("configurator undo: a paste can be undone", ConfiguratorActions.canUndo(level, PLAYER));
        ConfiguratorActions.Result undone = ConfiguratorActions.undo(level, PLAYER);
        boolean allBack = true;
        for (BlockPos pos : inside) {
            allBack &= at(pos).redstoneMode() == RedstoneMode.ALWAYS && !at(pos).autoImport()
                    && at(pos).sides().mode(SideResource.ITEMS, Face.UP) == ConnectionMode.IMPORT
                    && labeled(at(pos), SideResource.ITEMS, FaceLabel.FRONT) == ConnectionMode.OPEN;
        }
        SelfTest.check("configurator undo: undo puts every machine of an area paste back",
                "message.aeroflux.configurator_undone".equals(undone.key()) && undone.count() == 4 && allBack);
        ConfiguratorActions.Result again = ConfiguratorActions.undo(level, PLAYER);
        SelfTest.check("configurator undo: a paste is undone only once",
                !again.success() && "message.aeroflux.configurator_nothing_to_undo".equals(again.key()));
        SelfTest.check("configurator undo: another player's memory has nothing to undo",
                !ConfiguratorActions.undo(level, UUID.randomUUID()).success());
    }

    private static void checkAreaRefusals() {
        ItemStack tool = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
        ConfiguratorActions.copy(tool, configured(machine(MachineType.FURNACE, 2, 6, Direction.NORTH)));
        int y = Y + 2;

        // The same four machines with a limit of three.
        ConfiguratorActions.areaUse(tool, level, new BlockPos(1, y, 7), PLAYER, 3);
        ConfiguratorActions.areaUse(tool, level, new BlockPos(9, y + 1, 10), PLAYER, 3);
        ConfiguratorActions.Result limited = ConfiguratorActions.areaPaste(tool, level, PLAYER, 3);
        SelfTest.check("configurator area: more machines than the limit are refused",
                !limited.success() && "message.aeroflux.configurator_area_limit".equals(limited.key())
                        && at(new BlockPos(2, y, 8)).redstoneMode() == RedstoneMode.ALWAYS);
        SelfTest.check("configurator area: a refused box stays marked", ConfiguratorActions.pendingArea(tool, level) != null);

        ConfiguratorStackData.clearCorners(tool);
        ConfiguratorActions.areaUse(tool, level, new BlockPos(0, y, 0), PLAYER, 64);
        ConfiguratorActions.Result huge = ConfiguratorActions.areaUse(tool, level, new BlockPos(ConfigArea.MAX_SIDE + 4, y, 0), PLAYER, 64);
        SelfTest.check("configurator area: a box longer than the side limit is refused and forgotten",
                !huge.success() && "message.aeroflux.configurator_area_too_large".equals(huge.key())
                        && ConfiguratorStackData.firstCorner(tool) == null);

        ConfiguratorActions.areaUse(tool, level, new BlockPos(2, Y + 6, 2), PLAYER, 64);
        ConfiguratorActions.areaUse(tool, level, new BlockPos(4, Y + 6, 4), PLAYER, 64);
        ConfiguratorActions.Result empty = ConfiguratorActions.areaUse(tool, level, new BlockPos(3, Y + 6, 3), PLAYER, 64);
        SelfTest.check("configurator area: a box with no machine pastes nothing",
                !empty.success() && "message.aeroflux.configurator_area_empty".equals(empty.key()));

        ConfiguratorStackData.setFirstCorner(tool, new ConfiguratorStackData.Corner(
                new RunPlanner.Pos(1, y, 7), "minecraft:overworld"));
        ConfiguratorStackData.setMode(tool, ConfiguratorMode.PASTE);
        SelfTest.check("configurator area: changing the mode forgets marked corners", ConfiguratorStackData.firstCorner(tool) == null);
    }

    private static void checkPresets() {
        ItemStack tool = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
        FluxMachineBlockEntity configuredFurnace = configured(machine(MachineType.FURNACE, 2, 12, Direction.NORTH));
        FluxMachineBlockEntity plainGrinder = at(machine(MachineType.GRINDER, 4, 12, Direction.NORTH));
        ConfiguratorActions.copy(tool, configuredFurnace);
        ConfiguratorActions.Result select = ConfiguratorActions.selectPreset(tool, 2);
        ConfiguratorActions.copy(tool, plainGrinder);
        ConfiguratorPresets presets = ConfiguratorStackData.presets(tool);
        SelfTest.check("configurator presets: copies go into the selected slot and leave the others",
                select.success() && presets.selected() == 2 && "furnace".equals(presets.get(0).settings().kind())
                        && "grinder".equals(presets.get(2).settings().kind()));

        BlockPos target = machine(MachineType.FURNACE, 6, 12, Direction.NORTH);
        ConfiguratorActions.selectPreset(tool, 0);
        ConfiguratorActions.paste(tool, level, target, null);
        boolean fromSlot0 = at(target).redstoneMode() == RedstoneMode.HIGH;
        ConfiguratorActions.selectPreset(tool, 2);
        ConfiguratorActions.paste(tool, level, target, null);
        SelfTest.check("configurator presets: loading a preset pastes what it holds",
                fromSlot0 && at(target).redstoneMode() == RedstoneMode.ALWAYS);

        ConfiguratorActions.Result renamed = ConfiguratorActions.renamePreset(tool, 2, "  Ore §cline ");
        SelfTest.check("configurator presets: a preset can be renamed, and the name is cleaned",
                renamed.success() && "Ore cline".equals(ConfiguratorStackData.presets(tool).get(2).name()));
        ConfiguratorActions.copy(tool, configuredFurnace);
        SelfTest.check("configurator presets: copying into a named preset keeps its name",
                "Ore cline".equals(ConfiguratorStackData.presets(tool).get(2).name())
                        && "furnace".equals(ConfiguratorStackData.presets(tool).get(2).settings().kind()));

        SelfTest.check("configurator presets: an empty slot cannot be renamed or cleared",
                !ConfiguratorActions.renamePreset(tool, 5, "Name").success() && !ConfiguratorActions.clearPreset(tool, 5).success());
        SelfTest.check("configurator presets: a slot outside the eight is refused",
                !ConfiguratorActions.selectPreset(tool, ConfiguratorPresets.SLOTS).success()
                        && ConfiguratorStackData.presets(tool).selected() == 2);

        ConfiguratorActions.Result cleared = ConfiguratorActions.clearPreset(tool, 2);
        ConfiguratorActions.Result emptyPaste = ConfiguratorActions.paste(tool, level, target, null);
        SelfTest.check("configurator presets: a cleared preset is empty and pastes nothing",
                cleared.success() && ConfiguratorStackData.presets(tool).get(2) == null
                        && "message.aeroflux.configurator_empty".equals(emptyPaste.key())
                        && ConfiguratorStackData.presets(tool).get(0) != null);
    }

    private static void checkSingleUndo() {
        UUID player = UUID.randomUUID();
        ItemStack tool = new ItemStack(ModItems.FLUX_CONFIGURATOR.get());
        ConfiguratorActions.copy(tool, configured(machine(MachineType.FURNACE, 2, 14, Direction.NORTH)));
        BlockPos target = machine(MachineType.FURNACE, 4, 14, Direction.NORTH);
        MachineConfigActions.setRedstoneMode(at(target), RedstoneMode.PULSE);
        ConfiguratorActions.paste(tool, level, target, player);
        boolean pasted = at(target).redstoneMode() == RedstoneMode.HIGH;
        ConfiguratorActions.Result undone = ConfiguratorActions.undo(level, player);
        SelfTest.check("configurator undo: a single paste is undone back to the machine's own settings",
                pasted && undone.count() == 1 && at(target).redstoneMode() == RedstoneMode.PULSE && !at(target).autoImport()
                        && labeled(at(target), SideResource.ITEMS, FaceLabel.FRONT) == ConnectionMode.OPEN);

        // Pasting the same settings twice: the second paste changes nothing, so the undo keeps the first.
        ConfiguratorActions.paste(tool, level, target, player);
        ConfiguratorActions.paste(tool, level, target, player);
        ConfiguratorActions.undo(level, player);
        SelfTest.check("configurator undo: a paste that changed nothing does not replace the undo",
                at(target).redstoneMode() == RedstoneMode.PULSE);
    }

    // --- Helpers -----------------------------------------------------------------------------

    /** Places a copper machine in the check layer. */
    private static BlockPos machine(MachineType type, int x, int z, Direction facing) {
        return machineAt(type, x, Y, z, facing);
    }

    private static BlockPos machineAt(MachineType type, int x, int y, int z, Direction facing) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.machine(type, Tier.COPPER).get().defaultBlockState()
                .setValue(FluxMachineBlock.FACING, facing), Block.UPDATE_ALL);
        return pos;
    }

    /** Gives a machine a setup unlike the defaults: faces changed by name, high redstone, Auto Import on. */
    private static FluxMachineBlockEntity configured(BlockPos pos) {
        FluxMachineBlockEntity machine = at(pos);
        setLabeled(machine, SideResource.ITEMS, FaceLabel.BACK, ConnectionMode.IMPORT);
        setLabeled(machine, SideResource.ITEMS, FaceLabel.LEFT, ConnectionMode.EXPORT);
        setLabeled(machine, SideResource.ITEMS, FaceLabel.FRONT, ConnectionMode.BLOCKED);
        setLabeled(machine, SideResource.FLUID, FaceLabel.TOP, ConnectionMode.OFF);
        MachineConfigActions.setRedstoneMode(machine, RedstoneMode.HIGH);
        MachineConfigActions.applyButton(machine, MachineConfigActions.AUTO_IMPORT_BUTTON);
        return machine;
    }

    private static void setLabeled(FluxMachineBlockEntity machine, SideResource resource, FaceLabel label, ConnectionMode mode) {
        MachineConfigActions.apply(machine, SideCommand.set(resource, label.faceFor(machine.facing()), mode));
    }

    private static ConnectionMode labeled(FluxMachineBlockEntity machine, SideResource resource, FaceLabel label) {
        return machine.sides().mode(resource, label.faceFor(machine.facing()));
    }

    private static FluxMachineBlockEntity at(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }
}
