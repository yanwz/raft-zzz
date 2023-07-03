package com.zzz.call;

import com.zzz.call.codec.CallableHandler;
import com.zzz.call.message.req.RaftReq;
import com.zzz.handler.health.HealthBeatHandler;
import com.zzz.handler.MessageCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class Call implements Closeable {

    private final EventExecutor executor;

    private final SingleChannelPoolMap singleChannelPoolMap;

    private boolean closed;

    public Call(Bootstrap bootstrap, int maxConnections, int maxPendingAcquires) {
        this.executor = bootstrap.config().group().next();
        this.singleChannelPoolMap = new SingleChannelPoolMap(bootstrap, new CallableChannelPoolHandler(), maxConnections, maxPendingAcquires);
        this.closed = false;
    }

    public <R> Future<R> call(SocketAddress remoteAddress, RaftReq raftReq, Promise<R> promise) {
        return this.call(remoteAddress,raftReq,promise,-1);
    }


    public <R> Future<R> call(SocketAddress remoteAddress, RaftReq raftReq, Promise<R> promise, int timeout) {
        Objects.requireNonNull(raftReq);
        Objects.requireNonNull(promise);
        if (this.executor.inEventLoop()) {
            this.call0(remoteAddress, raftReq, (Promise<Object>) promise,timeout);
        } else {
            this.executor.execute(() -> Call.this.call0(remoteAddress, raftReq, (Promise<Object>) promise,timeout));
        }
        return promise;
    }

    private void call0(SocketAddress remoteAddress, RaftReq raftReq, Promise<Object> promise, int timeout) {
        try {
            assert this.executor.inEventLoop();
            if (this.closed) {
                promise.tryFailure(new IllegalStateException("Call was closed"));
                return;
            }
            if (timeout > 0) {
                scheduleTimeout(promise, timeout);
            }
            acquireAndWrite(remoteAddress, raftReq, promise);
        } catch (Throwable var) {
            promise.tryFailure(var);
        }
    }


    private void scheduleTimeout(Promise<Object> promise, int timeout) {
        Future<Boolean> timeoutFuture = this.executor.schedule(() -> promise.tryFailure(new ReadTimeoutException()), timeout, TimeUnit.MILLISECONDS);
        addCancelledListener(promise,timeoutFuture);
    }


    private void acquireAndWrite(SocketAddress remoteAddress, RaftReq raftReq, Promise<Object> promise) {
        ChannelPool channelPool = singleChannelPoolMap.get(remoteAddress);
        Future<Channel> acquireChannelFuture = channelPool.acquire();
        addFailedListenerToCancel(promise,acquireChannelFuture);
        acquireChannelFuture.addListener((GenericFutureListener<Future<Channel>>) future -> {
            if (future.isSuccess()) {
                Channel channel = future.get();
                ChannelFuture writeFuture = channel.writeAndFlush(new CallableRaftReq(raftReq, promise));
                writeFuture.addListener(f -> channelPool.release(channel));
                addFailedListener(writeFuture,promise);
                addFailedListenerToCancel(promise,writeFuture);
            } else {
                promise.tryFailure(future.cause());
            }
        });
    }

    private void addCancelledListener(Future<?> observable,Future<?> observer){
        observable.addListener(f -> {
            if(f.isCancelled()){
                observer.cancel(false);
            }
        });
    }

    private void addFailedListenerToCancel(Future<?> observable,Future<?> observer){
        observable.addListener(f -> {
            if(!f.isSuccess()){
                observer.cancel(false);
            }
        });
    }

    private void addFailedListener(Future<?> observable,Promise<?> observer){
        observable.addListener(f -> {
            if(!f.isSuccess()){
                observer.tryFailure(f.cause());
            }
        });
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
            channel.pipeline().addLast(new IdleStateHandler(45, 15, -1,TimeUnit.SECONDS));
            channel.pipeline().addLast(new LengthFieldPrepender(4,1,false));
            channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(65536,0,4,1,4));
            channel.pipeline().addLast(HealthBeatHandler.NO_ACK_INSTANCE);
            channel.pipeline().addLast(MessageCodec.INSTANCE);
            channel.pipeline().addLast(new CallableHandler());
        }
    }

}
