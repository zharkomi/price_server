package com.price.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HistoryResponse {
    private final String status;
    @Builder.Default
    private final List<Long> times = new ArrayList<>();
    @Builder.Default
    private final List<Float> opens = new ArrayList<>();
    @Builder.Default
    private final List<Float> highs = new ArrayList<>();
    @Builder.Default
    private final List<Float> lows = new ArrayList<>();
    @Builder.Default
    private final List<Float> closes = new ArrayList<>();
    @Builder.Default
    private final List<Integer> volumes = new ArrayList<>();
    private final String errorMessage;

    public static HistoryResponse success(List<Long> times, List<Float> opens, List<Float> highs,
                                          List<Float> lows, List<Float> closes, List<Integer> volumes) {
        return HistoryResponse.builder()
                .status("ok")
                .times(times)
                .opens(opens)
                .highs(highs)
                .lows(lows)
                .closes(closes)
                .volumes(volumes)
                .build();
    }

    public static HistoryResponse error(String errorMessage) {
        return HistoryResponse.builder()
                .status("error")
                .errorMessage(errorMessage)
                .build();
    }

    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("s", status);

        if ("ok".equals(status)) {
            json.put("t", times);
            json.put("o", opens);
            json.put("h", highs);
            json.put("l", lows);
            json.put("c", closes);
            json.put("v", volumes);
        } else {
            json.put("errmsg", errorMessage);
        }

        return json.toString();
    }
}
