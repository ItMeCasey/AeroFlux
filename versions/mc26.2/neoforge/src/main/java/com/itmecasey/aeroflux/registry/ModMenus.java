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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The menu types AeroFlux adds. */
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AeroFlux.MODID);

    /** The pipe screen. Its client copy is built from the data {@link PipeConfigMenu#open} sends. */
    public static final DeferredHolder<MenuType<?>, MenuType<PipeConfigMenu>> PIPE_CONFIG =
            MENUS.register("pipe_config", () -> IMenuTypeExtension.create(PipeConfigMenu::fromNetwork));

    /** The chunk loader screen, shared by all six tiers. */
    public static final DeferredHolder<MenuType<?>, MenuType<ChunkLoaderMenu>> CHUNK_LOADER =
            MENUS.register("chunk_loader", () -> IMenuTypeExtension.create(ChunkLoaderMenu::fromNetwork));

    /** The converter screen, shared by every kind and tier. */
    public static final DeferredHolder<MenuType<?>, MenuType<ConverterMenu>> CONVERTER =
            MENUS.register("converter", () -> IMenuTypeExtension.create(ConverterMenu::fromNetwork));

    /** The collector screen with its sky panel, shared by all six tiers. */
    public static final DeferredHolder<MenuType<?>, MenuType<CollectorMenu>> COLLECTOR =
            MENUS.register("collector", () -> IMenuTypeExtension.create(CollectorMenu::fromNetwork));

    /** The tank screen, shared by all six tiers. */
    public static final DeferredHolder<MenuType<?>, MenuType<TankMenu>> TANK =
            MENUS.register("tank", () -> IMenuTypeExtension.create(TankMenu::fromNetwork));

    /** One menu type per machine, all built by {@link MachineMenu}; each has its own screen registration. */
    private static final Map<MachineType, DeferredHolder<MenuType<?>, MenuType<MachineMenu>>> MACHINES =
            new EnumMap<>(MachineType.class);

    static {
        for (MachineType machine : MachineType.values()) {
            MACHINES.put(machine, MENUS.register(machine.id(), () -> IMenuTypeExtension.create(MachineMenu::fromNetwork)));
        }
    }

    private ModMenus() {
    }

    /** The menu type of a machine. */
    public static DeferredHolder<MenuType<?>, MenuType<MachineMenu>> machine(MachineType machine) {
        return MACHINES.get(machine);
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
