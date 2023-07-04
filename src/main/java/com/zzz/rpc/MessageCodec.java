package com.zzz.rpc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

@ChannelHandler.Sharable
public class MessageCodec extends ChannelDuplexHandler {

    public static final MessageCodec INSTANCE = new MessageCodec();

    private static final byte HEADER = 1;


    private MessageCodec() {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            ByteBuf byteBuf = (ByteBuf) msg;
            byte header = byteBuf.markReaderIndex().readByte();
            if (header == HEADER) {
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(bytes);
                ctx.fireChannelRead(deserialize(bytes));
                return;
            } else {
                byteBuf.resetReaderIndex();
            }
            release = false;
            ctx.fireChannelRead(msg);
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{HEADER},serialize(msg)), promise);
    }

    private byte[] serialize(Object msg) {
        return JSON.toJSONBytes(msg, JSONWriter.Feature.WriteClassName);
    }

    private Object deserialize(byte[] bytes) {
        return JSON.parseObject(bytes, JSONReader.Feature.SupportAutoType, JSONReader.Feature.ErrorOnNotSupportAutoType);
    }

}
