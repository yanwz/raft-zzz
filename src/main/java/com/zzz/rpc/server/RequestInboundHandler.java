package com.zzz.rpc.server;

import com.zzz.RaftCore;
import com.zzz.rpc.message.Request;
import com.zzz.rpc.message.Response;
import com.zzz.rpc.message.req.RaftReq;
import com.zzz.rpc.message.res.RaftRsp;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

@ChannelHandler.Sharable
public class RequestInboundHandler extends SimpleChannelInboundHandler<Request> {

    private final RaftCore raftCore;

    public RequestInboundHandler(RaftCore raftCore) {
        this.raftCore = raftCore;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
        String id = request.getId();
        RaftReq raftReq = request.getContent();
        Future<RaftRsp> future = raftCore.handle(channelHandlerContext.channel().remoteAddress(), raftReq, channelHandlerContext.executor().newPromise());
        future.addListener((GenericFutureListener<Future<RaftRsp>>) f -> {
            Response response;
            if (f.isSuccess()){
                response = new Response(id, f.get());
            }else {
                response = new Response(id,500);
            }
            channelHandlerContext.writeAndFlush(response);
        });
    }
}
