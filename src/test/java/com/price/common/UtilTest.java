package com.price.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    @Test
    void testParseSecondsTimeframe() {
        assertEquals(1000, Util.parseTimeframeToMilliseconds("1s"));
        assertEquals(5000, Util.parseTimeframeToMilliseconds("5s"));
        assertEquals(30000, Util.parseTimeframeToMilliseconds("30s"));
        assertEquals(45000, Util.parseTimeframeToMilliseconds("45s"));
    }

    @Test
    void testParseMinutesTimeframe() {
        assertEquals(60000, Util.parseTimeframeToMilliseconds("1m"));
        assertEquals(300000, Util.parseTimeframeToMilliseconds("5m"));
        assertEquals(900000, Util.parseTimeframeToMilliseconds("15m"));
        assertEquals(1800000, Util.parseTimeframeToMilliseconds("30m"));
    }

    @Test
    void testParseHoursTimeframe() {
        assertEquals(3600000, Util.parseTimeframeToMilliseconds("1h"));
        assertEquals(14400000, Util.parseTimeframeToMilliseconds("4h"));
        assertEquals(21600000, Util.parseTimeframeToMilliseconds("6h"));
        assertEquals(43200000, Util.parseTimeframeToMilliseconds("12h"));
    }

    @Test
    void testParseDaysTimeframe() {
        assertEquals(86400000, Util.parseTimeframeToMilliseconds("1d"));
        assertEquals(259200000, Util.parseTimeframeToMilliseconds("3d"));
        assertEquals(604800000, Util.parseTimeframeToMilliseconds("7d"));
    }

    @Test
    void testParseCaseInsensitive() {
        assertEquals(60000, Util.parseTimeframeToMilliseconds("1M"));
        assertEquals(60000, Util.parseTimeframeToMilliseconds("1m"));
        assertEquals(3600000, Util.parseTimeframeToMilliseconds("1H"));
        assertEquals(3600000, Util.parseTimeframeToMilliseconds("1h"));
        assertEquals(1000, Util.parseTimeframeToMilliseconds("1S"));
        assertEquals(86400000, Util.parseTimeframeToMilliseconds("1D"));
    }

    @Test
    void testParseMultiDigitNumbers() {
        assertEquals(10000, Util.parseTimeframeToMilliseconds("10s"));
        assertEquals(1500000, Util.parseTimeframeToMilliseconds("25m"));
        assertEquals(36000000, Util.parseTimeframeToMilliseconds("10h"));
        assertEquals(864000000, Util.parseTimeframeToMilliseconds("10d"));
    }

    @Test
    void testEmptyStringThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Util.parseTimeframeToMilliseconds("")
        );
        assertEquals("Empty time value", exception.getMessage());
    }

    @Test
    void testInvalidFormatNoNumber() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Util.parseTimeframeToMilliseconds("m")
        );
        assertEquals("Invalid time format: m", exception.getMessage());
    }

    @Test
    void testInvalidFormatNoUnit() {
        // This will parse the number but return -1000 due to invalid unit (empty string)
        assertEquals(-1000, Util.parseTimeframeToMilliseconds("10"));
    }

    @Test
    void testInvalidUnit() {
        // Invalid units should return negative value (-1 * 1000)
        assertEquals(-1000, Util.parseTimeframeToMilliseconds("1x"));
        assertEquals(-1000, Util.parseTimeframeToMilliseconds("5w"));
        assertEquals(-1000, Util.parseTimeframeToMilliseconds("10y"));
    }

    @Test
    void testZeroValue() {
        assertEquals(0, Util.parseTimeframeToMilliseconds("0s"));
        assertEquals(0, Util.parseTimeframeToMilliseconds("0m"));
        assertEquals(0, Util.parseTimeframeToMilliseconds("0h"));
        assertEquals(0, Util.parseTimeframeToMilliseconds("0d"));
    }

    @Test
    void testLargeValues() {
        assertEquals(100000000, Util.parseTimeframeToMilliseconds("100000s"));
        // Note: Very large values may cause integer overflow since method returns int
        // Maximum safe value for minutes: ~35791m (Integer.MAX_VALUE / 60000)
        assertEquals(1200000000, Util.parseTimeframeToMilliseconds("20000m"));
    }

    @Test
    void testCommonTradingTimeframes() {
        // Common crypto trading timeframes
        assertEquals(60000, Util.parseTimeframeToMilliseconds("1m"));
        assertEquals(180000, Util.parseTimeframeToMilliseconds("3m"));
        assertEquals(300000, Util.parseTimeframeToMilliseconds("5m"));
        assertEquals(900000, Util.parseTimeframeToMilliseconds("15m"));
        assertEquals(1800000, Util.parseTimeframeToMilliseconds("30m"));
        assertEquals(3600000, Util.parseTimeframeToMilliseconds("1h"));
        assertEquals(7200000, Util.parseTimeframeToMilliseconds("2h"));
        assertEquals(14400000, Util.parseTimeframeToMilliseconds("4h"));
        assertEquals(21600000, Util.parseTimeframeToMilliseconds("6h"));
        assertEquals(28800000, Util.parseTimeframeToMilliseconds("8h"));
        assertEquals(43200000, Util.parseTimeframeToMilliseconds("12h"));
        assertEquals(86400000, Util.parseTimeframeToMilliseconds("1d"));
        assertEquals(259200000, Util.parseTimeframeToMilliseconds("3d"));
        assertEquals(604800000, Util.parseTimeframeToMilliseconds("7d"));
    }
}
