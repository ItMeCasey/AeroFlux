package com.itmecasey.aeroflux.machine.configurator;

import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorMode;
import com.itmecasey.aeroflux.core.configurator.ConfiguratorPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the Flux Configurator remembers, kept on the item stack so it travels with the item
 * (and survives death, a chest or a server restart): its mode, the eight presets with the
 * selected one, and the corners of an area being marked. All reads and writes go through here,
 * like the Conduit Tool's {@code ToolStackData}. In 1.20.1 the stack's NBT holds it.
 */
public final class ConfiguratorStackData {
    private static final String KEY_MODE = "mode";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_FIRST = "first";
    private static final String KEY_SECOND = "second";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_DIMENSION = "dimension";

    private ConfiguratorStackData() {
    }

    /** A marked corner of an area, with the dimension it was marked in. */
    public record Corner(RunPlanner.Pos pos, String dimension) {
    }

    public static ConfiguratorMode mode(ItemStack stack) {
        return ConfiguratorMode.byId(read(stack).getString(KEY_MODE));
    }

    /** Changes the mode. Marked corners belong to the area mode, so they are forgotten. */
    public static void setMode(ItemStack stack, ConfiguratorMode mode) {
        update(stack, tag -> {
            tag.putString(KEY_MODE, mode.id());
            tag.remove(KEY_FIRST);
            tag.remove(KEY_SECOND);
        });
    }

    /** The presets stored on the stack, with the selected slot. */
    public static ConfiguratorPresets presets(ItemStack stack) {
        CompoundTag tag = read(stack);
        ListTag list = tag.getList(KEY_PRESETS, Tag.TAG_STRING);
        List<String> lines = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            lines.add(list.getString(i));
        }
        return ConfiguratorPresets.decode(lines, tag.getInt(KEY_SELECTED));
    }

    /** Writes the presets and the selected slot back to the stack. */
    public static void setPresets(ItemStack stack, ConfiguratorPresets presets) {
        update(stack, tag -> {
            ListTag list = new ListTag();
            for (String line : presets.encode()) {
                list.add(StringTag.valueOf(line));
            }
            tag.put(KEY_PRESETS, list);
            tag.putInt(KEY_SELECTED, presets.selected());
        });
    }

    /** The first marked corner, or {@code null}. */
    public static Corner firstCorner(ItemStack stack) {
        return corner(read(stack), KEY_FIRST);
    }

    /** The second marked corner, or {@code null}; it is only set while an area waits to be confirmed. */
    public static Corner secondCorner(ItemStack stack) {
        return corner(read(stack), KEY_SECOND);
    }

    public static void setFirstCorner(ItemStack stack, Corner corner) {
        update(stack, tag -> {
            tag.put(KEY_FIRST, write(corner));
            tag.remove(KEY_SECOND);
        });
    }

    public static void setSecondCorner(ItemStack stack, Corner corner) {
        update(stack, tag -> tag.put(KEY_SECOND, write(corner)));
    }

    /** Forgets both corners. */
    public static void clearCorners(ItemStack stack) {
        update(stack, tag -> {
            tag.remove(KEY_FIRST);
            tag.remove(KEY_SECOND);
        });
    }

    private static Corner corner(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag corner = tag.getCompound(key);
        return new Corner(new RunPlanner.Pos(corner.getInt(KEY_X), corner.getInt(KEY_Y), corner.getInt(KEY_Z)),
                corner.getString(KEY_DIMENSION));
    }

    private static CompoundTag write(Corner corner) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_X, corner.pos().x());
        tag.putInt(KEY_Y, corner.pos().y());
        tag.putInt(KEY_Z, corner.pos().z());
        tag.putString(KEY_DIMENSION, corner.dimension());
        return tag;
    }

    /** The stack's tag, or an empty one that is not attached to the stack. Reading never adds a tag. */
    private static CompoundTag read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag;
    }

    private static void update(ItemStack stack, Consumer<CompoundTag> change) {
        change.accept(stack.getOrCreateTag());
    }
}
