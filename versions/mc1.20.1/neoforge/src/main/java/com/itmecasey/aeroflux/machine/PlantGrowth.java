package com.itmecasey.aeroflux.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * How the Plant Grower grows a plant no recipe names, which covers most modded crops and trees.
 *
 * <ul>
 *     <li>A seed that plants a block with an {@code age}, such as any mod's crop, is grown to its
 *     last age and the crop's own loot table is rolled, as if a player broke it ripe: whatever the
 *     crop drops, seeds included, comes out.</li>
 *     <li>A sapling gives the log of its tree, found by name the way nearly every mod names them
 *     ({@code x:fir_sapling} grows {@code x:fir_log}, or a {@code _stem} or {@code _wood}), and its
 *     sapling back half of the time; with a Silk Touch Upgrade it keeps some leaves too.</li>
 * </ul>
 *
 * <p>Recipes win where they exist, so a pack can give any plant its own harvest with a recipe.
 */
public final class PlantGrowth {
    /** Ticks a crop takes before tier speed, as long as the grower's own crop recipes. */
    public static final int CROP_TICKS = 400;
    /** Ticks a tree takes before tier speed, as long as the grower's own tree recipes. */
    public static final int TREE_TICKS = 600;
    private static final int LOGS_PER_TREE = 3;
    private static final int LEAVES_PER_TREE = 2;

    private PlantGrowth() {
    }

    /** Whether the grower can grow this item with no recipe. */
    public static boolean grows(ItemStack stack) {
        return crop(stack) != null || treeLog(stack) != null;
    }

    /** How long growing this item takes before tier speed, in ticks. */
    public static int ticks(ItemStack stack) {
        return treeLog(stack) != null ? TREE_TICKS : CROP_TICKS;
    }

    /** One harvest of the plant this item grows; empty when it grows nothing. */
    public static List<ItemStack> harvest(ServerLevel level, BlockPos pos, ItemStack seed, boolean silkTouch, RandomSource random) {
        Item log = treeLog(seed);
        if (log != null) {
            List<ItemStack> products = new ArrayList<>();
            products.add(new ItemStack(log, LOGS_PER_TREE));
            Item leaves = sibling(seed.getItem(), "_leaves");
            if (silkTouch && leaves != null) {
                products.add(new ItemStack(leaves, LEAVES_PER_TREE));
            }
            if (random.nextBoolean()) {
                products.add(seed.copyWithCount(1));
            }
            return products;
        }
        BlockState ripe = crop(seed);
        if (ripe == null) {
            return List.of();
        }
        ItemStack tool = silkTouch ? silkTouchHoe() : ItemStack.EMPTY;
        return Block.getDrops(ripe, level, pos, null, null, tool);
    }

    /** The ripe crop a seed plants: its block at the last age, or {@code null} when it plants no aging block. */
    private static @Nullable BlockState crop(ItemStack seed) {
        if (seed.isEmpty() || !(seed.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof SaplingBlock) {
            return null;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && age.getName().equals("age")) {
                return state.setValue(age, Collections.max(age.getPossibleValues()));
            }
        }
        return null;
    }

    /** The log a sapling grows into, found by name; {@code null} for anything else. */
    private static @Nullable Item treeLog(ItemStack stack) {
        boolean sapling = stack.is(ItemTags.SAPLINGS)
                || (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock);
        if (!sapling) {
            return null;
        }
        for (String suffix : List.of("_log", "_stem", "_wood")) {
            Item log = sibling(stack.getItem(), suffix);
            if (log != null) {
                return log;
            }
        }
        return null;
    }

    /**
     * The item named like this sapling with another ending: {@code x:fir_sapling} and
     * {@code "_log"} give {@code x:fir_log}. Mangroves grow from propagules, so those count too.
     */
    private static @Nullable Item sibling(Item sapling, String suffix) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(sapling);
        String path = id.getPath();
        for (String ending : List.of("_sapling", "_propagule")) {
            if (path.endsWith(ending)) {
                ResourceLocation wanted = new ResourceLocation(id.getNamespace(),
                        path.substring(0, path.length() - ending.length()) + suffix);
                Item item = BuiltInRegistries.ITEM.get(wanted);
                return item == Items.AIR ? null : item;
            }
        }
        return null;
    }

    /** A hoe with Silk Touch, the tool a Silk Touch Upgrade harvests with. */
    private static ItemStack silkTouchHoe() {
        ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
        hoe.enchant(Enchantments.SILK_TOUCH, 1);
        return hoe;
    }
}
