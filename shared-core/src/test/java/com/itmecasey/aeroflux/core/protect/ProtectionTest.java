package com.itmecasey.aeroflux.core.protect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.machine.MachineType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtectionTest {
    private static final List<Verdict> NO_ANSWERS = List.of();

    @Test
    void aMachineWithoutAnOwnerIsNeverAsked() {
        assertFalse(ProtectionPolicy.shouldAsk(true, false));
        assertTrue(ProtectionPolicy.machineMayAct(true, false, List.of(Verdict.DENY)));
    }

    @Test
    void switchedOffNothingIsAsked() {
        assertFalse(ProtectionPolicy.shouldAsk(false, true));
        assertTrue(ProtectionPolicy.machineMayAct(false, true, List.of(Verdict.DENY)));
        assertTrue(ProtectionPolicy.playerMayAct(false, List.of(Verdict.DENY)));
    }

    @Test
    void anOwnedMachineIsHeldToTheAnswers() {
        assertTrue(ProtectionPolicy.shouldAsk(true, true));
        assertTrue(ProtectionPolicy.machineMayAct(true, true, NO_ANSWERS));
        assertTrue(ProtectionPolicy.machineMayAct(true, true, List.of(Verdict.ALLOW, Verdict.ABSTAIN)));
        assertFalse(ProtectionPolicy.machineMayAct(true, true, List.of(Verdict.ALLOW, Verdict.DENY, Verdict.ALLOW)));
    }

    @Test
    void aPlayerIsHeldToTheAnswersWhenProtectionIsOn() {
        assertTrue(ProtectionPolicy.playerMayAct(true, List.of(Verdict.ABSTAIN)));
        assertFalse(ProtectionPolicy.playerMayAct(true, List.of(Verdict.DENY)));
    }

    @Test
    void anyDenialWinsWhereverItSits() {
        assertFalse(ProtectionPolicy.noneDenies(List.of(Verdict.DENY, Verdict.ALLOW)));
        assertFalse(ProtectionPolicy.noneDenies(List.of(Verdict.ALLOW, Verdict.DENY)));
        assertTrue(ProtectionPolicy.noneDenies(List.of(Verdict.ABSTAIN)));
    }

    @Test
    void theCacheAsksOnceWhileAnAnswerHolds() {
        ProtectionCache cache = new ProtectionCache(40);
        AtomicInteger asked = new AtomicInteger();
        assertTrue(cache.allowed(7L, 100, () -> {
            asked.incrementAndGet();
            return true;
        }));
        assertTrue(cache.allowed(7L, 139, () -> {
            asked.incrementAndGet();
            return false;
        }));
        assertEquals(1, asked.get());
    }

    @Test
    void theCacheAsksAgainWhenTheAnswerRunsOut() {
        ProtectionCache cache = new ProtectionCache(40);
        assertTrue(cache.allowed(7L, 100, () -> true));
        assertFalse(cache.allowed(7L, 140, () -> false));
        assertFalse(cache.allowed(7L, 141, () -> true));
    }

    @Test
    void theCacheKeepsPlacesApart() {
        ProtectionCache cache = new ProtectionCache(40);
        assertTrue(cache.allowed(1L, 0, () -> true));
        assertFalse(cache.allowed(2L, 0, () -> false));
        assertTrue(cache.allowed(1L, 1, () -> false));
        assertFalse(cache.allowed(2L, 1, () -> true));
    }

    @Test
    void clearingTheCacheForcesNewQuestions() {
        ProtectionCache cache = new ProtectionCache(40);
        assertTrue(cache.allowed(1L, 0, () -> true));
        cache.clear();
        assertEquals(0, cache.size());
        assertFalse(cache.allowed(1L, 1, () -> false));
    }

    @Test
    void theCacheStaysBounded() {
        ProtectionCache cache = new ProtectionCache(1_000);
        for (long key = 0; key < 5_000; key++) {
            cache.allowed(key, 0, () -> true);
        }
        assertTrue(cache.size() <= 256);
    }

    @Test
    void aTtlBelowOneTickStillHoldsOneTick() {
        ProtectionCache cache = new ProtectionCache(0);
        AtomicInteger asked = new AtomicInteger();
        cache.allowed(1L, 10, () -> asked.incrementAndGet() > 0);
        cache.allowed(1L, 10, () -> asked.incrementAndGet() > 0);
        assertEquals(1, asked.get());
    }

    @Test
    void onlyTheMachinesThatReachOutAreChecked() {
        assertEquals(ProtectedMachines.Reach.OWN_SPOT, ProtectedMachines.reachOf(MachineType.SKY_CONTROLLER));
        assertEquals(ProtectedMachines.Reach.OWN_SPOT, ProtectedMachines.reachOf(MachineType.SPAWNER));
        assertEquals(ProtectedMachines.Reach.OWN_SPOT, ProtectedMachines.reachOf(MachineType.PLANT_GROWER));
        assertEquals(ProtectedMachines.Reach.ENTITIES, ProtectedMachines.reachOf(MachineType.MAGNET));
        assertEquals(ProtectedMachines.Reach.ENTITIES, ProtectedMachines.reachOf(MachineType.BREEDER));
        assertEquals(ProtectedMachines.Reach.NONE, ProtectedMachines.reachOf(MachineType.FURNACE));
        assertEquals(ProtectedMachines.Reach.NONE, ProtectedMachines.reachOf(MachineType.SIFTER));
    }

    @Test
    void reachPicksTheAction() {
        assertEquals(ProtectionAction.USE_ENTITY, ProtectedMachines.actionOf(ProtectedMachines.Reach.ENTITIES));
        assertEquals(ProtectionAction.USE_BLOCK, ProtectedMachines.actionOf(ProtectedMachines.Reach.OWN_SPOT));
    }
}
