package com.cgangel.screen_translate;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OcrLineMergerTest {
    @Test
    public void mergeAndSortRemovesOverlappingDuplicatesAndAssignsIds() {
        List<OcrLine> merged = OcrLineMerger.mergeAndSort(Arrays.asList(
                new OcrLine("", "Second", 20, 120, 180, 150),
                new OcrLine("", "First", 10, 20, 120, 45),
                new OcrLine("", "First", 12, 21, 122, 46),
                new OcrLine("", "  ", 0, 0, 10, 10)
        ));

        assertEquals(2, merged.size());
        assertEquals("line_1", merged.get(0).id);
        assertEquals("First", merged.get(0).text);
        assertEquals("line_2", merged.get(1).id);
        assertEquals("Second", merged.get(1).text);
    }

    @Test
    public void mergePrefersJapaneseTextOverLongLatinNoise() {
        List<OcrLine> merged = OcrLineMerger.mergeAndSort(Arrays.asList(
                new OcrLine("", "Nihongo", 10, 20, 130, 48),
                new OcrLine("", "\u65e5\u672c\u8a9e", 11, 20, 131, 48)
        ));

        assertEquals(1, merged.size());
        assertEquals("\u65e5\u672c\u8a9e", merged.get(0).text);
    }

    @Test
    public void mergeDoesNotReplaceEnglishWithSingleCjkFalsePositive() {
        List<OcrLine> merged = OcrLineMerger.mergeAndSort(Arrays.asList(
                new OcrLine("", "Settings", 10, 20, 150, 48),
                new OcrLine("", "\u8a2d", 11, 20, 151, 48)
        ));

        assertEquals(1, merged.size());
        assertEquals("Settings", merged.get(0).text);
    }
    @Test
    public void mergeNearbyLinesCombinesDialogueBlockAndResetsIds() {
        List<OcrLine> blocks = OcrLineMerger.mergeNearbyLines(Arrays.asList(
                new OcrLine("line_1", "Hello", 10, 20, 120, 45),
                new OcrLine("line_2", "world", 12, 50, 130, 75),
                new OcrLine("line_3", "Separate", 10, 140, 150, 165)
        ));

        assertEquals(2, blocks.size());
        assertEquals("line_1", blocks.get(0).id);
        assertEquals("Hello\nworld", blocks.get(0).text);
        assertEquals(10, blocks.get(0).left);
        assertEquals(20, blocks.get(0).top);
        assertEquals(130, blocks.get(0).right);
        assertEquals(75, blocks.get(0).bottom);
        assertEquals("line_2", blocks.get(1).id);
        assertEquals("Separate", blocks.get(1).text);
    }

    @Test
    public void mergeNearbyLinesKeepsDistantLinesSeparate() {
        List<OcrLine> blocks = OcrLineMerger.mergeNearbyLines(Arrays.asList(
                new OcrLine("line_1", "Top", 10, 20, 120, 45),
                new OcrLine("line_2", "Bottom", 12, 100, 130, 125)
        ));

        assertEquals(2, blocks.size());
        assertEquals("Top", blocks.get(0).text);
        assertEquals("Bottom", blocks.get(1).text);
    }
}
