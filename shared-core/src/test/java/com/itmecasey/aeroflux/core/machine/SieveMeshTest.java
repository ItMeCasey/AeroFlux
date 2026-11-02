package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class SieveMeshTest {
    /** Hands out the given numbers in turn. */
    private static DoubleSupplier rolls(double... values) {
        int[] next = {0};
        return () -> values[next[0]++];
    }

    @Test
    void exNihiloMeshKindsBecomeItsMeshItems() {
        assertEquals("exnihilosequentia:flint_mesh", SieveMesh.nihiloMeshItem("flint"));
        assertEquals("exnihilosequentia:netherite_mesh", SieveMesh.nihiloMeshItem("NETHERITE"));
    }

    @Test
    void aConstantIsAlwaysTheSame() {
        assertEquals(3, new SieveMesh.Constant(3).roll(rolls()));
        assertEquals(0, new SieveMesh.Constant(-2).roll(rolls()));
    }

    @Test
    void aBinomialCountsTheTriesBelowItsChance() {
        SieveMesh.Amount amount = new SieveMesh.Binomial(3, 0.5);
        assertEquals(2, amount.roll(rolls(0.1, 0.7, 0.49)));
        assertEquals(0, amount.roll(rolls(0.5, 0.9, 0.6)));
    }

    @Test
    void aUniformRangeCoversBothEnds() {
        SieveMesh.Amount amount = new SieveMesh.Uniform(1, 3);
        assertEquals(1, amount.roll(rolls(0.0)));
        assertEquals(2, amount.roll(rolls(0.5)));
        assertEquals(3, amount.roll(rolls(0.999)));
    }

    @Test
    void aSumAddsItsParts() {
        SieveMesh.Amount amount = new SieveMesh.Sum(List.of(new SieveMesh.Constant(1), new SieveMesh.Binomial(1, 0.5)));
        assertEquals(2, amount.roll(rolls(0.2)));
        assertEquals(1, amount.roll(rolls(0.8)));
    }

    @Test
    void fortuneRaisesOnlyChances() {
        SieveMesh.Binomial raised = (SieveMesh.Binomial) new SieveMesh.Binomial(2, 0.1).withFortune(2);
        assertEquals(2, raised.tries());
        assertEquals(0.3, raised.chance(), 1e-9);
        assertEquals(new SieveMesh.Constant(2), new SieveMesh.Constant(2).withFortune(4));
        SieveMesh.Amount sum = new SieveMesh.Sum(List.of(new SieveMesh.Binomial(1, 0.95))).withFortune(1);
        assertEquals(new SieveMesh.Sum(List.of(new SieveMesh.Binomial(1, 1.0))), sum);
    }
}
