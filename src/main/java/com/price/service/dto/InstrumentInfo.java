package com.price.service.dto;

import com.price.common.config.Instrument;
import lombok.Builder;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

@Getter
@Builder
public class InstrumentInfo {
    private final String name;
    private final String source;
    private final String fullName;
    private final int[] timeframes;
    private final long marketEvents;
    private final long candleEvents;

    public static InstrumentInfo from(Instrument instrument) {
        return InstrumentInfo.builder()
                .name(instrument.name())
                .source(instrument.source().name())
                .fullName(instrument.fullName())
                .timeframes(instrument.timeframes())
                .marketEvents(instrument.marketEvents().get())
                .candleEvents(instrument.candlesEvents().get())
                .build();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("source", source);
        json.put("fullName", fullName);

        JSONArray timeframesArray = new JSONArray();
        for (int timeframe : timeframes) {
            timeframesArray.put(timeframe);
        }
        json.put("timeframes", timeframesArray);

        json.put("marketEvents", marketEvents);
        json.put("candleEvents", candleEvents);

        return json;
    }
}
