package com.cgangel.screen_translate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoordinateMapperTest {
    @Test
    public void mapsLineFromCaptureBitmapToOverlayCoordinates() {
        OcrLine source = new OcrLine("line_1", "Hello", 100, 200, 300, 260);

        OcrLine mapped = CoordinateMapper.mapLine(source, 1000, 2000, 500, 1000);

        assertEquals(50, mapped.left);
        assertEquals(100, mapped.top);
        assertEquals(150, mapped.right);
        assertEquals(130, mapped.bottom);
        assertEquals(100, mapped.top);
        assertEquals(50, mapped.left);
    }

    @Test
    public void scalesLineBackAfterDownsampledOcr() {
        OcrLine source = new OcrLine("line_1", "Hello", 50, 100, 150, 130);

        OcrLine scaled = CoordinateMapper.scaleLine(source, 2.0f, 2.0f);

        assertEquals(100, scaled.left);
        assertEquals(200, scaled.top);
        assertEquals(300, scaled.right);
        assertEquals(260, scaled.bottom);
    }
}
