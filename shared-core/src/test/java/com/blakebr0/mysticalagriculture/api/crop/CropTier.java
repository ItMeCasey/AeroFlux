package com.blakebr0.mysticalagriculture.api.crop;

/** A stand-in for Mystical Agriculture's tier: one farmland block, given here as a string. */
public class CropTier {
    private final String farmland;

    public CropTier(String farmland) {
        this.farmland = farmland;
    }

    public boolean isEffectiveFarmland(String block) {
        return farmland.equals(block);
    }
}
