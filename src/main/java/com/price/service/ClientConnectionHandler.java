package com.price.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.price.event.client.SubscriptionEvent;
import com.price.market.MarketDataProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ClientConnectionHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChannelGroup allChannels;
    private final SubscriptionProcessor subscriptionProcessor;

    public ClientConnectionHandler(SocketChannel channel, ChannelGroup allChannels, Map<String, MarketDataProcessor> marketDataProcessorMap) {
        this.allChannels = allChannels;
        this.subscriptionProcessor = new SubscriptionProcessor(channel, marketDataProcessorMap);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        allChannels.add(ctx.channel());
        subscriptionProcessor.start();
        log.debug("Client connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        allChannels.remove(ctx.channel());
        subscriptionProcessor.stop();
        log.debug("Client disconnected: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
            return;
        }

        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        if (frame instanceof TextWebSocketFrame textFrame) {
            handleTextMessage(ctx, textFrame.text());
            return;
        }

        throw new UnsupportedOperationException("Unsupported frame type: " + frame.getClass().getName());
    }

    private void handleTextMessage(ChannelHandlerContext ctx, String text) throws Exception {
        SubscriptionEvent event = objectMapper.readValue(text, SubscriptionEvent.class);
        if (event.getType() == SubscriptionEvent.Type.SUBSCRIBE) {
            subscriptionProcessor.subscribe(event.getInstrument(), event.getTimeframe());
        } else if (event.getType() == SubscriptionEvent.Type.UNSUBSCRIBE) {
            subscriptionProcessor.unsubscribe(event.getInstrument(), event.getTimeframe());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error for client {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}