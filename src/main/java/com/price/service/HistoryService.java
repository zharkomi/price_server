package com.price.service;

import com.price.common.Util;
import com.price.event.CandleEvent;
import com.price.storage.Repository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

@Slf4j
public class HistoryService implements AutoCloseable {
    private final Repository repository;
    private final Server server;
    private static final int DEFAULT_PORT = 8080;

    public HistoryService(Repository repository) {
        this.repository = repository;
        this.server = new Server(DEFAULT_PORT);
        this.server.setHandler(new HistoryHandler());
        log.info("Jetty server created on port {}", DEFAULT_PORT);
    }

    public void start() {
        try {
            server.start();
            log.info("HistoryService HTTP server started on port {}", DEFAULT_PORT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Jetty server", e);
        }
    }

    private class HistoryHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            try {
                switch (target) {
                    case "/history" -> handleHistory(request, response);
                    case "/health" -> handleHealth(response);
                    default -> sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Not found");
                }
            } catch (Exception e) {
                log.error("Error handling request for {}", target, e);
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            }

            baseRequest.setHandled(true);
        }

        private void handleHealth(HttpServletResponse response) throws IOException {
            JSONObject healthResponse = new JSONObject();
            healthResponse.put("status", "ok");
            healthResponse.put("service", "HistoryService");
            healthResponse.put("timestamp", System.currentTimeMillis());
            sendJsonResponse(response, HttpServletResponse.SC_OK, healthResponse.toString());
            log.debug("Health check request handled");
        }

        private void handleHistory(HttpServletRequest request, HttpServletResponse response) throws Exception {
            try {
                // Parse and validate request
                HistoryRequest historyRequest = parseAndValidateRequest(request);

                // Query candles from repository
                log.info("Querying candles: symbol={}, timeframeMs={}, from={}, to={}",
                        historyRequest.getSymbol(), historyRequest.getTimeframeMs(),
                        historyRequest.getFromTimestamp(), historyRequest.getToTimestamp());
                List<CandleEvent> candles = repository.queryCandles(
                        historyRequest.getSymbol(),
                        historyRequest.getTimeframeMs(),
                        historyRequest.getFromTimestamp(),
                        historyRequest.getToTimestamp());
                log.info("Query returned {} candles", candles.size());

                // Build response DTO
                HistoryResponse historyResponse = buildHistoryResponse(candles);

                // Send response
                sendJsonResponse(response, HttpServletResponse.SC_OK, historyResponse.toJson());

                log.info("Handled history request for {} {} from {} to {}, returned {} candles",
                        historyRequest.getSymbol(), historyRequest.getInterval(),
                        historyRequest.getFromTimestamp(), historyRequest.getToTimestamp(), candles.size());

            } catch (IllegalArgumentException e) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            }
        }

        private HistoryRequest parseAndValidateRequest(HttpServletRequest request) {
            // Get query parameters
            String symbol = request.getParameter("symbol");
            String interval = request.getParameter("interval");
            String fromStr = request.getParameter("from");
            String toStr = request.getParameter("to");

            // Check required parameters
            if (symbol == null || interval == null || fromStr == null || toStr == null) {
                throw new IllegalArgumentException("Missing required parameters: symbol, interval, from, to");
            }

            // Validate symbol is not empty
            if (symbol.trim().isEmpty()) {
                throw new IllegalArgumentException("Symbol cannot be empty");
            }

            // Validate interval is not empty
            if (interval.trim().isEmpty()) {
                throw new IllegalArgumentException("Interval cannot be empty");
            }

            // Parse and validate timestamps
            long fromTimestamp;
            long toTimestamp;
            try {
                fromTimestamp = Long.parseLong(fromStr) * 1000; // Convert seconds to milliseconds
                toTimestamp = Long.parseLong(toStr) * 1000;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid timestamp format");
            }

            if (fromTimestamp <= 0) {
                throw new IllegalArgumentException("From timestamp must be positive");
            }

            if (toTimestamp <= 0) {
                throw new IllegalArgumentException("To timestamp must be positive");
            }

            if (fromTimestamp >= toTimestamp) {
                throw new IllegalArgumentException("From timestamp must be before to timestamp");
            }

            // Parse and validate interval
            int timeframeMs;
            try {
                timeframeMs = Util.parseTimeframeToMilliseconds(interval);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid interval format: " + e.getMessage());
            }

            if (timeframeMs <= 0) {
                throw new IllegalArgumentException("Invalid interval format");
            }

            return HistoryRequest.of(symbol, interval, fromTimestamp, toTimestamp, timeframeMs);
        }
    }

    private HistoryResponse buildHistoryResponse(List<CandleEvent> candles) {
        List<Long> times = new ArrayList<>();
        List<Double> opens = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();

        for (CandleEvent candle : candles) {
            times.add(candle.time() / 1000); // Convert milliseconds to seconds
            opens.add(candle.open());
            highs.add(candle.high());
            lows.add(candle.low());
            closes.add(candle.close());
            volumes.add(candle.volume());
        }

        return HistoryResponse.success(times, opens, highs, lows, closes, volumes);
    }

    private void sendJsonResponse(HttpServletResponse response, int statusCode, String json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        response.getWriter().write(json);
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        HistoryResponse errorResponse = HistoryResponse.error(message);
        sendJsonResponse(response, statusCode, errorResponse.toJson());
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down HistoryService HTTP server");
        server.stop();
        log.info("HistoryService HTTP server stopped");
    }
}
