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

@Service
public class Connector implements com.price.common.source.Connector {
    private static final Logger logger = LoggerFactory.getLogger(Connector.class);

    private final Map<String, PriceEventHandler> handlersBySymbol = new ConcurrentHashMap<>();
    private WebSocketStreamClient wsClient;
    private int connectionId;

    @Override
    public void start() {
        if (handlersBySymbol.isEmpty()) {
            logger.warn("No handlers registered, not starting WebSocket connection");
            return;
        }

        wsClient = new WebSocketStreamClientImpl();

        // Build the stream names for all registered symbols
        ArrayList<String> streamNames = new ArrayList<>();
        for (String symbol : handlersBySymbol.keySet()) {
            streamNames.add(symbol.toLowerCase() + "@bookTicker");
        }

        logger.info("Starting Binance WebSocket connection for streams: {}", streamNames);

        // Connect to the combined book ticker stream
        connectionId = wsClient.combineStreams(streamNames, this::onMessage);

        logger.info("Binance WebSocket connection started with ID: {}", connectionId);
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
        if (wsClient != null) {
            wsClient.closeConnection(connectionId);
            wsClient.closeAllConnections();
        }
    }
}
