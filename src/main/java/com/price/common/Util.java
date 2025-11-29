package com.price.common;

public class Util {
    public static int parseTimeframeToMilliseconds(String timeframe) {
        if (timeframe.isEmpty()) {
            throw new IllegalArgumentException("Empty time value");
        }

        // Extract number and unit (e.g., "1m" -> number=1, unit="m")
        int numberEnd = 0;
        while (numberEnd < timeframe.length() && Character.isDigit(timeframe.charAt(numberEnd))) {
            numberEnd++;
        }

        if (numberEnd == 0) {
            throw new IllegalArgumentException("Invalid time format: " + timeframe);
        }

        int value = Integer.parseInt(timeframe.substring(0, numberEnd));
        String unit = timeframe.substring(numberEnd).toLowerCase();

        return 1000 * switch (unit) {
            case "s" -> value;
            case "m" -> value * 60;
            case "h" -> value * 3600;
            case "d" -> value * 86400;
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit +
                    ". Supported units: s, m, h, d");
        };
    }
}
