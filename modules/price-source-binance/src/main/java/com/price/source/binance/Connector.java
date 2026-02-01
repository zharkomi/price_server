package com.price.source.binance;

import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.price.common.config.Instrument;
import com.price.common.source.PriceEventHandler;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class Connector implements com.price.common.source.Connector {
    private static final Logger logger = LoggerFactory.getLogger(Connector.class);

    private static final int RECONNECT_DELAY_SECONDS = 5;

    private final Map<String, PriceEventHandler> handlersBySymbol = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-reconnect");
        t.setDaemon(true);
        return t;
    });

    private WebSocketStreamClient wsClient;
    private int connectionId;

    @Override
    public void start() {
        if (handlersBySymbol.isEmpty()) {
            logger.warn("No handlers registered, not starting WebSocket connection");
            return;
        }

        running.set(true);
        connect();
    }

    private void connect() {
        if (!running.get()) {
            logger.info("Connector is stopped, skipping connection attempt");
            return;
        }

        wsClient = new WebSocketStreamClientImpl();

        // Build the stream names for all registered symbols
        ArrayList<String> streamNames = new ArrayList<>();
        for (String symbol : handlersBySymbol.keySet()) {
            streamNames.add(symbol.toLowerCase() + "@bookTicker");
        }

        logger.info("Starting Binance WebSocket connection for streams: {}", streamNames);

        // Connect to the combined book ticker stream with callbacks
        connectionId = wsClient.combineStreams(
            streamNames,
            this::onOpen,
            this::onMessage,
            this::onClosing,
            this::onClosed,
            this::onFailure
        );

        logger.info("Binance WebSocket connection started with ID: {}", connectionId);
    }

    private void onOpen(okhttp3.Response response) {
        logger.info("Binance WebSocket connection opened: {}", response);
    }

    private void onClosing(int code, String reason) {
        logger.info("Binance WebSocket connection closing: code={}, reason={}", code, reason);
    }

    private void onClosed(int code, String reason) {
        logger.info("Binance WebSocket connection closed: code={}, reason={}", code, reason);
        scheduleReconnect();
    }

    private void onFailure(Throwable cause, okhttp3.Response response) {
        logger.error("Binance WebSocket connection failure: {}", response, cause);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            logger.info("Connector is stopped, not reconnecting");
            return;
        }

        logger.info("Scheduling reconnection in {} seconds", RECONNECT_DELAY_SECONDS);

        reconnectScheduler.schedule(() -> {
            try {
                connect();
            } catch (Exception e) {
                logger.error("Reconnection failed", e);
                scheduleReconnect();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void register(PriceEventHandler handler) {
        Instrument instrument = handler.getInstrument();
        String symbol = instrument.name();

        logger.info("Registering handler for symbol: {}", symbol);
        handlersBySymbol.put(symbol, handler);
    }

    private void onMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            // Handle combined stream format
            if (json.has("stream") && json.has("data")) {
                JSONObject data = json.getJSONObject("data");
                processBookTickerData(data);
            } else {
                // Handle single stream format
                processBookTickerData(json);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", message, e);
        }
    }

    private void processBookTickerData(JSONObject data) {
        try {
            String symbol = data.getString("s");
            String bidPriceStr = data.getString("b");
            String askPriceStr = data.getString("a");
            String bidQtyStr = data.getString("B");
            String askQtyStr = data.getString("A");

            double bidPrice = Double.parseDouble(bidPriceStr);
            double askPrice = Double.parseDouble(askPriceStr);
            double bidQty = Double.parseDouble(bidQtyStr);
            double askQty = Double.parseDouble(askQtyStr);

            double midPrice = (bidPrice + askPrice) / 2.0;
            long volume = (long) (bidQty + askQty);

            // bookTicker doesn't include event time field, use system time
            long timestamp = System.currentTimeMillis();

            PriceEventHandler handler = handlersBySymbol.get(symbol);
            if (handler != null) {
                handler.handlePriceEvent(timestamp, midPrice, volume);
                logger.debug("Processed book ticker for {}: bid={}, ask={}, mid={}, volume={}, eventTime={}",
                    symbol, bidPrice, askPrice, midPrice, volume, timestamp);
            } else {
                logger.warn("No handler found for symbol: {}", symbol);
            }
        } catch (Exception e) {
            logger.error("Error processing book ticker data: {}", data, e);
        }
    }

    @Override
    public void close() throws Exception {
        logger.info("Closing Binance WebSocket connection");
        running.set(false);
        reconnectScheduler.shutdownNow();
        if (wsClient != null) {
            wsClient.closeConnection(connectionId);
            wsClient.closeAllConnections();
        }
    }
}
