package com.cgangel.screen_translate;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OcrLineFilterTest {
    @Test
    public void excludesFloatingButtonTextInsideButtonBounds() {
        OcrLine exclusion = new OcrLine("button", "button", 900, 200, 980, 280);
        List<OcrLine> filtered = OcrLineFilter.excludeOverlayLines(
                Arrays.asList(
                        new OcrLine("line_1", "\u8bd1", 925, 222, 950, 252),
                        new OcrLine("line_2", "Hello", 100, 100, 220, 130)
                ),
                exclusion,
                "\u8bd1"
        );

        assertEquals(1, filtered.size());
        assertEquals("Hello", filtered.get(0).text);
    }

    @Test
    public void keepsSameTextOutsideButtonBounds() {
        OcrLine exclusion = new OcrLine("button", "button", 900, 200, 980, 280);
        List<OcrLine> filtered = OcrLineFilter.excludeOverlayLines(
                Arrays.asList(
                        new OcrLine("line_1", "\u8bd1", 100, 100, 125, 130)
                ),
                exclusion,
                "\u8bd1"
        );

        assertEquals(1, filtered.size());
        assertEquals("\u8bd1", filtered.get(0).text);
    }

    @Test
    public void excludesOriginalButtonTextInsideSecondButtonBounds() {
        OcrLine translateExclusion = new OcrLine("translate", "button", 900, 200, 980, 280);
        OcrLine originalExclusion = new OcrLine("original", "button", 900, 286, 980, 366);
        List<OcrLine> filtered = OcrLineFilter.excludeOverlayLines(
                Arrays.asList(
                        new OcrLine("line_1", "\u539f", 924, 310, 950, 340),
                        new OcrLine("line_2", "Game Text", 100, 100, 220, 130)
                ),
                Arrays.asList(translateExclusion, originalExclusion),
                "\u8bd1"
        );

        assertEquals(1, filtered.size());
        assertEquals("Game Text", filtered.get(0).text);
    }
}
