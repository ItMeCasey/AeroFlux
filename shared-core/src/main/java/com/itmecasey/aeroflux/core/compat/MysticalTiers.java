package com.itmecasey.aeroflux.core.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks Mystical Agriculture which farmland suits a seed, without depending on the mod. Its
 * seeds are items that provide a crop ({@code ICropProvider.getCrop()}), a crop has a tier, and
 * a tier knows its own farmland ({@code CropTier.isEffectiveFarmland(Block)}), which also covers
 * blocks the mod tags as always effective. The classes are the same on every version of the mod
 * AeroFlux supports, so plain reflection on the objects it is handed is enough, and anything
 * unexpected just means "no match". Nothing here names a Minecraft class.
 */
public final class MysticalTiers {
    private static final String PROVIDER = "com.blakebr0.mysticalagriculture.api.crop.ICropProvider";

    /** Looked-up methods per class; {@link Optional#empty()} where the class has no such method. */
    private static final Map<Class<?>, Optional<Method>> GET_CROP = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> GET_TIER = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> IS_EFFECTIVE = new ConcurrentHashMap<>();

    private MysticalTiers() {
    }

    /**
     * Whether the block is effective farmland for the tier of the crop this seed item plants.
     *
     * @param seedItem  the seed's item; anything that is not a Mystical Agriculture seed gives {@code false}
     * @param soilBlock the block in the soil slot
     */
    public static boolean farmlandMatches(Object seedItem, Object soilBlock) {
        if (!providesCrops(seedItem.getClass())) {
            return false;
        }
        try {
            Object crop = call(GET_CROP, seedItem, "getCrop", 0);
            Object tier = crop == null ? null : call(GET_TIER, crop, "getTier", 0);
            return tier != null && Boolean.TRUE.equals(call(IS_EFFECTIVE, tier, "isEffectiveFarmland", 1, soilBlock));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Whether the class, or anything it extends or implements, is Mystical Agriculture's crop provider. */
    private static boolean providesCrops(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.getName().equals(PROVIDER)) {
            return true;
        }
        for (Class<?> parent : type.getInterfaces()) {
            if (providesCrops(parent)) {
                return true;
            }
        }
        return providesCrops(type.getSuperclass());
    }

    private static Object call(Map<Class<?>, Optional<Method>> cache, Object target, String name, int arguments,
                               Object... values) throws ReflectiveOperationException {
        Optional<Method> method = cache.computeIfAbsent(target.getClass(), type -> find(type, name, arguments));
        return method.isPresent() ? method.get().invoke(target, values) : null;
    }

    private static Optional<Method> find(Class<?> type, String name, int arguments) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}
