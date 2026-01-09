package com.price.service;

import com.price.common.config.Configuration;
import com.price.market.MarketDataProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class StreamService implements AutoCloseable {
    private static final String WEBSOCKET_PATH = "/stream";

    private final int port;
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final Map<String, MarketDataProcessor> marketDataProcessorMap;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public StreamService(Configuration configuration, Map<String, MarketDataProcessor> marketDataProcessorMap) {
        this.port = configuration.httpPort() + 1;
        this.marketDataProcessorMap = marketDataProcessorMap;
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(new WebSocketServerCompressionHandler());
                            pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true));
                            pipeline.addLast(new ClientConnectionHandler(channel, allChannels, marketDataProcessorMap));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            serverChannel = bootstrap.bind(port).sync().channel();
            log.info("StreamService WebSocket server started on port {}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to start WebSocket server", e);
        }
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down StreamService WebSocket server");

        allChannels.close().awaitUninterruptibly();

        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().sync();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().sync();
        }

        log.info("StreamService WebSocket server stopped");
    }
}