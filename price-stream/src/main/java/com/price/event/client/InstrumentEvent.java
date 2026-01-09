package com.price.event.client;

public record InstrumentEvent(String i, long t, long f, double o, double h, double l, double c, double v) {
}
