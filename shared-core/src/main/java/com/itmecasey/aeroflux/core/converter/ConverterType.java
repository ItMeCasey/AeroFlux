package com.itmecasey.aeroflux.core.converter;

/** The machines that turn Flux into another mod's power. */
public enum ConverterType {
    /** Flux into Forge Energy (FE), the power most tech mods use. */
    ENERGY("flux_energy_converter", "Flux Energy Converter"),
    /** Flux into Applied Energistics 2 power (AE), fed straight into an ME network. */
    AE("flux_ae_converter", "Flux AE Converter"),
    /** Flux into Create's rotational force, measured in stress units. */
    STRESS("flux_stress_converter", "Flux Stress Converter");

    private final String blockId;
    private final String displayName;

    ConverterType(String blockId, String displayName) {
        this.blockId = blockId;
        this.displayName = displayName;
    }

    /** The registry name of the first-tier block; higher tiers put the tier in front. */
    public String blockId() {
        return blockId;
    }

    /** The English name of the first-tier block. */
    public String displayName() {
        return displayName;
    }
}
