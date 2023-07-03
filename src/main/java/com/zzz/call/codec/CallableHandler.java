package com.zzz.call.codec;

import com.zzz.call.CallableRaftReq;
import com.zzz.call.Request;
import com.zzz.call.Response;
import com.zzz.call.exception.ErrorResException;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Promise;

import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CallableHandler extends ChannelDuplexHandler {

    private final Map<String, Promise<Object>> promiseContext;


    public CallableHandler() {
        this.promiseContext = new HashMap<>();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise writePromise) throws Exception {
        if (msg instanceof CallableRaftReq) {
            CallableRaftReq callableMessage = (CallableRaftReq) msg;
            Promise<Object> promise = callableMessage.getPromise();
            String id = generateId();
            promise.addListener(future -> promiseContext.remove(id));
            promiseContext.put(id, promise);
            msg = new Request(id, callableMessage.getRaftReq());
        }
        ctx.write(msg, writePromise);
    }

    private String generateId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Response) {
            Response response = (Response) msg;
            Promise<Object> promise = promiseContext.get(response.getId());
            if (promise != null) {
                if(response.isSuccess()){
                    promise.trySuccess(response.getContent());
                }else {
                    promise.tryFailure(new ErrorResException(response.getErrorCode()));
                }
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Iterator<Promise<Object>> iterator = promiseContext.values().iterator();
        while (iterator.hasNext()) {
            Promise<Object> promise = iterator.next();
            promise.tryFailure(new ClosedChannelException());
            iterator.remove();
        }
        ctx.fireChannelInactive();
    }

}
