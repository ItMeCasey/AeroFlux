package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
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

    private ModMenus() {
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
