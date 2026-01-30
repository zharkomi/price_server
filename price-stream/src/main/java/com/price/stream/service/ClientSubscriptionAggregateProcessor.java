package com.price.stream.service;

import com.price.common.db.CandleEvent;
import com.price.stream.market.MarketDataAggregateHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientSubscriptionAggregateProcessor extends ClientSubscriptionProcessor {

    private final StringBuilder stringBuilder = new StringBuilder();
    private final MarketDataAggregateHandler marketDataHandler;

    public ClientSubscriptionAggregateProcessor(SocketChannel channel, MarketDataAggregateHandler marketDataHandler, int clientBufferSize) {
        super(channel, marketDataHandler, clientBufferSize);
        this.marketDataHandler = marketDataHandler;
    }

    public void timeFrameProcessed() {
        log.debug("Timeframe processed");
        long sequence = ringBuffer.next();
        try {
            CandleEvent event = ringBuffer.get(sequence);
            event.endOfBatch(true);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    protected void sendEvent(CandleEvent event, long sequence, boolean endOfBatch) {
        if (event.endOfBatch()) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(']');
                channel.writeAndFlush(new TextWebSocketFrame(stringBuilder.toString()));
                stringBuilder.setLength(0);
            }
            return;
        }

        try {
            String json = MAPPER.writeValueAsString(event);
            if (stringBuilder.isEmpty()) {
                stringBuilder.append('[');
            } else {
                stringBuilder.append(',');
            }
            stringBuilder.append(json);
        } catch (Exception e) {
            log.error("Error serializing candle event", e);
        }
    }

    public synchronized void start() {
        super.start();
        this.marketDataHandler.register(this);
    }

    public synchronized void stop() {
        marketDataHandler.deregister(this);
        super.stop();
    }
}
