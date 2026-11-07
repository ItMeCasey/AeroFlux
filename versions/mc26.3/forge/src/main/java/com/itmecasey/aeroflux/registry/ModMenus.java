package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.block.menu.CollectorMenu;
import com.itmecasey.aeroflux.block.menu.TankMenu;
import com.itmecasey.aeroflux.chunkloader.ChunkLoaderMenu;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import com.itmecasey.aeroflux.converter.ConverterMenu;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.MachineMenu;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The menu types AeroFlux adds. */
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, AeroFlux.MODID);

    /** The pipe screen. Its client copy is built from the data {@link PipeConfigMenu#open} sends. */
    public static final RegistryObject<MenuType<PipeConfigMenu>> PIPE_CONFIG =
            MENUS.register("pipe_config", () -> IForgeMenuType.create(PipeConfigMenu::fromNetwork));

    /** The chunk loader screen, shared by all six tiers. */
    public static final RegistryObject<MenuType<ChunkLoaderMenu>> CHUNK_LOADER =
            MENUS.register("chunk_loader", () -> IForgeMenuType.create(ChunkLoaderMenu::fromNetwork));

    /** The converter screen, shared by every kind and tier. */
    public static final RegistryObject<MenuType<ConverterMenu>> CONVERTER =
            MENUS.register("converter", () -> IForgeMenuType.create(ConverterMenu::fromNetwork));

    /** The collector screen with its sky panel, shared by all six tiers. */
    public static final RegistryObject<MenuType<CollectorMenu>> COLLECTOR =
            MENUS.register("collector", () -> IForgeMenuType.create(CollectorMenu::fromNetwork));

    /** The tank screen, shared by all six tiers. */
    public static final RegistryObject<MenuType<TankMenu>> TANK =
            MENUS.register("tank", () -> IForgeMenuType.create(TankMenu::fromNetwork));

    /** One menu type per machine, all built by {@link MachineMenu}; each has its own screen registration. */
    private static final Map<MachineType, RegistryObject<MenuType<MachineMenu>>> MACHINES =
            new EnumMap<>(MachineType.class);

    static {
        for (MachineType machine : MachineType.values()) {
            MACHINES.put(machine, MENUS.register(machine.id(), () -> IForgeMenuType.create(MachineMenu::fromNetwork)));
        }
    }

    private ModMenus() {
    }

    /** The menu type of a machine. */
    public static RegistryObject<MenuType<MachineMenu>> machine(MachineType machine) {
        return MACHINES.get(machine);
    }

    /** Hooks this registry into the mod event bus group; call once from the mod constructor. */
    public static void register(BusGroup modBusGroup) {
        MENUS.register(modBusGroup);
    }
}
