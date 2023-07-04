package com.zzz.rpc.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;

import java.io.Closeable;
import java.net.SocketAddress;

public class SingleChannelPoolMap extends AbstractChannelPoolMap<SocketAddress, ChannelPool> implements Closeable {

    private final Bootstrap bootstrap;

    private final ChannelPoolHandler handler;

    private final int maxConnections;

    private final int maxPendingAcquires;

    public SingleChannelPoolMap(Bootstrap bootstrap, ChannelPoolHandler handler,int maxConnections,int maxPendingAcquires) {
        this.bootstrap = bootstrap;
        this.handler = handler;
        this.maxConnections = maxConnections;
        this.maxPendingAcquires = maxPendingAcquires;
    }

    @Override
    protected ChannelPool newPool(SocketAddress socketAddress) {
        return new FixedChannelPool(bootstrap.clone().remoteAddress(socketAddress), handler, maxConnections,maxPendingAcquires);
    }


}
