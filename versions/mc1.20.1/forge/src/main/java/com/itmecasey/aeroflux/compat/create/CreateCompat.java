package com.itmecasey.aeroflux.compat.create;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.List;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Everything the rest of AeroFlux asks of Create, behind a check that Create is installed, the
 * same way {@code Ae2Compat} keeps AE2 out of the way.
 *
 * <p>The Flux Stress converter has to be a Create kinetic block (its block entity extends
 * Create's generator class), so unlike the AE converter it cannot exist without Create: its six
 * tiers are only registered when Create is loaded. Without Create there is no such block, no
 * item and no recipe, and worlds never see it.
 */
public final class CreateCompat {
    private static final boolean LOADED = ModList.get().isLoaded("create");

    private CreateCompat() {
    }

    /** Whether Create is installed. */
    public static boolean loaded() {
        return LOADED;
    }

    /** Registers the Stress converter's blocks, items and block entity; called from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        if (LOADED) {
            CreateContent.register(modEventBus);
        }
    }

    /** The registry name of the Stress converter of one tier, as for the other converters. */
    public static String converterName(Tier tier) {
        return ModBlocks.converterName(ConverterType.STRESS, tier);
    }

    /** The Stress converter blocks, copper first, for the creative tab; empty without Create. */
    public static List<ItemLike> stressConverters() {
        return LOADED ? CreateContent.converters() : List.of();
    }
}
