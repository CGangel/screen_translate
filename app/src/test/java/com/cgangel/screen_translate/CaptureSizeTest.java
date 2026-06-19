package com.cgangel.screen_translate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CaptureSizeTest {
    @Test
    public void keepsSmallCaptureSizeUnchanged() {
        CaptureSize size = CaptureSize.fitWithin(1280, 720, 1600);

        assertEquals(1280, size.width);
        assertEquals(720, size.height);
    }

    @Test
    public void scalesLargeLandscapeCaptureByLongEdge() {
        CaptureSize size = CaptureSize.fitWithin(3840, 2160, 1600);

        assertEquals(1600, size.width);
        assertEquals(900, size.height);
    }

    @Test
    public void scalesLargePortraitCaptureByLongEdge() {
        CaptureSize size = CaptureSize.fitWithin(1440, 3200, 1600);

        assertEquals(720, size.width);
        assertEquals(1600, size.height);
    }
}
