package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxClientConfig;
import com.itmecasey.aeroflux.Ids;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.MachineSoundSet;
import com.itmecasey.aeroflux.core.machine.MachineStatus;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.creative.CreativeFluxHandler;
import com.itmecasey.aeroflux.creative.CreativeFluxTankBlockEntity;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModCreativeTabs;
import com.itmecasey.aeroflux.registry.ModFluids;
import com.itmecasey.aeroflux.registry.ModItems;
import com.itmecasey.aeroflux.registry.ModSounds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Checks for the creative and polish content: the Creative Flux Tank (feeds a machine for as long as
 * it likes, never empties, swallows flux, switches flux and keeps the choice), the creative tab
 * (it lists every AeroFlux item), the machine sounds (every sound event is registered and has its
 * file) and the client config's sound switches, which start out on. Builds in a layer of its own at y {@value #Y}, a row away from {@link FluxScreenSelfTest}.
 */
final class PolishSelfTest {
    private static final int Y = 206;
    private static final int Z = 9;
    /** Long enough for a copper furnace to burn through a good part of its own tank. */
    private static final int FEED_CHECK = 60;
    private static final int SWITCH_CHECK = FEED_CHECK + 20;

    private static ServerLevel level;
    private static ScreenSelfTest.TestPlayer player;
    private static boolean finished;
    private static long started = -1;

    private static BlockPos creative;
    private static BlockPos furnace;
    private static BlockPos infuser;

    private PolishSelfTest() {
    }

    static boolean finished() {
        return finished;
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        finished = false;
        started = -1;
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (started < 0) {
            if (!SelfTest.blockEntitiesTick(level, new BlockPos(3, Y, Z))) {
                return;
            }
            started = level.getGameTime();
            player = new ScreenSelfTest.TestPlayer(level);
            buildCreativeTank();
            checkCreativeTab();
            checkSounds();
            checkSoundSwitches();
            return;
        }
        long elapsed = level.getGameTime() - started;
        if (elapsed == FEED_CHECK) {
            checkFeeding();
            switchToMoon();
        } else if (elapsed == SWITCH_CHECK) {
            checkMoon();
            finished = true;
        }
    }

    // --- Creative Flux Tank ------------------------------------------------------------------

    private static void buildCreativeTank() {
        creative = new BlockPos(3, Y, Z);
        furnace = creative.west();
        infuser = creative.east();
        level.setBlock(creative, ModBlocks.CREATIVE_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(furnace, ModBlocks.machine(MachineType.FURNACE, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
        // A furnace with a long job and an empty tank: everything it burns comes from the creative tank.
        machine(furnace).setStackIn(0, new ItemStack(Items.RAW_IRON, 64));

        CreativeFluxTankBlockEntity tank = tank();
        IFluidHandler.FluidAction execute = IFluidHandler.FluidAction.EXECUTE;
        int taken = tank.tank().drain(new FluidStack(ModFluids.AERO_FLUX.get(), 5_000_000), execute).getAmount();
        int swallowed = tank.tank().fill(new FluidStack(ModFluids.MOON_FLUX.get(), 7_000), execute);
        int refused = tank.tank().fill(new FluidStack(Fluids.WATER, 1_000), execute);
        FluidStack shown = tank.tank().getFluidInTank(0);
        SelfTest.check("creative tank: it starts on Aero Flux and shows as full",
                tank.flux() == FluxType.AERO && shown.getFluid().isSame(ModFluids.AERO_FLUX.get())
                        && shown.getAmount() == tank.tank().getTankCapacity(0));
        SelfTest.check("creative tank: any amount comes out and it is still full",
                taken == 5_000_000 && tank.tank().getFluidInTank(0).getAmount() == CreativeFluxHandler.SHOWN_AMOUNT);
        SelfTest.check("creative tank: it swallows flux poured in and refuses water", swallowed == 7_000 && refused == 0);
    }

    private static void checkFeeding() {
        FluxMachineBlockEntity machine = machine(furnace);
        SelfTest.check("creative tank: it keeps a furnace beside it fed and working",
                machine.status() == MachineStatus.RUNNING && machine.storedFlux() == FluxType.AERO
                        && machine.fluxAmount() >= machine.fluxCapacity() - machine.fluxPerTick());
        SelfTest.check("creative tank: it never empties", tank().tank().getFluidInTank(0).getAmount() == CreativeFluxHandler.SHOWN_AMOUNT);
    }

    private static void switchToMoon() {
        player.setPos(creative.getX() + 0.5, Y, creative.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(creative).add(0, 0.5, 0), Direction.UP, creative, false);
        level.getBlockState(creative).useWithoutItem(level, player, top);

        // An empty bucket now fills with Moon Flux, and the tank stays full.
        ItemStack bucket = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        level.getBlockState(creative).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND, top);
        SelfTest.check("creative tank: an empty hand switches it to Moon Flux and a bucket fills with it",
                tank().flux() == FluxType.MOON && player.getMainHandItem().is(ModItems.MOON_FLUX_BUCKET.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // A Moon machine on the other side, with an empty tank.
        level.setBlock(infuser, ModBlocks.machine(MachineType.INFUSER, Tier.COPPER).get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void checkMoon() {
        SelfTest.check("creative tank: after the switch it fills a Moon machine",
                machine(infuser).storedFlux() == FluxType.MOON && machine(infuser).fluxAmount() == machine(infuser).fluxCapacity());
        SelfTest.check("creative tank: the furnace beside it gets no Moon Flux it cannot use",
                machine(furnace).storedFlux() != FluxType.MOON);

        CompoundTag saved = tank().saveWithFullMetadata(level.registryAccess());
        CreativeFluxTankBlockEntity restored = new CreativeFluxTankBlockEntity(creative, level.getBlockState(creative));
        restored.loadWithComponents(saved, level.registryAccess());
        SelfTest.check("creative tank: the chosen flux survives saving and loading", restored.flux() == FluxType.MOON);
        SelfTest.check("creative tank: broken, it drops itself",
                Block.getDrops(level.getBlockState(creative), level, creative, tank()).stream()
                        .anyMatch(stack -> stack.is(ModBlocks.CREATIVE_TANK.get().asItem())));
    }

    // --- Creative tab ------------------------------------------------------------------------

    private static void checkCreativeTab() {
        CreativeModeTab tab = ModCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
        Set<Item> listed = new HashSet<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            listed.add(stack.getItem());
        }
        List<String> missing = new ArrayList<>();
        BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(AeroFlux.MODID))
                .filter(item -> !listed.contains(item))
                .forEach(item -> missing.add(BuiltInRegistries.ITEM.getKey(item).getPath()));
        if (!missing.isEmpty()) {
            AeroFlux.LOGGER.info("AEROFLUX-SELFTEST INFO missing from the creative tab: {}", missing);
        }
        SelfTest.check("creative tab: it lists every AeroFlux item", missing.isEmpty());
        SelfTest.check("creative tab: the Creative Flux Tank and the upgrades are in it",
                listed.contains(ModBlocks.CREATIVE_TANK.get().asItem())
                        && listed.contains(ModItems.SPEED_UPGRADE.get()) && listed.contains(ModItems.upgradeCard(Tier.GOLD).get()));
    }

    // --- Sounds ------------------------------------------------------------------------------

    private static void checkSounds() {
        List<String> events = new ArrayList<>(List.of("machine.start", "machine.stop", "machine.done"));
        for (String loop : MachineSoundSet.LOOPS) {
            events.add("machine." + loop + ".loop");
        }
        boolean registered = events.stream().allMatch(name -> BuiltInRegistries.SOUND_EVENT.containsKey(Ids.mod(name)));
        SelfTest.check("sounds: all eleven machine sound events are registered", registered && events.size() == 11);

        boolean everyMachine = true;
        for (MachineType machine : MachineType.values()) {
            everyMachine &= (ModSounds.loopOf(machine) == null) == (MachineSoundSet.loopOf(machine) == null);
        }
        SelfTest.check("sounds: every machine but the Magnet has a running loop", everyMachine
                && ModSounds.loopOf(MachineType.MAGNET) == null && ModSounds.loopOf(MachineType.SIFTER) != null);

        String sounds = resource("/assets/aeroflux/sounds.json");
        boolean listedAndPresent = sounds != null;
        for (String name : events) {
            listedAndPresent &= sounds != null && sounds.contains("\"" + name + "\"");
        }
        String[] files = {"start", "stop", "done", "furnace_loop", "grinder_loop", "smelter_loop", "sawmill_loop",
                "infuser_loop", "enchanter_loop", "disenchanter_loop", "grower_loop"};
        for (String file : files) {
            listedAndPresent &= PolishSelfTest.class.getResource("/assets/aeroflux/sounds/machine/" + file + ".ogg") != null;
        }
        SelfTest.check("sounds: sounds.json lists every event and every sound file is packed", listedAndPresent);
    }

    /**
     * The client config's sound switches start out on. A server never loads the client file, so
     * this reads the defaults the spec declares, and what the getters answer without a file.
     */
    private static void checkSoundSwitches() {
        boolean declared = defaultOf("sounds.enabled").equals(true) && defaultOf("sounds.machineEffects").equals(true)
                && defaultOf("sounds.machineLoopVolume").equals(0.5) && defaultOf("particles").equals(true);
        boolean answered = AeroFluxClientConfig.sounds() && AeroFluxClientConfig.machineEffects()
                && AeroFluxClientConfig.machineLoopVolume() == 0.5F && AeroFluxClientConfig.particles();
        SelfTest.check("sounds: the client config starts with every sound and the motes on", declared && answered);
    }

    private static Object defaultOf(String path) {
        Object value = AeroFluxClientConfig.SPEC.getValues().get(path);
        return value instanceof ForgeConfigSpec.ConfigValue<?> config ? config.getDefault() : "missing";
    }

    private static String resource(String path) {
        try (InputStream in = PolishSelfTest.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static CreativeFluxTankBlockEntity tank() {
        return (CreativeFluxTankBlockEntity) level.getBlockEntity(creative);
    }

    private static FluxMachineBlockEntity machine(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }
}
