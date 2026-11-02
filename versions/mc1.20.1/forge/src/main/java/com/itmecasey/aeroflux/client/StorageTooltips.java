package com.itmecasey.aeroflux.client;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.StorageSizes;
import com.itmecasey.aeroflux.block.FluxCollectorBlock;
import com.itmecasey.aeroflux.block.FluxTankBlock;
import com.itmecasey.aeroflux.chunkloader.FluxChunkLoaderBlock;
import com.itmecasey.aeroflux.converter.FluxConverterBlock;
import com.itmecasey.aeroflux.core.converter.ConverterType;
import com.itmecasey.aeroflux.lamp.FluxLampBlock;
import com.itmecasey.aeroflux.machine.FluxMachineBlock;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Adds how much a block holds to its item's tooltip: a Flux Tank's size, a collector's buffer, and
 * the Flux tank of machines, converters, chunk loaders and lamps.
 *
 * <p>The numbers come from {@link StorageSizes}, so on a server they are the server's sizes,
 * even when they differ from this game's own config.
 */
@Mod.EventBusSubscriber(modid = AeroFlux.MODID, value = Dist.CLIENT)
public final class StorageTooltips {
    private StorageTooltips() {
    }

    @SubscribeEvent
    public static void addSize(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof BlockItem item)) {
            return;
        }
        Component line = sizeLine(item.getBlock());
        if (line != null) {
            // Right under the name, where vanilla puts a block's own short facts.
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), line);
        }
    }

    /** The size line for a block, or {@code null} if it is not one of the blocks that hold something. */
    private static @Nullable Component sizeLine(Block block) {
        String key = "tooltip." + AeroFlux.MODID + ".holds.";
        if (block instanceof FluxTankBlock tank) {
            return line(key + "tank", mb(StorageSizes.tank(tank.tier())));
        }
        if (block instanceof FluxCollectorBlock collector) {
            return line(key + "collector", mb(StorageSizes.collector(collector.tier())));
        }
        if (block instanceof FluxMachineBlock machine) {
            return line(key + "machine", mb(StorageSizes.machine(machine.tier())));
        }
        if (block instanceof FluxConverterBlock converter) {
            ConverterType type = converter.type();
            return line(key + "converter_" + type.name().toLowerCase(Locale.ROOT),
                    mb(StorageSizes.converterTank(converter.tier())),
                    number(StorageSizes.converterBuffer(converter.tier(), type)));
        }
        if (block instanceof FluxChunkLoaderBlock loader) {
            return line(key + "chunk_loader", mb(StorageSizes.chunkLoader(loader.tier())));
        }
        if (block instanceof FluxLampBlock) {
            return line(key + "lamp", mb(StorageSizes.lamp()));
        }
        return null;
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    /** An amount of fluid: mB with thousands separators, and the same in buckets when it is whole buckets. */
    private static String mb(int amount) {
        String text = number(amount) + " mB";
        return amount % 1_000 == 0 ? text + " (" + number(amount / 1_000) + " B)" : text;
    }

    private static String number(int amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }
}
