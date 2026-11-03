package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.chunkloader.ChunkLoaderRegistry;
import com.itmecasey.aeroflux.conduit.ConduitBundleBlockEntity;
import com.itmecasey.aeroflux.conduit.tool.ConduitToolActions;
import com.itmecasey.aeroflux.conduit.tool.ToolStackData;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.core.profile.ProfileBudget;
import com.itmecasey.aeroflux.core.profile.ProfileReport;
import com.itmecasey.aeroflux.core.profile.ProfileTypes;
import com.itmecasey.aeroflux.core.protect.ProtectionAction;
import com.itmecasey.aeroflux.core.protect.ProtectionPolicy;
import com.itmecasey.aeroflux.core.protect.Verdict;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.lamp.LampKind;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.machine.MachineConfigActions;
import com.itmecasey.aeroflux.machine.MachineFactory;
import com.itmecasey.aeroflux.profile.ProfileReporter;
import com.itmecasey.aeroflux.profile.TickProfiler;
import com.itmecasey.aeroflux.protect.Protection;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Checks for the profiler, the save versioning and the protection layer, in a chunk of their own
 * (x and z from {@value #OX}; y {@value #PROFILE_Y} for the profiler and the saves, y {@value #GUARD_Y}
 * for the protection checks, with a stone floor under it, and one row of bundles at y {@value #TOOL_Y}).
 *
 * <p>The protection checks use a provider that denies every position with x of 8 or more, standing
 * in for a claim mod, and listeners that veto the standard events for one owner. They never touch the
 * game clock or weather: a Sky Controller is only shown refusing.
 */
final class GuardSelfTest {
    private static final int PROFILE_Y = 200;
    private static final int GUARD_Y = 210;
    private static final int TOOL_Y = 220;
    /** The corner of the chunk these checks use; it is forced loaded in {@link #setUp}, apart from the other checks' chunk. */
    private static final int OX = 64;
    private static final int OZ = 64;
    private static final int WINDOW = 40;
    private static final int CHECK_AT = 90;
    private static final int FLUX = 5_000;
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID EVENT_OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000000c3");

    private static ServerLevel level;
    private static boolean finished;
    private static long started = -1;
    private static ProfileReport report;

    private static BlockPos skyDenied;
    private static BlockPos magnetOk;
    private static BlockPos magnetDenied;
    private static BlockPos magnetUnowned;
    private static BlockPos growerOk;
    private static BlockPos growerDenied;
    private static BlockPos growerUnowned;
    private static ItemEntity itemOk;
    private static ItemEntity itemDenied;
    private static ItemEntity itemUnowned;

    private GuardSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        finished = false;
        started = -1;
        report = null;
        // Only forced chunks tick without a player nearby.
        serverLevel.getServer().getCommands().performPrefixedCommand(serverLevel.getServer().createCommandSourceStack(),
                "forceload add " + OX + " " + OZ);
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (started < 0) {
            if (!SelfTest.blockEntitiesTick(level, new BlockPos(OX + 3, PROFILE_Y, OZ + 1))) {
                return;
            }
            started = level.getGameTime();
            build();
            return;
        }
        if (level.getGameTime() - started == CHECK_AT) {
            checkProfile();
            checkMachines();
            Protection.setEnabledOverride(null);
            finished = true;
        }
    }

    private static void build() {
        buildProfileBlocks();
        checkIdleProfiler();
        checkSaves();
        checkRegistrySave();
        buildProtection();
        checkPolicyAndEvents();
        checkTools();
    }

    // --- Profiler ----------------------------------------------------------------------------

    private static void buildProfileBlocks() {
        place(1, MachineType.FURNACE);
        place(3, MachineType.GRINDER);
        set(5, ModBlocks.collector(Tier.COPPER).get().defaultBlockState());
        set(7, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState());
        set(9, ModBlocks.chunkLoader(Tier.COPPER).get().defaultBlockState());
        set(11, ModBlocks.converter(ConverterType.ENERGY, Tier.COPPER).get().defaultBlockState());
        set(13, ModBlocks.lamp(LampKind.SUN, Tier.COPPER).get().defaultBlockState());
        set(15, ModBlocks.CREATIVE_TANK.get().defaultBlockState());
        set(1, 3, ModBlocks.tank(Tier.COPPER).get().defaultBlockState());
    }

    private static void checkIdleProfiler() {
        SelfTest.check("profiler: with nobody sampling nothing is kept",
                !TickProfiler.sampling() && TickProfiler.retainedBlocks() == 0);

        // The command starts a window, refuses a second one, and cancel ends it.
        command("aeroflux profile 1");
        SelfTest.check("profiler: the command starts a window", TickProfiler.sampling());
        SelfTest.check("profiler: a second window is refused", !TickProfiler.start(5, ignored -> { }));
        command("aeroflux profile cancel");
        SelfTest.check("profiler: cancel ends the window and keeps nothing",
                !TickProfiler.sampling() && TickProfiler.retainedBlocks() == 0);

        SelfTest.check("profiler: a window can be started from code", TickProfiler.start(WINDOW, done -> report = done));
        SelfTest.check("profiler: the window is running", TickProfiler.sampling());
    }

    private static void checkProfile() {
        SelfTest.check("profiler: the window ended and kept nothing", !TickProfiler.sampling() && TickProfiler.retainedBlocks() == 0);
        SelfTest.check("profiler: the report arrived", report != null);
        if (report == null) {
            return;
        }
        long summed = report.types().stream().mapToLong(ProfileReport.Line::nanos).sum();
        double perTick = report.total().msPerTick(report.windowTicks());
        AeroFlux.LOGGER.info("AEROFLUX-SELFTEST INFO profile window={} blocks={} ms/tick={} budget={}",
                report.windowTicks(), report.total().blocks(), perTick, ProfileBudget.allowedMs(report));
        SelfTest.check("profiler: the window is the length asked for", report.windowTicks() == WINDOW);
        SelfTest.check("profiler: the kinds add up to the total", summed == report.total().nanos() && report.total().calls() > 0);
        SelfTest.check("profiler: a sensible time per tick (above zero, under 100 ms)", perTick > 0.0 && perTick < 100.0);
        SelfTest.check("profiler: every kind built here is counted", hasType(ProfileTypes.machine(MachineType.FURNACE.id()))
                && hasType(ProfileTypes.machine(MachineType.GRINDER.id())) && hasType(ProfileTypes.COLLECTOR)
                && hasType(ProfileTypes.CONDUIT_BUNDLE) && hasType(ProfileTypes.CHUNK_LOADER) && hasType(ProfileTypes.CONVERTER)
                && hasType(ProfileTypes.LAMP) && hasType(ProfileTypes.CREATIVE_TANK));
        SelfTest.check("profiler: at least those eight blocks are loaded", report.total().blocks() >= 8);
        SelfTest.check("profiler: the machine group counts both machines",
                report.groups().stream().anyMatch(line -> line.type().equals("machine") && line.blocks() >= 2));
        boolean ordered = true;
        for (int i = 1; i < report.heaviest().size(); i++) {
            ordered &= report.heaviest().get(i - 1).nanos() >= report.heaviest().get(i).nanos();
        }
        SelfTest.check("profiler: the heaviest blocks are listed in order, with positions",
                !report.heaviest().isEmpty() && report.heaviest().size() <= ProfileReporter.HEAVIEST_LISTED && ordered
                        && report.heaviest().get(0).dimension().equals("minecraft:overworld"));
        SelfTest.check("profiler: the budget is worked out for what is loaded", ProfileBudget.allowedMs(report) >= ProfileBudget.FLOOR_MS);
        List<Component> lines = ProfileReporter.lines(report);
        SelfTest.check("profiler: the report prints a header, a budget line and the kinds",
                keyOf(lines.get(0)).equals("commands.aeroflux.profile.header")
                        && keyOf(lines.get(1)).equals("commands.aeroflux.profile.budget") && lines.size() > 5);
    }

    private static boolean hasType(String type) {
        return report.types().stream().anyMatch(line -> line.type().equals(type) && line.blocks() >= 1);
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    // --- Save versioning ---------------------------------------------------------------------

    private static void checkSaves() {
        FluxMachineBlockEntityAt furnace = new FluxMachineBlockEntityAt(1);
        furnace.machine.setStackIn(0, new ItemStack(Items.RAW_IRON, 3));
        furnace.machine.setFlux(new FluidStack(ModFluids.AERO_FLUX.get(), 1_234));
        furnace.machine.setOwner(OWNER);
        BlockEntity tank = level.getBlockEntity(pos(1, 3));
        ((com.itmecasey.aeroflux.block.entity.FluxTankBlockEntity) tank).tank()
                .fill(new FluidStack(ModFluids.MOON_FLUX.get(), 777), IFluidHandler.FluidAction.EXECUTE);

        roundTrip("machine", pos(1), SaveSchema.Kind.MACHINE);
        roundTrip("machine (second kind)", pos(3), SaveSchema.Kind.MACHINE);
        roundTrip("collector", pos(5), SaveSchema.Kind.COLLECTOR);
        roundTrip("conduit bundle", pos(7), SaveSchema.Kind.CONDUIT_BUNDLE);
        roundTrip("chunk loader", pos(9), SaveSchema.Kind.CHUNK_LOADER);
        roundTrip("converter", pos(11), SaveSchema.Kind.CONVERTER);
        roundTrip("lamp", pos(13), SaveSchema.Kind.LAMP);
        roundTrip("creative tank", pos(15), SaveSchema.Kind.CREATIVE_TANK);
        roundTrip("tank", pos(1, 3), SaveSchema.Kind.TANK);

        // The values themselves, not only the shape, survive a round trip.
        CompoundTag saved = furnace.machine.saveWithFullMetadata(level.registryAccess());
        FluxMachineBlockEntity copy = load(furnace.state, pos(1), saved);
        SelfTest.check("save: a machine's items, Flux and owner round-trip", ItemStack.matches(copy.stackIn(0), furnace.machine.stackIn(0))
                && copy.fluxAmount() == 1_234 && copy.owner().equals(java.util.Optional.of(OWNER)));
        SelfTest.check("save: a machine records its owner in the data", saved.contains("owner"));

        // Data from before versioning, built by hand.
        CompoundTag unversioned = saved.copy();
        unversioned.remove(SaveSchema.TAG);
        unversioned.remove("owner");
        FluxMachineBlockEntity old = load(furnace.state, pos(1), unversioned);
        SelfTest.check("save: a machine saved before versioning (and before owners) loads with its contents",
                ItemStack.matches(old.stackIn(0), furnace.machine.stackIn(0)) && old.fluxAmount() == 1_234 && old.owner().isEmpty());
        SelfTest.check("save: and is saved again with the current version",
                old.saveWithFullMetadata(level.registryAccess()).getInt(SaveSchema.TAG) == SaveSchema.Kind.MACHINE.current());

        // A version-2 machine (the layout before owners) loads and gets the version-3 stamp.
        CompoundTag two = saved.copy();
        two.putInt(SaveSchema.TAG, 2);
        two.remove("owner");
        SelfTest.check("save: a version 2 machine loads and migrates",
                load(furnace.state, pos(1), two).stackIn(0).getCount() == 3);

        // A save from a newer build is read as well as it can be.
        CompoundTag future = saved.copy();
        future.putInt(SaveSchema.TAG, SaveSchema.Kind.MACHINE.current() + 5);
        SelfTest.check("save: a save from a newer build still loads what it can",
                load(furnace.state, pos(1), future).fluxAmount() == 1_234);

        checkSifterLayouts();
    }

    /** The Sifter gained a mesh slot in version 2; a version 1 save has one input slot too few. */
    private static void checkSifterLayouts() {
        BlockState state = ModBlocks.machine(MachineType.SIFTER, Tier.COPPER).get().defaultBlockState();
        FluxMachineBlockEntity sifter = MachineFactory.create(pos(1, 6), state);
        int firstOutput = MachineType.SIFTER.inputSlots();
        int lastOutput = firstOutput + MachineType.SIFTER.outputSlots() - 1;
        sifter.setStackIn(0, new ItemStack(Items.GRAVEL, 5));
        sifter.setStackIn(firstOutput, new ItemStack(Items.FLINT, 3));
        sifter.setStackIn(lastOutput, new ItemStack(Items.STICK, 2));
        CompoundTag current = sifter.saveWithFullMetadata(level.registryAccess());

        SelfTest.check("save: a current Sifter round-trips its slots", sameSlots(load(state, pos(1, 6), current), firstOutput, lastOutput));

        // The old layout: the same data without the mesh slot, as version 1 (and unversioned data) wrote
        // it. Forge's item handler saves a list of filled slots, so every slot after the mesh slot moves down one.
        for (int version : new int[] {1, 0}) {
            CompoundTag legacy = current.copy();
            ListTag entries = legacy.getCompound("items").getList("Items", Tag.TAG_COMPOUND);
            boolean filled = entries.size() == 3;
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                if (entry.getInt("Slot") > 1) {
                    entry.putInt("Slot", entry.getInt("Slot") - 1);
                }
            }
            if (version == 0) {
                legacy.remove(SaveSchema.TAG);
            } else {
                legacy.putInt(SaveSchema.TAG, version);
            }
            FluxMachineBlockEntity migrated = load(state, pos(1, 6), legacy);
            SelfTest.check("save: a Sifter saved as version " + version + " gets its mesh slot and keeps its items in place",
                    filled && sameSlots(migrated, firstOutput, lastOutput) && migrated.stackIn(1).isEmpty());
        }
    }

    private static boolean sameSlots(FluxMachineBlockEntity machine, int firstOutput, int lastOutput) {
        return machine.stackIn(0).is(Items.GRAVEL) && machine.stackIn(0).getCount() == 5
                && machine.stackIn(firstOutput).is(Items.FLINT) && machine.stackIn(firstOutput).getCount() == 3
                && machine.stackIn(lastOutput).is(Items.STICK) && machine.stackIn(lastOutput).getCount() == 2;
    }

    /** Saves a block entity, strips the version as old data lacks it, loads it into a new one and compares. */
    private static void roundTrip(String name, BlockPos pos, SaveSchema.Kind kind) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            SelfTest.check("save: " + name + " has a block entity", false);
            return;
        }
        CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
        SelfTest.check("save: " + name + " writes its version", tag.getInt(SaveSchema.TAG) == kind.current());

        BlockEntity fresh = entity.getType().create(pos, level.getBlockState(pos));
        fresh.loadWithComponents(tag, level.registryAccess());
        SelfTest.check("save: " + name + " data round-trips", fresh.saveWithFullMetadata(level.registryAccess()).equals(tag));

        CompoundTag old = tag.copy();
        old.remove(SaveSchema.TAG);
        BlockEntity migrated = entity.getType().create(pos, level.getBlockState(pos));
        migrated.loadWithComponents(old, level.registryAccess());
        SelfTest.check("save: " + name + " data from before versioning loads and is saved with the version",
                migrated.saveWithFullMetadata(level.registryAccess()).equals(tag));
    }

    private static FluxMachineBlockEntity load(BlockState state, BlockPos pos, CompoundTag tag) {
        FluxMachineBlockEntity machine = MachineFactory.create(pos, state);
        machine.loadWithComponents(tag, level.registryAccess());
        return machine;
    }

    /** The per-dimension chunk loader list: a registry saved before versioning loads, and a new one keeps its version. */
    private static void checkRegistrySave() {
        // The registry class starts first, as it does in the game; its entry codec is built from its own constants.
        ChunkLoaderRegistry.codec();
        BlockPos at = new BlockPos(2, 100, 2);
        ChunkLoaderRegistry.Entry entry = new ChunkLoaderRegistry.Entry(at, java.util.Optional.of(OWNER), Tier.GOLD, false);
        ListTag list = new ListTag();
        list.add(ChunkLoaderRegistry.Entry.CODEC.encodeStart(NbtOps.INSTANCE, entry).getOrThrow());
        CompoundTag legacy = new CompoundTag();
        legacy.put("loaders", list);

        DataResult<ChunkLoaderRegistry> parsed = ChunkLoaderRegistry.codec().parse(NbtOps.INSTANCE, legacy);
        ChunkLoaderRegistry registry = parsed.result().orElse(null);
        SelfTest.check("save: a loader list saved before versioning loads", registry != null
                && registry.get(at).map(entry::equals).orElse(false) && registry.loadedVersion() == 0);
        if (registry == null) {
            return;
        }
        Tag written = ChunkLoaderRegistry.codec().encodeStart(NbtOps.INSTANCE, registry).getOrThrow();
        SelfTest.check("save: a loader list is written with the current version", written instanceof CompoundTag tag
                && tag.getInt(SaveSchema.TAG) == SaveSchema.Kind.CHUNK_LOADER_REGISTRY.current());
        ChunkLoaderRegistry again = ChunkLoaderRegistry.codec().parse(NbtOps.INSTANCE, written).result().orElse(null);
        SelfTest.check("save: a current loader list round-trips", again != null && again.get(at).map(entry::equals).orElse(false)
                && again.loadedVersion() == SaveSchema.Kind.CHUNK_LOADER_REGISTRY.current());
    }

    // --- Protection --------------------------------------------------------------------------

    private static void buildProtection() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                level.setBlock(guardPos(x, z).below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        // The stand-in for a claim mod: nothing may happen at x of 8 or more.
        Protection.addProvider(request -> request.pos().getX() - OX >= 8 ? Verdict.DENY : Verdict.ABSTAIN);

        skyDenied = guarded(MachineType.SKY_CONTROLLER, 10, 3, OWNER, FluxType.AERO);
        magnetOk = guarded(MachineType.MAGNET, 3, 2, OWNER, FluxType.MOON);
        magnetDenied = guarded(MachineType.MAGNET, 10, 8, OWNER, FluxType.MOON);
        magnetUnowned = guarded(MachineType.MAGNET, 10, 14, null, FluxType.MOON);
        growerOk = guarded(MachineType.PLANT_GROWER, 3, 11, OWNER, FluxType.AERO);
        growerDenied = guarded(MachineType.PLANT_GROWER, 12, 11, OWNER, FluxType.AERO);
        growerUnowned = guarded(MachineType.PLANT_GROWER, 14, 11, null, FluxType.AERO);
        for (BlockPos grower : new BlockPos[] {growerOk, growerDenied, growerUnowned}) {
            machineAt(grower).setStackIn(0, new ItemStack(Items.WHEAT_SEEDS, 8));
        }
        itemOk = drop(magnetOk);
        itemDenied = drop(magnetDenied);
        itemUnowned = drop(magnetUnowned);
    }

    private static BlockPos guarded(MachineType type, int x, int z, UUID owner, FluxType flux) {
        BlockPos pos = guardPos(x, z);
        level.setBlock(pos, ModBlocks.machine(type, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = machineAt(pos);
        machine.setOwner(owner);
        machine.setFlux(new FluidStack(flux == FluxType.AERO ? ModFluids.AERO_FLUX.get() : ModFluids.MOON_FLUX.get(), FLUX));
        return pos;
    }

    private static ItemEntity drop(BlockPos magnet) {
        ItemEntity item = new ItemEntity(level, magnet.getX() + 0.5, GUARD_Y + 1.0, magnet.getZ() + 1.5, new ItemStack(Items.COBBLESTONE, 4));
        item.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(item);
        return item;
    }

    private static void checkPolicyAndEvents() {
        BlockPos denied = guardPos(10, 3);
        BlockPos allowed = guardPos(3, 3);
        SelfTest.check("protect: a provider that denies a region stops an owned machine there",
                !Protection.mayAct(level, OWNER, denied, ProtectionAction.USE_BLOCK));
        SelfTest.check("protect: outside the region it is allowed", Protection.mayAct(level, OWNER, allowed, ProtectionAction.USE_BLOCK));
        SelfTest.check("protect: a machine with no owner is never asked",
                Protection.mayAct(level, null, denied, ProtectionAction.USE_BLOCK));
        Protection.setEnabledOverride(false);
        SelfTest.check("protect: with the switch off nothing is asked", Protection.mayAct(level, OWNER, denied, ProtectionAction.USE_BLOCK));
        Protection.setEnabledOverride(null);
        SelfTest.check("protect: the policy denies when any source denies",
                !ProtectionPolicy.machineMayAct(true, true, List.of(Verdict.ALLOW, Verdict.DENY)));

        // The standard events, vetoed the way a claim mod does for one player in one corner.
        BlockPos corner = guardPos(1, 3);
        Consumer<PlayerInteractEvent.RightClickBlock> use = event -> {
            if (event.getEntity().getUUID().equals(EVENT_OWNER) && event.getPos().getX() - OX <= 1) {
                event.setCanceled(true);
            }
        };
        Consumer<BlockEvent.BreakEvent> breaking = event -> {
            if (event.getPlayer().getUUID().equals(EVENT_OWNER) && event.getPos().getX() - OX <= 1) {
                event.setCanceled(true);
            }
        };
        Consumer<BlockEvent.EntityPlaceEvent> placing = event -> {
            if (event.getEntity() != null && event.getEntity().getUUID().equals(EVENT_OWNER) && event.getPos().getX() - OX <= 1) {
                event.setCanceled(true);
            }
        };
        Consumer<PlayerInteractEvent.EntityInteract> touching = event -> {
            if (event.getEntity().getUUID().equals(EVENT_OWNER) && event.getTarget().blockPosition().getX() - OX <= 1) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(use);
        MinecraftForge.EVENT_BUS.addListener(breaking);
        MinecraftForge.EVENT_BUS.addListener(placing);
        MinecraftForge.EVENT_BUS.addListener(touching);
        try {
            SelfTest.check("protect: a vetoed use event stops the owner's machine",
                    !Protection.mayAct(level, EVENT_OWNER, corner, ProtectionAction.USE_BLOCK));
            SelfTest.check("protect: a vetoed break event stops it",
                    !Protection.mayAct(level, EVENT_OWNER, corner, ProtectionAction.BREAK_BLOCK));
            SelfTest.check("protect: a vetoed place event stops it",
                    !Protection.mayAct(level, EVENT_OWNER, corner, ProtectionAction.PLACE_BLOCK));
            ItemEntity target = new ItemEntity(level, OX + 1.5, GUARD_Y + 1.0, OZ + 3.5, new ItemStack(Items.DIRT));
            SelfTest.check("protect: a vetoed entity event stops it", !Protection.mayTouch(level, EVENT_OWNER, target));
            SelfTest.check("protect: the veto is for that owner only (another owner is allowed)",
                    Protection.mayAct(level, OTHER, corner, ProtectionAction.USE_BLOCK)
                            && Protection.mayAct(level, OTHER, corner, ProtectionAction.BREAK_BLOCK)
                            && Protection.mayTouch(level, OTHER, target));
            SelfTest.check("protect: and only in that corner",
                    Protection.mayAct(level, EVENT_OWNER, guardPos(5, 3), ProtectionAction.USE_BLOCK));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(use);
            MinecraftForge.EVENT_BUS.unregister(breaking);
            MinecraftForge.EVENT_BUS.unregister(placing);
            MinecraftForge.EVENT_BUS.unregister(touching);
        }
        SelfTest.check("protect: with the listeners gone the owner is allowed again",
                Protection.mayAct(level, EVENT_OWNER, corner, ProtectionAction.USE_BLOCK));
    }

    private static void checkTools() {
        // A run of three bundles; the guard refuses the middle one, so the tool removes the ends only.
        List<BlockPos> row = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BlockPos at = new BlockPos(OX + 2 + i, TOOL_Y, OZ + 3);
            level.setBlock(at, ModBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), Block.UPDATE_ALL);
            ((ConduitBundleBlockEntity) level.getBlockEntity(at)).addPipe(PipeType.FLUID);
            row.add(at);
        }
        ItemStack tool = new ItemStack(ModItems.CONDUIT_TOOL.get());
        ToolStackData.setMode(tool, ToolMode.REMOVE_RUN);
        int[] asked = {0};
        ConduitToolActions.removeRun(level, tool, row.get(0), PipeType.FLUID, at -> true);
        ConduitToolActions.removeRun(level, tool, row.get(2), PipeType.FLUID, at -> {
            asked[0]++;
            return !at.equals(row.get(1));
        });
        SelfTest.check("protect: the Conduit Tool's run asks about every spot it reaches", asked[0] == 3);
        SelfTest.check("protect: the Conduit Tool leaves a refused bundle alone and removes the rest",
                level.getBlockEntity(row.get(1)) instanceof ConduitBundleBlockEntity middle
                        && middle.bundle().set().contains(PipeType.FLUID)
                        && !(level.getBlockEntity(row.get(0)) instanceof ConduitBundleBlockEntity)
                        && !(level.getBlockEntity(row.get(2)) instanceof ConduitBundleBlockEntity));
    }

    private static void checkMachines() {
        FluxMachineBlockEntity sky = machineAt(skyDenied);
        SelfTest.check("protect: a denied Sky Controller does not act and keeps its Flux",
                !sky.machineButton(MachineConfigActions.SKY_WEATHER_BUTTON) && sky.fluxAmount() == FLUX);

        SelfTest.check("protect: a magnet the claims allow pulls its item", itemOk.isRemoved() && outputCount(magnetOk) > 0);
        SelfTest.check("protect: a denied magnet leaves the item where it is",
                !itemDenied.isRemoved() && outputCount(magnetDenied) == 0 && machineAt(magnetDenied).fluxAmount() == FLUX);
        SelfTest.check("protect: an unowned magnet in the same region acts as before",
                itemUnowned.isRemoved() && outputCount(magnetUnowned) > 0);

        SelfTest.check("protect: a Plant Grower the claims allow grows", machineAt(growerOk).progress() > 0 || machineAt(growerOk).fluxAmount() < FLUX);
        FluxMachineBlockEntity denied = machineAt(growerDenied);
        SelfTest.check("protect: a denied Plant Grower does not grow and keeps its Flux",
                denied.progress() == 0 && denied.fluxAmount() == FLUX && denied.stackIn(0).getCount() == 8);
        SelfTest.check("protect: an unowned Plant Grower in the region acts as before", machineAt(growerUnowned).fluxAmount() < FLUX);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static int outputCount(BlockPos pos) {
        FluxMachineBlockEntity machine = machineAt(pos);
        int count = 0;
        for (int slot = MachineType.MAGNET.inputSlots(); slot < MachineType.MAGNET.inputSlots() + MachineType.MAGNET.outputSlots(); slot++) {
            count += machine.stackIn(slot).getCount();
        }
        return count;
    }

    private static FluxMachineBlockEntity machineAt(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static BlockPos pos(int x) {
        return pos(x, 1);
    }

    private static BlockPos pos(int x, int z) {
        return new BlockPos(OX + x, PROFILE_Y, OZ + z);
    }

    private static BlockPos guardPos(int x, int z) {
        return new BlockPos(OX + x, GUARD_Y, OZ + z);
    }

    private static void place(int x, MachineType type) {
        set(x, 1, ModBlocks.machine(type, Tier.COPPER).get().defaultBlockState());
    }

    private static void set(int x, BlockState state) {
        set(x, 1, state);
    }

    private static void set(int x, int z, BlockState state) {
        level.setBlock(pos(x, z), state, Block.UPDATE_ALL);
    }

    private static void command(String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
    }

    /** A placed machine and its state, for the save checks. */
    private static final class FluxMachineBlockEntityAt {
        final FluxMachineBlockEntity machine;
        final BlockState state;

        FluxMachineBlockEntityAt(int x) {
            this.machine = machineAt(pos(x));
            this.state = level.getBlockState(pos(x));
        }
    }
}
