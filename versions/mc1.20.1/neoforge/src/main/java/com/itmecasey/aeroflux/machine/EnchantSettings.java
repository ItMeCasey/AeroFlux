package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Hands the {@code enchanting} part of the config to {@link EnchantRules} whenever the file loads or
 * is reloaded, and answers the block and allow lists for a real enchantment.
 *
 * <p>The config events are mod bus events on this version. In 1.20.1 an enchantment is a plain
 * registry entry, so its id and tags come from the built-in enchantment registry.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EnchantSettings {
    private EnchantSettings() {
    }

    @SubscribeEvent
    public static void configLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == AeroFluxConfig.SPEC) {
            EnchantRules.use(AeroFluxConfig.enchantSettings());
        }
    }

    @SubscribeEvent
    public static void configReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == AeroFluxConfig.SPEC) {
            EnchantRules.use(AeroFluxConfig.enchantSettings());
        }
    }

    /** Whether the block and allow lists let the Enchanter and Disenchanter move this enchantment. */
    public static boolean listsAllow(Enchantment enchantment) {
        ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        String id = key == null ? "" : key.toString();
        return EnchantRules.listsAllow(id, tag -> {
            ResourceLocation tagId = ResourceLocation.tryParse(tag);
            return tagId != null && key != null
                    && BuiltInRegistries.ENCHANTMENT.wrapAsHolder(enchantment).is(TagKey.create(Registries.ENCHANTMENT, tagId));
        });
    }
}
