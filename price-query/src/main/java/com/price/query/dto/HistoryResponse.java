package com.price.query.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryResponse {
    @JsonProperty("s")
    private final String status;

    @JsonProperty("errmsg")
    private final String error;

    @JsonProperty("t")
    @Builder.Default
    private final List<Long> times = new ArrayList<>();

    @JsonProperty("o")
    @Builder.Default
    private final List<Double> opens = new ArrayList<>();

    @JsonProperty("h")
    @Builder.Default
    private final List<Double> highs = new ArrayList<>();

    @JsonProperty("l")
    @Builder.Default
    private final List<Double> lows = new ArrayList<>();

    @JsonProperty("c")
    @Builder.Default
    private final List<Double> closes = new ArrayList<>();

    @JsonProperty("v")
    @Builder.Default
    private final List<Double> volumes = new ArrayList<>();


    public static HistoryResponse success(List<Long> times, List<Double> opens, List<Double> highs,
                                          List<Double> lows, List<Double> closes, List<Double> volumes) {
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
                .error(errorMessage)
                .build();
    }
}
