package com.price.service.dto;

import lombok.Builder;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

@Getter
@Builder
public class HealthResponse {
    private final String status;
    private final String service;
    private final long timestamp;
    private final List<InstrumentInfo> instruments;

    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("status", status);
        json.put("service", service);
        json.put("timestamp", timestamp);

        JSONArray instrumentsArray = new JSONArray();
        for (InstrumentInfo instrument : instruments) {
            instrumentsArray.put(instrument.toJson());
        }
        json.put("instruments", instrumentsArray);

        return json.toString();
    }
}
