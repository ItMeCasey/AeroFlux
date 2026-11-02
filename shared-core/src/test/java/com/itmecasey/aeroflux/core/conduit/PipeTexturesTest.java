package com.itmecasey.aeroflux.core.conduit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.aeroflux.core.Tier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PipeTexturesTest {
    /** The generated textures, as the art tool writes them. */
    private static final Path TEXTURES = Path.of("..", "assets-src", "aeroflux_pipes", "textures", "block");

    @Test
    void namesFollowTheArtsPattern() {
        assertEquals("pipe_fluid_arm_v", PipeTextures.name(PipeType.FLUID, Tier.COPPER, "arm_v"));
        assertEquals("pipe_fluid_arm_v_gold", PipeTextures.name(PipeType.FLUID, Tier.GOLD, "arm_v"));
        assertEquals("pipe_fluid_plug", PipeTextures.name(PipeType.FLUID, Tier.GOLD, "plug"));
        assertEquals("pipe_dense_me_core_end", PipeTextures.name(PipeType.DENSE_ME, Tier.FLUXIUM, "core_end"));
    }

    @Test
    void everyNameHasItsFileAndNothingIsLeftOver() throws Exception {
        List<String> names = PipeTextures.allNames();
        assertEquals(names.size(), new HashSet<>(names).size(), "duplicate names");
        List<String> files;
        try (Stream<Path> list = Files.list(TEXTURES)) {
            files = list.map(path -> path.getFileName().toString().replace(".png", "")).sorted().toList();
        }
        assertEquals(names.stream().sorted().toList(), files);
    }

    @Test
    void sidesFollowTheFaceTable() {
        // x: every side is H.
        for (Face face : List.of(Face.UP, Face.DOWN, Face.NORTH, Face.SOUTH)) {
            assertFalse(PipeTextures.vertical(0, face));
        }
        // y: every side is V.
        for (Face face : List.of(Face.NORTH, Face.SOUTH, Face.EAST, Face.WEST)) {
            assertTrue(PipeTextures.vertical(1, face));
        }
        // z: up and down are V, east and west H.
        assertTrue(PipeTextures.vertical(2, Face.UP));
        assertTrue(PipeTextures.vertical(2, Face.DOWN));
        assertFalse(PipeTextures.vertical(2, Face.EAST));
        assertFalse(PipeTextures.vertical(2, Face.WEST));
        assertTrue(PipeTextures.isEnd(2, Face.NORTH));
        assertFalse(PipeTextures.isEnd(2, Face.UP));
    }

    @Test
    void armsShowTheirShareOfTheTexture() {
        assertArrayEquals(new float[] {0, 0, 16, 8}, PipeTextures.armUv(true, 5));
        assertArrayEquals(new float[] {0, 0, 16, 16}, PipeTextures.armUv(false, 10));
        assertArrayEquals(new float[] {0, 0, 16, 16}, PipeTextures.armUv(false, 12), "never more than the whole texture");
    }
}
