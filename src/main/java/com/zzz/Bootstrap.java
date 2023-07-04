package com.zzz;

import com.zzz.rpc.client.Call;
import com.zzz.config.ElectConfig;
import com.zzz.rpc.MessageCodec;
import com.zzz.rpc.server.RequestInboundHandler;
import com.zzz.rpc.health.HealthBeatHandler;
import com.zzz.log.LogStorage;
import com.zzz.net.Cluster;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class Bootstrap {

    public static void main() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
//            String ip = InetAddress.getLocalHost().getHostAddress();
//            Properties applicationProperties = new Properties();
//            applicationProperties.load(this.getClass().getClassLoader().getResourceAsStream("application.properties"));
//            String port = applicationProperties.getProperty("server.port");
//            if(port == null){
//                throw new Exception();
//            }
//            InetSocketAddress self = new InetSocketAddress(ip,Integer.valueOf(port));
//
//
//            Properties clisterProperties = new Properties();
//            clisterProperties.load(this.getClass().getClassLoader().getResourceAsStream("cluster.properties"));
//            String cluster = clisterProperties.getProperty("cluster");
//            if(cluster == null){
//
//            }
//            String[] allNodes = cluster.split(",");
//            if(allNodes < 3){
//
//            }
//
//            List<SocketAddress> otherNodes = new ArrayList<>();
//            for(String node: cluster.split(",")){
//                String[] hostAndPort = node.split(":");
//                if(hostAndPort.length != 2){
//                    throw new Exception();
//                }
//                InetSocketAddress inetSocketAddress = new InetSocketAddress(hostAndPort[0],Integer.valueOf(hostAndPort[1]));
//            }

            Cluster cluster = null;
            ElectConfig electConfig = null;
            LogStorage logStorage = null;
            Call call = null;

            RaftCore raftCore = new RaftCore(cluster, electConfig, logStorage, workerGroup.next(), call, null);

            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup);
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.option(ChannelOption.SO_BACKLOG, 128);
            serverBootstrap.option(ChannelOption.SO_KEEPALIVE, true); // (4)
            serverBootstrap.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel channel) throws Exception {
                    channel.pipeline().addLast(new LengthFieldPrepender(4, 1, false));
                    channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 1, 4));
                    channel.pipeline().addLast(new IdleStateHandler(45, -1, -1, TimeUnit.SECONDS), HealthBeatHandler.ACK_INSTANCE);
                    channel.pipeline().addLast(MessageCodec.INSTANCE);
                    channel.pipeline().addLast(new RequestInboundHandler(raftCore));
                }
            });
            Channel channel = serverBootstrap.bind(cluster.self()).sync().channel();
            log.info("Server started....");

            try {
                ServerBootstrap httpServerBootstrap = new ServerBootstrap();
                httpServerBootstrap.group(bossGroup, workerGroup);
                httpServerBootstrap.channel(NioServerSocketChannel.class);
                httpServerBootstrap.childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        channel.pipeline().addLast(new HttpServerCodec());
                    }
                });

                Channel httpChannel = serverBootstrap.bind(6565).sync().channel();
                log.info("Http Server started....");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    httpChannel.close().awaitUninterruptibly();
                    channel.close().awaitUninterruptibly();
                }));
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


}
