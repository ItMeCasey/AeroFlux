package com.itmecasey.aeroflux.machine;

import com.itmecasey.aeroflux.AeroFluxConfig;
import com.itmecasey.aeroflux.core.machine.MachineReach;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Animal Breeder: it feeds two grown animals of the same kind in its range the food in its
 * slot, so they fall in love and breed the vanilla way. The babies stay where they are born. It
 * stops once its range holds the configured number of that kind, so a pen never fills up until
 * the server lags.
 */
public class BreederBlockEntity extends FluxMachineBlockEntity {
    private static final int BREED_TICKS = 100;
    /** Food used per pair: one for each animal. */
    private static final int FOOD_PER_PAIR = 2;
    /** How far above and below the machine it looks, in blocks. */
    private static final int HEIGHT = 2;
    private static final String KEY = "breed";

    public BreederBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected boolean isInputValid(int slot, ItemStack stack) {
        return level != null && MachineInputs.accepts(machine(), level, slot, stack);
    }

    private List<Animal> animalsFor(ItemStack food) {
        if (level == null) {
            return List.of();
        }
        int radius = MachineReach.breederRadius(tier());
        AABB pen = new AABB(worldPosition).inflate(radius, HEIGHT, radius);
        return level.getEntitiesOfClass(Animal.class, pen, animal -> animal.isAlive() && animal.isFood(food));
    }

    /** Two grown animals of one kind that are ready to breed, or {@code null}. */
    private @Nullable List<Animal> readyPair(ItemStack food) {
        List<Animal> animals = animalsFor(food);
        List<Animal> ready = new ArrayList<>();
        for (Animal animal : animals) {
            if (animal.getAge() == 0 && animal.canFallInLove()) {
                ready.add(animal);
            }
        }
        for (Animal first : ready) {
            EntityType<?> kind = first.getType();
            long ofKind = animals.stream().filter(animal -> animal.getType() == kind).count();
            if (ofKind >= AeroFluxConfig.breederAnimalCap()) {
                continue;
            }
            for (Animal second : ready) {
                if (second != first && second.getType() == kind) {
                    return List.of(first, second);
                }
            }
        }
        return null;
    }

    @Override
    protected @Nullable Job findJob() {
        ItemStack food = stackIn(0);
        if (food.getCount() < FOOD_PER_PAIR || readyPair(food) == null) {
            return null;
        }
        return new Job() {
            @Override
            public Object key() {
                return KEY;
            }

            @Override
            public int baseTicks() {
                return BREED_TICKS;
            }

            @Override
            public boolean outputFits() {
                return true;
            }

            @Override
            public void complete() {
                ItemStack now = stackIn(0);
                List<Animal> pair = now.getCount() >= FOOD_PER_PAIR ? readyPair(now) : null;
                if (pair == null) {
                    return;
                }
                for (Animal animal : pair) {
                    animal.setInLove(null);
                }
                consumeInput(0, FOOD_PER_PAIR);
            }
        };
    }
}
