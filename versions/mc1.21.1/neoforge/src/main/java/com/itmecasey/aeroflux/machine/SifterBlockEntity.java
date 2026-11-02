package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.core.machine.ExtraTank;
import com.itmecasey.aeroflux.core.machine.MachineUpgrades;
import com.itmecasey.aeroflux.core.machine.SieveMesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Flux Sifter, a powered sieve for the recipes of Ex Deorum and Ex Nihilo Sequentia (see
 * {@link SieveImports}); it has no recipes of its own. The player puts the block to sift in one
 * slot and a mesh from either mod in the other. Like those mods' sieves, one block rolls every drop
 * its recipes give for that mesh, each on its own; the mesh is never used up. Waterlogged recipes
 * take water from the tank. The tier sets only the speed, as it does for every machine, and
 * Fortune Upgrades raise the drop chances.
 */
public class SifterBlockEntity extends FluxMachineBlockEntity {
    /** One block takes five seconds at copper; tier speed and Speed upgrades shorten it. */
    public static final int SIFT_TICKS = 100;

    public SifterBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /** The block slot takes what some sieve recipe sifts, the mesh slot what some recipe uses as a mesh. */
    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    /** The mesh slot holds a single mesh. */
    @Override
    protected int inputLimit(int slot) {
        return slot == SieveMesh.MESH_SLOT ? 1 : super.inputLimit(slot);
    }

    /** Every drop of sifting this block through this mesh. */
    private List<SieveRoll> rollsFor(ServerLevel server, ItemStack material, ItemStack mesh) {
        List<SieveRoll> found = new ArrayList<>();
        for (SieveRoll roll : SieveImports.of(server)) {
            if (roll.runs(material, mesh)) {
                found.add(roll);
            }
        }
        return found;
    }

    @Override
    protected @Nullable Job findJob() {
        ItemStack material = stackIn(SieveMesh.MATERIAL_SLOT);
        ItemStack mesh = stackIn(SieveMesh.MESH_SLOT);
        if (material.isEmpty() || mesh.isEmpty() || !(level instanceof ServerLevel server)) {
            return null;
        }
        List<SieveRoll> rolls = rollsFor(server, material, mesh);
        int water = 0;
        for (SieveRoll roll : rolls) {
            water = Math.max(water, roll.water());
        }
        int waterTank = extraTankIndex(ExtraTank.WATER);
        if (rolls.isEmpty() || (water > 0 && extraAmount(waterTank) < water)) {
            return null;
        }
        int neededWater = water;
        // The job is the pair: swapping either the block or the mesh starts the run over.
        Item materialItem = material.getItem();
        Item meshItem = mesh.getItem();
        return new Job() {
            @Override
            public Object key() {
                return List.of(materialItem, meshItem);
            }

            @Override
            public int baseTicks() {
                return SIFT_TICKS;
            }

            /** Sieve drops are all rolls, so one free slot is enough to go on. */
            @Override
            public boolean outputFits() {
                return canFitOutputs(List.of(rolls.getFirst().product().copy().copyWithCount(1)));
            }

            @Override
            public void complete() {
                consumeInput(SieveMesh.MATERIAL_SLOT, 1);
                if (neededWater > 0) {
                    drainExtra(waterTank, neededWater, false);
                }
                RandomSource random = level.getRandom();
                int fortune = upgrades(MachineUpgrades.Special.FORTUNE);
                List<ItemStack> products = new ArrayList<>();
                for (SieveRoll roll : rolls) {
                    int count = roll.amount().withFortune(fortune).roll(random::nextDouble);
                    if (count > 0) {
                        ItemStack product = roll.product().copy();
                        products.add(product.copyWithCount(count * product.getCount()));
                    }
                }
                putOutputs(products);
            }
        };
    }
}
