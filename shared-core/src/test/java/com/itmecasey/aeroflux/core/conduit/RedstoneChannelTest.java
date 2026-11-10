package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import org.junit.jupiter.api.Test;

/** The redstone channel of a face: how it is set, kept, saved and stepped through. */
class RedstoneChannelTest {
    private static final int LANE = PipeType.REDSTONE.lane();

    @Test
    void aFaceStartsOnChannelZero() {
        assertEquals(0, new EndpointSettings().channel(LANE, Face.NORTH));
    }

    @Test
    void settingAChannelChangesOnlyThatFace() {
        EndpointSettings settings = new EndpointSettings();
        assertTrue(settings.setChannel(LANE, Face.NORTH, 5));
        assertEquals(5, settings.channel(LANE, Face.NORTH));
        assertEquals(0, settings.channel(LANE, Face.SOUTH));
        assertFalse(settings.setChannel(LANE, Face.NORTH, 5), "the same value is no change");
    }

    @Test
    void outOfRangeChannelsAreRefused() {
        EndpointSettings settings = new EndpointSettings();
        assertFalse(settings.setChannel(LANE, Face.UP, -1));
        assertFalse(settings.setChannel(LANE, Face.UP, EndpointSettings.MAX_CHANNELS));
    }

    @Test
    void channelsSurviveSaving() {
        EndpointSettings settings = new EndpointSettings();
        settings.setChannel(LANE, Face.EAST, 11);
        settings.setChannel(LANE, Face.WEST, 15);
        EndpointSettings back = EndpointSettings.decode(settings.encode());
        assertEquals(11, back.channel(LANE, Face.EAST));
        assertEquals(15, back.channel(LANE, Face.WEST));
        assertEquals(settings, back);
    }

    @Test
    void channelsAndUpgradesShareAFace() {
        EndpointSettings settings = new EndpointSettings();
        settings.install(PipeType.ITEM.lane(), Face.NORTH, EndpointSettings.Upgrade.PRIORITY);
        settings.setChannel(LANE, Face.NORTH, 3);
        EndpointSettings back = EndpointSettings.decode(settings.encode());
        assertEquals(3, back.channel(LANE, Face.NORTH));
        assertTrue(back.has(PipeType.ITEM.lane(), Face.NORTH, EndpointSettings.Upgrade.PRIORITY));
    }

    @Test
    void dataSavedBeforeChannelsReadsAsChannelZero() {
        // Version 1, one record: lane 3, face 0, flags 2 (a Priority upgrade), priority 4, no filter entries.
        EndpointSettings old = EndpointSettings.decode("1;" + LANE + ",0,2,4,");
        assertEquals(0, old.channel(LANE, Face.byIndex(0)));
    }

    @Test
    void resettingToZeroForgetsTheFace() {
        EndpointSettings settings = new EndpointSettings();
        settings.setChannel(LANE, Face.DOWN, 2);
        settings.setChannel(LANE, Face.DOWN, 0);
        assertTrue(settings.isEmpty());
    }

    @Test
    void channelsOfOtherPipesAreDroppedWhenTheyAreChecked() {
        EndpointSettings settings = new EndpointSettings();
        settings.setChannel(PipeType.FLUID.lane(), Face.NORTH, 4);
        settings.retainValid(PipeSet.of(PipeType.FLUID));
        assertEquals(0, settings.channel(PipeType.FLUID.lane(), Face.NORTH));
    }

    @Test
    void steppingWrapsAtTheTiersChannelCount() {
        assertEquals(0, RedstoneChannels.next(0, Tier.COPPER));
        assertEquals(1, RedstoneChannels.next(0, Tier.IRON));
        assertEquals(0, RedstoneChannels.next(1, Tier.IRON));
        assertEquals(0, RedstoneChannels.next(15, Tier.FLUXIUM));
        assertEquals(0, RedstoneChannels.next(9, Tier.GOLD), "a face above the count starts over");
    }

    @Test
    void everyChannelHasADyeNameAndColor() {
        assertEquals(EndpointSettings.MAX_CHANNELS, RedstoneChannels.values().length);
        assertEquals("light_blue", RedstoneChannels.of(3).id());
        assertEquals(RedstoneChannels.WHITE, RedstoneChannels.of(16));
    }
}
