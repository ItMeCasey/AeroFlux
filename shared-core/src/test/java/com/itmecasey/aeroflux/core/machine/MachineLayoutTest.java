package com.itmecasey.aeroflux.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineLayoutTest {
    private static boolean inside(MachineLayout.Rect rect) {
        return rect.x() >= 0 && rect.y() >= 0
                && rect.x() + rect.width() <= MachineLayout.WIDTH && rect.y() + rect.height() <= MachineLayout.HEIGHT;
    }

    private static List<MachineLayout.Rect> parts(MachineLayout layout) {
        List<MachineLayout.Rect> all = new ArrayList<>();
        all.add(layout.gauge());
        all.add(layout.arrow());
        all.add(layout.status());
        for (int i = 0; i < layout.inputCount(); i++) {
            all.add(MachineLayout.frameOf(layout.inputSlot(i)));
        }
        for (int i = 0; i < layout.outputCount(); i++) {
            all.add(MachineLayout.frameOf(layout.outputSlot(i)));
        }
        return all;
    }

    @Test
    void everySlotCountUpToSixFitsAndNothingOverlaps() {
        for (int inputs = 1; inputs <= 6; inputs++) {
            for (int outputs = 1; outputs <= 6; outputs++) {
                MachineLayout layout = MachineLayout.of(inputs, outputs);
                List<MachineLayout.Rect> all = parts(layout);
                for (int i = 0; i < all.size(); i++) {
                    assertTrue(inside(all.get(i)), inputs + " in, " + outputs + " out: part " + i + " leaves the panel");
                    for (int j = i + 1; j < all.size(); j++) {
                        assertFalse(all.get(i).overlaps(all.get(j)),
                                inputs + " in, " + outputs + " out: parts " + i + " and " + j + " overlap");
                    }
                }
            }
        }
    }

    @Test
    void inputsSitLeftOfTheArrowAndOutputsRightOfIt() {
        MachineLayout layout = MachineLayout.of(3, 3);
        for (int i = 0; i < 3; i++) {
            assertTrue(layout.inputSlot(i).x() + MachineLayout.SLOT < layout.arrow().x());
            assertTrue(layout.outputSlot(i).x() - 1 > layout.arrow().x() + layout.arrow().width());
        }
    }

    @Test
    void aSingleSlotIsCenteredOnTheArrow() {
        MachineLayout layout = MachineLayout.of(1, 1);
        int arrowMiddle = layout.arrow().y() + layout.arrow().height() / 2;
        assertEquals(arrowMiddle, layout.inputSlot(0).y() + 8);
        assertEquals(arrowMiddle, layout.outputSlot(0).y() + 8);
    }

    @Test
    void thePlayerInventoryFitsBelowTheMachine() {
        MachineLayout layout = MachineLayout.of(3, 3);
        assertTrue(layout.hotbarY() + MachineLayout.SLOT <= layout.height());
        assertTrue(layout.inventoryX() + 9 * MachineLayout.SLOT <= layout.width());
        assertTrue(layout.inventoryY() > layout.status().y() + layout.status().height());
    }

    // --- The side-config window --------------------------------------------------------------

    @Test
    void sideWindowPartsFitAndDoNotOverlap() {
        for (int tabs = 1; tabs <= 5; tabs++) {
            SidePanelLayout layout = new SidePanelLayout(tabs);
            List<MachineLayout.Rect> all = layout.clickable();
            for (int i = 0; i < all.size(); i++) {
                MachineLayout.Rect a = all.get(i);
                assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.width() <= SidePanelLayout.WIDTH
                        && a.y() + a.height() <= SidePanelLayout.HEIGHT, tabs + " tabs: part " + i + " leaves the window");
                for (int j = i + 1; j < all.size(); j++) {
                    assertFalse(a.overlaps(all.get(j)), tabs + " tabs: parts " + i + " and " + j + " overlap");
                }
            }
            assertTrue(layout.infoY() + 10 <= SidePanelLayout.HEIGHT);
        }
    }

    @Test
    void clicksLandWhereThePictureSaysTheyDo() {
        SidePanelLayout layout = new SidePanelLayout(2);
        for (FaceLabel label : FaceLabel.values()) {
            MachineLayout.Rect rect = layout.face(label);
            SidePanelLayout.Hit hit = layout.hit(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0);
            assertEquals(SidePanelLayout.Part.FACE, hit.part());
            assertEquals(label.ordinal(), hit.index());
        }
        for (int i = 0; i < 2; i++) {
            MachineLayout.Rect rect = layout.tab(i);
            SidePanelLayout.Hit hit = layout.hit(rect.x() + 1, rect.y() + 1);
            assertEquals(SidePanelLayout.Part.TAB, hit.part());
            assertEquals(i, hit.index());
        }
        assertEquals(SidePanelLayout.Part.ALL_OFF, layout.hit(layout.allOffButton().x() + 1, layout.allOffButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.COPY_TO_ALL, layout.hit(layout.copyButton().x() + 1, layout.copyButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.RESET, layout.hit(layout.resetButton().x() + 1, layout.resetButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.NOTHING, layout.hit(0, 0).part());
        assertEquals(SidePanelLayout.Part.NOTHING, layout.hit(-5, 500).part());
    }

    @Test
    void theCubeIsUnfoldedTheWayThePlanDraws() {
        SidePanelLayout layout = new SidePanelLayout(1);
        MachineLayout.Rect front = layout.face(FaceLabel.FRONT);
        assertEquals(front.y(), layout.face(FaceLabel.LEFT).y());
        assertEquals(front.y(), layout.face(FaceLabel.RIGHT).y());
        assertEquals(front.y(), layout.face(FaceLabel.BACK).y());
        assertTrue(layout.face(FaceLabel.LEFT).x() < front.x());
        assertTrue(layout.face(FaceLabel.RIGHT).x() > front.x());
        assertTrue(layout.face(FaceLabel.BACK).x() > layout.face(FaceLabel.RIGHT).x());
        assertEquals(front.x(), layout.face(FaceLabel.TOP).x());
        assertTrue(layout.face(FaceLabel.TOP).y() < front.y());
        assertEquals(front.x(), layout.face(FaceLabel.BOTTOM).x());
        assertTrue(layout.face(FaceLabel.BOTTOM).y() > front.y());
    }

    // --- The glyph-only side-config window ---------------------------------------------------

    @Test
    void glyphWindowPartsFitInsideTheBorderAndDoNotOverlap() {
        for (int tabs = 1; tabs <= 5; tabs++) {
            GlyphSidePanelLayout layout = new GlyphSidePanelLayout(tabs);
            List<MachineLayout.Rect> all = layout.clickable();
            for (int i = 0; i < all.size(); i++) {
                MachineLayout.Rect a = all.get(i);
                // The raised border takes two pixels on every side.
                assertTrue(a.x() >= 2 && a.y() >= 2 && a.x() + a.width() <= GlyphSidePanelLayout.WIDTH - 2
                        && a.y() + a.height() <= GlyphSidePanelLayout.HEIGHT - 2, tabs + " tabs: part " + i + " leaves the window");
                for (int j = i + 1; j < all.size(); j++) {
                    assertFalse(a.overlaps(all.get(j)), tabs + " tabs: parts " + i + " and " + j + " overlap");
                }
            }
        }
    }

    @Test
    void glyphWindowMatchesTheMockup() {
        GlyphSidePanelLayout five = new GlyphSidePanelLayout(5);
        assertEquals(17, five.tabSize());
        assertEquals(new MachineLayout.Rect(5, 4, 17, 17), five.tab(0));
        assertEquals(15, five.cubeX());
        assertEquals(27, five.cubeY());
        assertEquals(new MachineLayout.Rect(37, 27, 21, 21), five.face(FaceLabel.TOP));
        assertEquals(new MachineLayout.Rect(15, 49, 21, 21), five.face(FaceLabel.LEFT));
        assertEquals(new MachineLayout.Rect(59, 71, 21, 21), five.face(FaceLabel.BACK));
        assertEquals(new MachineLayout.Rect(11, 98, 22, 22), five.allOffButton());
        assertEquals(new MachineLayout.Rect(37, 98, 22, 22), five.copyButton());
        assertEquals(new MachineLayout.Rect(63, 98, 22, 22), five.resetButton());

        // Two tabs keep the five-tab size and sit in the middle, so nothing else moves.
        GlyphSidePanelLayout two = new GlyphSidePanelLayout(2);
        assertEquals(new MachineLayout.Rect(31, 4, 17, 17), two.tab(0));
        assertEquals(five.face(FaceLabel.FRONT), two.face(FaceLabel.FRONT));
    }

    @Test
    void glyphWindowPutsBackUnderRight() {
        GlyphSidePanelLayout layout = new GlyphSidePanelLayout(2);
        MachineLayout.Rect front = layout.face(FaceLabel.FRONT);
        MachineLayout.Rect right = layout.face(FaceLabel.RIGHT);
        MachineLayout.Rect bottom = layout.face(FaceLabel.BOTTOM);
        MachineLayout.Rect back = layout.face(FaceLabel.BACK);
        assertEquals(front.y(), layout.face(FaceLabel.LEFT).y());
        assertEquals(front.y(), right.y());
        assertEquals(front.x(), layout.face(FaceLabel.TOP).x());
        assertEquals(front.x(), bottom.x());
        assertEquals(right.x(), back.x());
        assertEquals(bottom.y(), back.y());
    }

    @Test
    void glyphWindowClicksLandWhereThePictureSaysTheyDo() {
        GlyphSidePanelLayout layout = new GlyphSidePanelLayout(2);
        for (FaceLabel label : FaceLabel.values()) {
            MachineLayout.Rect rect = layout.face(label);
            SidePanelLayout.Hit hit = layout.hit(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0);
            assertEquals(SidePanelLayout.Part.FACE, hit.part());
            assertEquals(label.ordinal(), hit.index());
        }
        for (int i = 0; i < 2; i++) {
            MachineLayout.Rect rect = layout.tab(i);
            SidePanelLayout.Hit hit = layout.hit(rect.x() + rect.width() - 1, rect.y() + 1);
            assertEquals(SidePanelLayout.Part.TAB, hit.part());
            assertEquals(i, hit.index());
        }
        assertEquals(SidePanelLayout.Part.ALL_OFF, layout.hit(layout.allOffButton().x() + 1, layout.allOffButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.COPY_TO_ALL, layout.hit(layout.copyButton().x() + 1, layout.copyButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.RESET, layout.hit(layout.resetButton().x() + 1, layout.resetButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.AUTO_IMPORT, layout.hit(layout.autoImportButton().x() + 1, layout.autoImportButton().y() + 1).part());
        assertEquals(SidePanelLayout.Part.AUTO_EXPORT, layout.hit(layout.autoExportButton().x() + 1, layout.autoExportButton().y() + 1).part());
        // The gap between two faces belongs to nothing.
        MachineLayout.Rect front = layout.face(FaceLabel.FRONT);
        assertEquals(SidePanelLayout.Part.NOTHING, layout.hit(front.x() + front.width(), front.y() + 5).part());
        assertEquals(SidePanelLayout.Part.NOTHING, layout.hit(0, 0).part());
        assertEquals(SidePanelLayout.Part.NOTHING, layout.hit(-5, 500).part());
    }
}
