package com.itmecasey.aeroflux.registry;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.conduit.config.PipeConfigMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The menu types AeroFlux adds. */
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AeroFlux.MODID);

    /** The pipe screen. Its client copy is built from the data {@link PipeConfigMenu#open} sends. */
    public static final RegistryObject<MenuType<PipeConfigMenu>> PIPE_CONFIG =
            MENUS.register("pipe_config", () -> IForgeMenuType.create(PipeConfigMenu::fromNetwork));

    private ModMenus() {
    }

    /** Hooks this registry into the mod event bus; call once from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
