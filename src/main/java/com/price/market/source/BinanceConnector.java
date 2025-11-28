package com.price.market.source;

import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.price.market.Connector;
import com.price.market.Instrument;
import com.price.market.MarketDataEvent;
import com.price.market.MarketDataProcessor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BinanceConnector implements Connector {
    private static final Logger logger = LoggerFactory.getLogger(BinanceConnector.class);

    private final Map<String, MarketDataProcessor> processorsBySymbol = new ConcurrentHashMap<>();
    private WebSocketStreamClient wsClient;
    private int connectionId;

    @Override
    public void start() {
        if (processorsBySymbol.isEmpty()) {
            logger.warn("No processors registered, not starting WebSocket connection");
            return;
        }

        wsClient = new WebSocketStreamClientImpl();

        // Build the stream names for all registered symbols
        ArrayList<String> streamNames = new ArrayList<>();
        for (String symbol : processorsBySymbol.keySet()) {
            streamNames.add(symbol.toLowerCase() + "@bookTicker");
        }

        logger.info("Starting Binance WebSocket connection for streams: {}", streamNames);

        // Connect to the combined book ticker stream
        connectionId = wsClient.combineStreams(streamNames, this::onMessage);

        logger.info("Binance WebSocket connection started with ID: {}", connectionId);
    }

    @Override
    public void register(MarketDataProcessor mdp) {
        Instrument instrument = mdp.instrument;
        String symbol = instrument.name();

        logger.info("Registering processor for symbol: {}", symbol);
        processorsBySymbol.put(symbol, mdp);
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

            float bidPrice = Float.parseFloat(bidPriceStr);
            float askPrice = Float.parseFloat(askPriceStr);
            float midPrice = (bidPrice + askPrice) / 2.0f;

            long timestamp = System.currentTimeMillis();

            MarketDataProcessor processor = processorsBySymbol.get(symbol);
            if (processor != null) {
                MarketDataEvent event = new MarketDataEvent(timestamp, midPrice, 0.0f);
                processor.handleEvent(event);

                logger.debug("Processed book ticker for {}: bid={}, ask={}, mid={}",
                    symbol, bidPrice, askPrice, midPrice);
            } else {
                logger.warn("No processor found for symbol: {}", symbol);
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
