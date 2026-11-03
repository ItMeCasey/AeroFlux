package com.blakebr0.mysticalagriculture.api.crop;

/** A stand-in for Mystical Agriculture's crop. */
public class Crop {
    private final CropTier tier;

    public Crop(CropTier tier) {
        this.tier = tier;
    }

    public CropTier getTier() {
        return tier;
    }
}
