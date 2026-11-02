package com.itmecasey.aeroflux.devtest;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.FluxType;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.machine.EnchantRules;
import com.itmecasey.aeroflux.core.machine.MachineType;
import com.itmecasey.aeroflux.machine.FluxMachineBlockEntity;
import com.itmecasey.aeroflux.registry.ModBlocks;
import com.itmecasey.aeroflux.registry.ModFluids;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;

/**
 * Checks the {@code enchanting} config: that the file's defaults are the built-in rules, and that
 * changed settings reach the machines. One Enchanter and one Disenchanter run under changed
 * settings: curses allowed, levels capped at II, two lapis per level, Unbreaking and Mending
 * blocked, and experience given back. Treasure stays allowed here, because every curse is treasure
 * too; the treasure switch is covered by the shared-core tests.
 *
 * <p>The settings are global, so this waits until {@link MachineSelfTest}, which checks the
 * defaults, is done, and puts the config's own settings back when it finishes. It builds at y 244,
 * on a glass floor at y 243.
 */
final class EnchantConfigSelfTest {
    private static final int Y = 244;
    private static final int CHECK = 400;
    /**
     * Seven a level: the three stripped levels give 21, which splits into orbs of 17, 3 and 1.
     * Orbs of the same size merge and hide how many they hold, so the sizes must all differ.
     */
    private static final int XP_PER_LEVEL = 7;
    private static final EnchantRules.Settings CHANGED = new EnchantRules.Settings(true, true, 2, 100, 2, 50,
            true, XP_PER_LEVEL, List.of("minecraft:unbreaking", "minecraft:mending"), List.of());

    private static ServerLevel level;
    private static int tick;
    private static boolean built;
    private static boolean finished;
    private static BlockPos enchanter;
    private static BlockPos disenchanter;

    private EnchantConfigSelfTest() {
    }

    static void setUp(ServerLevel serverLevel) {
        level = serverLevel;
        tick = 0;
        built = false;
        finished = false;
    }

    static boolean finished() {
        return finished;
    }

    static void tick() {
        if (level == null || finished) {
            return;
        }
        if (!built) {
            SelfTest.check("enchanting: the config's defaults are the built-in rules",
                    AeroFluxConfig.enchantSettings().equals(EnchantRules.DEFAULTS) && EnchantRules.settings().equals(EnchantRules.DEFAULTS));
            EnchantRules.use(CHANGED);
            build();
            built = true;
            return;
        }
        if (++tick == CHECK) {
            check();
            EnchantRules.use(AeroFluxConfig.enchantSettings());
            finished = true;
        }
    }

    private static void build() {
        // A glass floor under both machines: experience orbs pop out with a little speed and
        // would otherwise roll off the machine and fall out of reach.
        for (int x = 0; x <= 8; x++) {
            for (int z = 2; z <= 10; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        enchanter = machine(MachineType.ENCHANTER, 2, new ItemStack(Items.IRON_SWORD),
                book(Enchantments.SHARPNESS, 3, Enchantments.MENDING, 1, Enchantments.VANISHING_CURSE, 1, Enchantments.UNBREAKING, 2),
                new ItemStack(Items.LAPIS_LAZULI, 20));

        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.enchant(Enchantments.SHARPNESS, 3);
        worn.enchant(Enchantments.UNBREAKING, 2);
        disenchanter = machine(MachineType.DISENCHANTER, 4, worn, new ItemStack(Items.BOOK));
    }

    private static void check() {
        FluxMachineBlockEntity enchanting = at(enchanter);
        ItemStack sword = enchanting.stackIn(MachineType.ENCHANTER.inputSlots());
        SelfTest.check("enchanting: the level cap holds Sharpness III on a book to II",
                sword.is(Items.IRON_SWORD) && level(sword, Enchantments.SHARPNESS) == 2);
        SelfTest.check("enchanting: with curses switched on the curse goes on", level(sword, Enchantments.VANISHING_CURSE) == 1);
        SelfTest.check("enchanting: a blocked enchantment is not applied",
                level(sword, Enchantments.UNBREAKING) == 0 && level(sword, Enchantments.MENDING) == 0);
        // Sharpness II and the curse: three levels, at two lapis each.
        SelfTest.check("enchanting: lapis per level follows the config", enchanting.stackIn(2).getCount() == 20 - 6);

        FluxMachineBlockEntity stripping = at(disenchanter);
        int out = MachineType.DISENCHANTER.inputSlots();
        ItemStack stripped = stripping.stackIn(out);
        // On an enchanted book the helper reads the stored enchantments.
        ItemStack newBook = stripping.stackIn(out + 1);
        SelfTest.check("enchanting: the Disenchanter leaves a blocked enchantment on the item",
                level(stripped, Enchantments.UNBREAKING) == 2 && level(stripped, Enchantments.SHARPNESS) == 0
                        && newBook.is(Items.ENCHANTED_BOOK)
                        && level(newBook, Enchantments.SHARPNESS) == 3 && level(newBook, Enchantments.UNBREAKING) == 0);
        int xp = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(disenchanter).inflate(4))) {
            xp += orb.getValue();
        }
        SelfTest.check("enchanting: the Disenchanter gives back experience when switched on", xp == 3 * XP_PER_LEVEL);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static BlockPos machine(MachineType type, int x, ItemStack... inputs) {
        BlockPos pos = new BlockPos(x, Y, 6);
        level.setBlock(pos, ModBlocks.machine(type, Tier.FLUXIUM).get().defaultBlockState(), Block.UPDATE_ALL);
        FluxMachineBlockEntity machine = at(pos);
        machine.setFlux(new FluidStack(ModFluids.fluidOf(FluxType.MOON), 10_000));
        for (int i = 0; i < inputs.length; i++) {
            machine.setStackIn(i, inputs[i]);
        }
        return pos;
    }

    private static FluxMachineBlockEntity at(BlockPos pos) {
        return (FluxMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static int level(ItemStack stack, Enchantment enchantment) {
        return EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment, 0);
    }

    /** An enchanted book holding the given enchantments, as pairs of enchantment and level. */
    private static ItemStack book(Object... pairs) {
        Map<Enchantment, Integer> stored = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            stored.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(stored, book);
        return book;
    }
}
