package com.price.query.controller;

import com.price.query.dto.HistoryResponse;
import com.price.query.service.HistoryService;
import com.price.query.storage.Candle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long from,
            @RequestParam long to) {
        try {
            List<Candle> candles = historyService.getCandles(symbol, interval, from * 1000, to * 1000);
            HistoryResponse response = buildHistoryResponse(candles);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(HistoryResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(HistoryResponse.error("Internal server error"));
        }
    }

    private HistoryResponse buildHistoryResponse(List<Candle> candles) {
        List<Long> times = new ArrayList<>();
        List<Double> opens = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        List<Double> volumes = new ArrayList<>();

        for (Candle candle : candles) {
            times.add(candle.time() / 1000); // Convert milliseconds to seconds
            opens.add(candle.open());
            highs.add(candle.high());
            lows.add(candle.low());
            closes.add(candle.close());
            volumes.add(candle.volume());
        }

        return HistoryResponse.success(times, opens, highs, lows, closes, volumes);
    }
}
