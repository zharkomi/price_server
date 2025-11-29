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
                    default -> sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Not found");
                }
            } catch (Exception e) {
                log.error("Error handling request for {}", target, e);
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            }

            baseRequest.setHandled(true);
        }

        private void handleHistory(HttpServletRequest request, HttpServletResponse response) throws Exception {
            try {
                // Get query parameters
                String symbol = request.getParameter("symbol");
                String interval = request.getParameter("interval");
                String fromStr = request.getParameter("from");
                String toStr = request.getParameter("to");

                if (symbol == null || interval == null || fromStr == null || toStr == null) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                            "Missing required parameters: symbol, interval, from, to");
                    return;
                }

                // Parse timestamps
                long fromTimestamp = Long.parseLong(fromStr) * 1000; // Convert seconds to milliseconds
                long toTimestamp = Long.parseLong(toStr) * 1000;

                // Parse interval to milliseconds
                int timeframeMs = Util.parseTimeframeToMilliseconds(interval);

                // Query candles from repository
                log.info("Querying candles: symbol={}, timeframeMs={}, from={}, to={}",
                        symbol, timeframeMs, fromTimestamp, toTimestamp);
                List<CandleEvent> candles = repository.queryCandles(symbol, timeframeMs, fromTimestamp, toTimestamp);
                log.info("Query returned {} candles", candles.size());

                // Build response DTO
                HistoryResponse historyResponse = buildHistoryResponse(candles);

                // Send response
                sendJsonResponse(response, HttpServletResponse.SC_OK, historyResponse.toJson());

                log.info("Handled history request for {} {} from {} to {}, returned {} candles",
                        symbol, interval, fromTimestamp, toTimestamp, candles.size());

            } catch (NumberFormatException e) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp format");
            } catch (IllegalArgumentException e) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid interval format: " + e.getMessage());
            }
        }
    }

    private HistoryResponse buildHistoryResponse(List<CandleEvent> candles) {
        List<Long> times = new ArrayList<>();
        List<Float> opens = new ArrayList<>();
        List<Float> highs = new ArrayList<>();
        List<Float> lows = new ArrayList<>();
        List<Float> closes = new ArrayList<>();
        List<Integer> volumes = new ArrayList<>();

        for (CandleEvent candle : candles) {
            times.add(candle.time() / 1000); // Convert milliseconds to seconds
            opens.add(candle.open());
            highs.add(candle.high());
            lows.add(candle.low());
            closes.add(candle.close());
            volumes.add((int) candle.volume());
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
