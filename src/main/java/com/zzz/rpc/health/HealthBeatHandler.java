package com.zzz.rpc.health;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

@ChannelHandler.Sharable
public class HealthBeatHandler extends ChannelDuplexHandler {

    public static final HealthBeatHandler NO_ACK_INSTANCE = new HealthBeatHandler();

    public static final HealthBeatHandler ACK_INSTANCE = new HealthBeatHandler(true);

    private static final byte HEADER = 0;

    private static final byte CONTENT = 1;

    private static final byte[] HEADER_CONTENT = new byte[]{HEADER, CONTENT};

    private final boolean autoAck;

    private HealthBeatHandler() {
        this.autoAck = false;
    }

    private HealthBeatHandler(boolean autoAck) {
        this.autoAck = autoAck;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            ByteBuf byteBuf = (ByteBuf) msg;
            byte header = byteBuf.markReaderIndex().readByte();
            if (header == HEADER) {
                if (autoAck) {
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(HEADER_CONTENT));
                    return;
                }
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
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
                ctx.close();
                return;
            }

            if (((IdleStateEvent) evt).state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(Unpooled.wrappedBuffer(HEADER_CONTENT));
                return;
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
}
