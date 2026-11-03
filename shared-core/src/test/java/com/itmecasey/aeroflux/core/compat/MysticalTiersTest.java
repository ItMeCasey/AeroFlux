package com.itmecasey.aeroflux.core.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.api.crop.CropTier;
import com.blakebr0.mysticalagriculture.api.crop.ICropProvider;
import org.junit.jupiter.api.Test;

class MysticalTiersTest {
    /** A seed item of a tier-three crop. */
    private static final class Seeds implements ICropProvider {
        @Override
        public Crop getCrop() {
            return new Crop(new CropTier("tertium_farmland"));
        }
    }

    @Test
    void theTierOfTheSeedDecidesWhichFarmlandMatches() {
        assertTrue(MysticalTiers.farmlandMatches(new Seeds(), "tertium_farmland"));
        assertFalse(MysticalTiers.farmlandMatches(new Seeds(), "inferium_farmland"));
    }

    @Test
    void anythingThatIsNotAMysticalSeedNeverMatches() {
        assertFalse(MysticalTiers.farmlandMatches("wheat_seeds", "tertium_farmland"));
    }
}
