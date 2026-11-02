package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Hands the {@code enchanting} part of the config to {@link EnchantRules} whenever the file loads or
 * is reloaded, and answers the block and allow lists for a real enchantment.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID)
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
    public static boolean listsAllow(Holder<Enchantment> enchantment) {
        String id = enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("");
        return EnchantRules.listsAllow(id, tag -> {
            Identifier tagId = Identifier.tryParse(tag);
            return tagId != null && enchantment.is(TagKey.create(Registries.ENCHANTMENT, tagId));
        });
    }
}
