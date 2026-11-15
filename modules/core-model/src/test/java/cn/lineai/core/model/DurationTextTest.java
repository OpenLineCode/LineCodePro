package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DurationTextTest {

    @Test public void formatsSubMinuteDurations() {
        assertEquals("0s", DurationText.format(0));
        assertEquals("0.8s", DurationText.format(800));
        assertEquals("8.4s", DurationText.format(8400));
        assertEquals("12.3s", DurationText.format(12300));
    }

    @Test public void formatsMinuteScaleDurations() {
        assertEquals("1m 0s", DurationText.format(60000));
        assertEquals("1m 5s", DurationText.format(65000));
        assertEquals("59m 59s", DurationText.format(3599000));
    }

    @Test public void formatsHourScaleDurations() {
        assertEquals("1h", DurationText.format(3600000));
        assertEquals("1h 2m", DurationText.format(3720000));
    }

    @Test public void clampsNegativeDurations() {
        assertEquals("0s", DurationText.format(-100));
    }
}
