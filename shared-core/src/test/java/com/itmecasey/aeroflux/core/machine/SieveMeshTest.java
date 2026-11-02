package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

class SieveMeshTest {
    @Test
    void namesFromEitherModBecomeThePlainMesh() {
        assertEquals("iron", SieveMesh.normalize("exdeorum:iron_mesh"));
        assertEquals("string", SieveMesh.normalize("STRING"));
        assertEquals("", SieveMesh.normalize(""));
    }

    @Test
    void eachTierRunsItsOwnMeshAndEverythingBelow() {
        assertTrue(SieveMesh.runs(Tier.COPPER, "string"));
        assertFalse(SieveMesh.runs(Tier.COPPER, "flint"));
        assertTrue(SieveMesh.runs(Tier.GOLD, "flint"));
        assertTrue(SieveMesh.runs(Tier.GOLD, "iron"));
        assertFalse(SieveMesh.runs(Tier.GOLD, "emerald"));
        assertTrue(SieveMesh.runs(Tier.DIAMOND, "emerald"));
        assertFalse(SieveMesh.runs(Tier.DIAMOND, "netherite"));
        assertTrue(SieveMesh.runs(Tier.NETHERITE, "netherite"));
    }

    @Test
    void anUnknownMeshNeedsFluxium() {
        assertFalse(SieveMesh.runs(Tier.NETHERITE, "othermod:crystal_mesh"));
        assertTrue(SieveMesh.runs(Tier.FLUXIUM, "othermod:crystal_mesh"));
    }

    @Test
    void fluxiumIsLuckierButNeverPastCertain() {
        assertEquals(0.25, SieveMesh.chance(Tier.DIAMOND, 0.25), 1e-9);
        assertEquals(0.3125, SieveMesh.chance(Tier.FLUXIUM, 0.25), 1e-9);
        assertEquals(1.0, SieveMesh.chance(Tier.FLUXIUM, 0.9), 1e-9);
    }
}
