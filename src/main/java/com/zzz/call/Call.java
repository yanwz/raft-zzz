package com.zzz.call;

import com.zzz.call.codec.CallableCodec;
import com.zzz.call.message.req.RaftReq;
import com.zzz.call.message.res.RaftRsp;
import com.zzz.handler.MessageCodec;
import com.zzz.handler.health.HealthBeatHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Call implements Closeable {

    private final EventExecutor executor;

    private final SingleChannelPoolMap singleChannelPoolMap;

    private boolean closed;

    public Call(Bootstrap bootstrap, int maxConnections, int maxPendingAcquires) {
        this.executor = bootstrap.config().group().next();
        this.singleChannelPoolMap = new SingleChannelPoolMap(bootstrap, new CallableChannelPoolHandler(), maxConnections, maxPendingAcquires);
        this.closed = false;
    }

    public <R extends RaftRsp> Future<R> call(SocketAddress remoteAddress, RaftReq raftReq, Promise<R> promise) {
        return this.call(remoteAddress,raftReq,promise,-1);
    }

    public <R extends RaftRsp> Future<R> call(SocketAddress remoteAddress, RaftReq raftReq, Promise<R> promise, int timeout) {
        acquireAndWrite(remoteAddress,raftReq,promise,timeout,true);
        return promise;
    }

    public Future<Void> send(SocketAddress remoteAddress, RaftReq raftReq, Promise<Void> promise) {
        return this.send(remoteAddress,raftReq,promise,-1);
    }

    public Future<Void> send(SocketAddress remoteAddress, RaftReq raftReq, Promise<Void> promise, int timeout) {
        acquireAndWrite(remoteAddress,raftReq,promise,timeout,false);
        return promise;
    }

    private void acquireAndWrite(SocketAddress remoteAddress, RaftReq raftReq, Promise<?> promise, int timeout,boolean oneway) {
        Objects.requireNonNull(raftReq);
        Objects.requireNonNull(promise);
        if (this.executor.inEventLoop()) {
            this.acquireAndWrite0(remoteAddress, raftReq,promise,timeout,oneway);
        } else {
            this.executor.execute(() -> Call.this.acquireAndWrite0(remoteAddress, raftReq, promise,timeout,oneway));
        }
    }


    private void acquireAndWrite0(SocketAddress remoteAddress, RaftReq raftReq, Promise<?> promise, int timeout,boolean oneway) {
        try {
            assert this.executor.inEventLoop();
            if (this.closed) {
                promise.tryFailure(new IllegalStateException("Call was closed"));
                return;
            }
            if (timeout > 0) {
                scheduleTimeout(promise, timeout);
            }
            ChannelPool channelPool = singleChannelPoolMap.get(remoteAddress);
            Future<Channel> acquireFuture = channelPool.acquire();
            promise.addListener((f)->acquireFuture.cancel(false));
            acquireFuture.addListener((GenericFutureListener<Future<Channel>>) future -> {
                if (future.isSuccess()) {
                    Channel channel = future.get();
                    channel.writeAndFlush(new CallableRaftReq(raftReq, promise,oneway)).addListener(f -> channelPool.release(channel));
                } else {
                    promise.tryFailure(future.cause());
                }
            });
        } catch (Throwable var) {
            promise.tryFailure(var);
        }
    }


    private void scheduleTimeout(Promise<?> promise, int timeout) {
        Future<Boolean> timeoutFuture = this.executor.schedule(() -> promise.tryFailure(new TimeoutException()), timeout, TimeUnit.MILLISECONDS);
        promise.addListener((f)->timeoutFuture.cancel(false));
    }


    @Override
    public void close() throws IOException {
        if (this.executor.inEventLoop()) {
            this.close0();
        } else {
            this.executor.submit(Call.this::close0).awaitUninterruptibly();
        }
    }

    private void close0() {
        assert this.executor.inEventLoop();
        if (!this.closed) {
            this.closed = true;
            while (true) {
                singleChannelPoolMap.close();
            }
        }
    }


    static class CallableChannelPoolHandler implements ChannelPoolHandler {

        @Override
        public void channelReleased(Channel channel) throws Exception {
        }

        @Override
        public void channelAcquired(Channel channel) throws Exception {
        }

        @Override
        public void channelCreated(Channel channel) throws Exception {
            channel.pipeline().addLast(new LengthFieldPrepender(4,1,false));
            channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(65536,0,4,1,4));
            channel.pipeline().addLast(new IdleStateHandler(45, 15, -1,TimeUnit.SECONDS),HealthBeatHandler.NO_ACK_INSTANCE);
            channel.pipeline().addLast(MessageCodec.INSTANCE);
            channel.pipeline().addLast(new CallableCodec());
        }
    }

}
