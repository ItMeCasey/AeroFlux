package com.itmecasey.aeroflux.conduit.tool;

import com.itmecasey.aeroflux.core.conduit.PipeType;
import com.itmecasey.aeroflux.core.conduit.RunPlanner;
import com.itmecasey.aeroflux.core.conduit.ToolMode;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Everything the Conduit Tool remembers, kept on the item stack: its mode, a copied bundle and
 * the start of a pending run removal. All reads and writes go through here, so the storage
 * (custom data in this version) can be swapped without touching the tool's logic.
 */
public final class ToolStackData {
    private static final String KEY_MODE = "mode";
    private static final String KEY_COPIED = "copied";
    private static final String KEY_RUN = "run";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_PIPE = "pipe";
    private static final String KEY_DIMENSION = "dimension";

    private ToolStackData() {
    }

    /** The remembered start of a run removal. */
    public record RunStart(RunPlanner.Pos pos, PipeType pipe, String dimension) {
    }

    /** The stack's mode; {@link ToolMode#EDIT} when none was ever set or the stored name is unknown. */
    public static ToolMode mode(ItemStack stack) {
        String name = read(stack).getString(KEY_MODE);
        for (ToolMode mode : ToolMode.values()) {
            if (mode.name().toLowerCase(Locale.ROOT).equals(name)) {
                return mode;
            }
        }
        return ToolMode.EDIT;
    }

    /** Changes the mode. A pending run only makes sense in its own mode, so it is forgotten. */
    public static void setMode(ItemStack stack, ToolMode mode) {
        update(stack, tag -> {
            tag.putString(KEY_MODE, mode.name().toLowerCase(Locale.ROOT));
            tag.remove(KEY_RUN);
        });
    }

    /** The serialized bundle last copied, or {@code null} if nothing was copied. */
    public static int[] copiedBundle(ItemStack stack) {
        CompoundTag tag = read(stack);
        return tag.contains(KEY_COPIED, Tag.TAG_INT_ARRAY) ? tag.getIntArray(KEY_COPIED) : null;
    }

    /** Remembers a bundle's serialized state. */
    public static void setCopiedBundle(ItemStack stack, int[] serialized) {
        update(stack, tag -> tag.putIntArray(KEY_COPIED, serialized));
    }

    /** The pending run start, or {@code null} if none is remembered. */
    public static RunStart runStart(ItemStack stack) {
        CompoundTag tag = read(stack);
        if (!tag.contains(KEY_RUN, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag run = tag.getCompound(KEY_RUN);
        for (PipeType type : PipeType.values()) {
            if (type.name().equals(run.getString(KEY_PIPE))) {
                return new RunStart(new RunPlanner.Pos(run.getInt(KEY_X), run.getInt(KEY_Y), run.getInt(KEY_Z)),
                        type, run.getString(KEY_DIMENSION));
            }
        }
        return null;
    }

    /** Remembers where a run removal started and which pipe it is about. */
    public static void setRunStart(ItemStack stack, RunStart start) {
        update(stack, tag -> {
            CompoundTag run = new CompoundTag();
            run.putInt(KEY_X, start.pos().x());
            run.putInt(KEY_Y, start.pos().y());
            run.putInt(KEY_Z, start.pos().z());
            run.putString(KEY_PIPE, start.pipe().name());
            run.putString(KEY_DIMENSION, start.dimension());
            tag.put(KEY_RUN, run);
        });
    }

    /** Forgets the pending run start. */
    public static void clearRunStart(ItemStack stack) {
        update(stack, tag -> tag.remove(KEY_RUN));
    }

    private static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void update(ItemStack stack, Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }
}
