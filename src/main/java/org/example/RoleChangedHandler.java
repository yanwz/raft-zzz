package org.example;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import org.example.request.ReplicationLogRequest;
import org.example.request.VoteRequest;
import org.example.response.ReplicationLogResponse;
import org.example.response.VoteResponse;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RoleChangedHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private NodeRole role;

    private final ClusterContext clusterContext;

    private final LogSystem logSystem;

    private final long timeoutMills;

    private final long randomTimeoutMills;

    private long timeoutStartTime = System.currentTimeMillis();
    private Future<?> timeoutFuture;

    private final long logReplicationTimeoutMills;

    private final int packageLogSize;


    private final ScheduledThreadPoolExecutor logReplicationExecutor;

    private Map<InetSocketAddress, Future<?>> logReplicationFutureMap;


    public RoleChangedHandler(NodeRole role, ClusterContext clusterContext, LogSystem logSystem, long timeoutMills, long randomTimeoutMills) {
        this.role = role;
        this.clusterContext = clusterContext;
        this.logSystem = logSystem;
        this.timeoutMills = timeoutMills;
        this.randomTimeoutMills = timeoutMills + new Random().nextInt(2000);
        this.logReplicationExecutor = new ScheduledThreadPoolExecutor(0);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket datagramPacket) throws Exception {
        InetSocketAddress remote = datagramPacket.sender();
        Message message = (Message) JSON.parse(datagramPacket.content().toString(CharsetUtil.UTF_8), JSONReader.Feature.SupportAutoType);
        if (message instanceof VoteRequest) {
            handleVoteRequest(ctx, remote, (VoteRequest) message);
        } else if (message instanceof VoteResponse) {
            handleVoteResponse(ctx, remote, (VoteResponse) message);
        } else if (message instanceof ReplicationLogRequest) {
            handleLogRequest(ctx, remote, (ReplicationLogRequest) message);
        } else if (message instanceof ReplicationLogResponse) {
            handleLogResponse(ctx, remote, (ReplicationLogResponse) message);
        } else {
            throw new IllegalArgumentException();
        }
    }


    private void handleVoteRequest(ChannelHandlerContext ctx, InetSocketAddress remote, VoteRequest voteRequest) {
        final NodeRole nodeRole = this.role;
        if (voteRequest.getTerm() < nodeRole.getTerm()) {
            writeMessage(ctx, remote, new VoteResponse(nodeRole.getTerm(), voteRequest.getId(), false));
            return;
        }

        if (voteRequest.getTerm() > nodeRole.getTerm()) {
            becomeFollower(ctx, voteRequest.getTerm());
        }

        boolean voteGranted = false;

        if (nodeRole instanceof FollowerNodeRole followerNodeRole) {
            if (followerNodeRole.getVotedFor() == null) {
                voteGranted = (voteRequest.getLastLogTerm() > logContext.getLastLogTerm() || (voteRequest.getLastLogTerm() == logContext.getLastLogTerm() && voteRequest.getLastLogIndex() >= logContext.getLastLogIndex()));
                if (voteGranted) {
                    followerNodeRole.setVotedFor(remote);
                }
            }
        }
        writeMessage(ctx, remote, new VoteResponse(this.role.getTerm(), voteRequest.getId(), voteGranted));
    }

    private void handleVoteResponse(ChannelHandlerContext ctx, InetSocketAddress remote, VoteResponse voteResponse) {
        final NodeRole nodeRole = this.role;
        if (!(nodeRole instanceof CandidateNodeRole candidateNodeRole)) {
            return;
        }

        //non-match
        if (voteResponse.getVoteTermByRequest() != candidateNodeRole.getTerm()) {
            return;
        }

        if (voteResponse.getTerm() > candidateNodeRole.getTerm()) {
            becomeFollower(ctx, voteResponse.getTerm());
            return;
        }

        if (voteResponse.isVoteGranted()) {
            candidateNodeRole.voteGranted(remote);
            if (candidateNodeRole.voteGrantedCount() > clusterContext.clusterNodeSize() / 2) {
                becomeLeader(ctx);
            }
        }
    }

    private void handleLogRequest(ChannelHandlerContext ctx, InetSocketAddress remote, ReplicationLogRequest logRequest) {
        final NodeRole nodeRole = this.role;

        if (logRequest.getTerm() < nodeRole.getTerm()) {
            writeMessage(ctx, remote, new ReplicationLogResponse(nodeRole.getTerm(), logRequest.getId(), false));
            return;
        }
        if (logRequest.getTerm() > nodeRole.getTerm() || nodeRole instanceof CandidateNodeRole) {
            becomeFollower(ctx, logRequest.getTerm());
        }
        boolean replicationResult = logSystem.replication(logRequest.getPreLogTerm(), logRequest.getPreLogIndex(), logRequest.getEntries());
        writeMessage(ctx, remote, new ReplicationLogResponse(nodeRole.getTerm(), logRequest.getId(), replicationResult));
    }


    private void handleLogResponse(ChannelHandlerContext ctx, InetSocketAddress remote, ReplicationLogResponse logResponse) {
        //non-match
        final NodeRole nodeRole = this.role;
        if (!(nodeRole instanceof LeaderNodeRole leaderNodeRole)) {
            return;
        }

        if (!logResponse.getRequestId().equals(leaderNodeRole.getLastRequest(remote))) {
            return;
        }

        if (logResponse.getTerm() > nodeRole.getTerm()) {
            becomeFollower(ctx, logResponse.getTerm());
            return;
        }

        if (logResponse.isSuccess()) {

        } else {

        }
    }


    private final class TimeoutCheckTask extends AbstractRoleTask {

        private final long randomTimeoutMills;

        public TimeoutCheckTask(ChannelHandlerContext ctx, long randomTimeoutMills) {
            super(ctx);
            this.randomTimeoutMills = randomTimeoutMills;
        }

        @Override
        protected void run(ChannelHandlerContext ctx) {
            long nextDelay = this.randomTimeoutMills - (System.currentTimeMillis() - RoleChangedHandler.this.timeoutStartTime);
            if (nextDelay <= 0) {
                RoleChangedHandler.this.becomeCandidate(ctx);
            } else {
                ctx.channel().eventLoop().schedule(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }


    private final static class ElectionRunnable extends AbstractRoleTask {

        private final int term;

        private int lastLogTerm;

        private int lastLogIndex;

        private final Set<InetSocketAddress> otherNodes;


        public ElectionRunnable(ChannelHandlerContext ctx, int term, int lastLogTerm, int lastLogIndex, Set<InetSocketAddress> otherNodes) {
            super(ctx);
            this.term = term;
            this.lastLogTerm = lastLogTerm;
            this.lastLogIndex = lastLogIndex;
            this.otherNodes = otherNodes;
        }


        @Override
        protected void run(ChannelHandlerContext ctx) {
            VoteRequest voteRequest = new VoteRequest(term, lastLogTerm, lastLogIndex);
            String jsonBody = JSON.toJSONString(voteRequest, JSONWriter.Feature.WriteClassName);
            for (InetSocketAddress otherNode : otherNodes) {
                DatagramPacket datagramPacket = new DatagramPacket(Unpooled.copiedBuffer(jsonBody, StandardCharsets.UTF_8), otherNode);
                ctx.channel().writeAndFlush(datagramPacket);
            }
        }
    }


    private abstract static class AbstractRoleTask implements Runnable {

        private final ChannelHandlerContext ctx;

        public AbstractRoleTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run() {
            if (this.ctx.channel().isOpen()) {
                this.run(this.ctx);
            }
        }

        protected abstract void run(ChannelHandlerContext ctx);
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NodeRole role = RoleChangedHandler.this.role;
        if (role instanceof FollowerNodeRole) {
            TimeoutCheckTask heartbeatTimeoutCheckRunnable = new TimeoutCheckTask(ctx);
            this.timeoutFuture = ctx.channel().eventLoop().schedule(heartbeatTimeoutCheckRunnable, RoleChangedHandler.this.timeoutMills, TimeUnit.MILLISECONDS);
        }

        if (role instanceof CandidateNodeRole) {
            CandidateRunnable candidateRunnable = new CandidateRunnable(ctx);
            ctx.channel().eventLoop().execute(candidateRunnable);
        }

        if (role instanceof LeaderNodeRole) {
            LeaderRunnable leaderRunnable = new LeaderRunnable(ctx);
            ctx.channel().eventLoop().execute(leaderRunnable);
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (this.logReplicationFutureMap != null) {
            for (Future<?> logReplicationFuture : this.logReplicationFutureMap.values()) {
                logReplicationFuture.cancel(false);
            }
            this.logReplicationFutureMap = null;
        }

        for (Runnable remainingTask : this.logReplicationExecutor.shutdownNow()) {
            if (remainingTask instanceof Future) {
                ((Future) remainingTask).cancel(true);
            }
        }
        try {
            logReplicationExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
        }
        ctx.fireChannelInactive();
    }

    private void writeMessage(ChannelHandlerContext ctx, InetSocketAddress remote, Object message) {
        String jsonBody = JSON.toJSONString(message, JSONWriter.Feature.WriteClassName);
        ctx.channel().writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(jsonBody, StandardCharsets.UTF_8), remote));
    }

    private void becomeFollower(ChannelHandlerContext ctx, int term) {
        this.role = new FollowerNodeRole(term);
        this.timeoutStartTime = System.currentTimeMillis();
        if (this.timeoutFuture != null) {
            this.timeoutFuture.cancel(false);
            this.timeoutFuture = null;
        }
        this.timeoutFuture = ctx.channel().eventLoop().schedule(new TimeoutCheckTask(ctx, this.randomTimeoutMills), this.randomTimeoutMills, TimeUnit.MILLISECONDS);
    }

    private void becomeCandidate(ChannelHandlerContext ctx) {
        NodeRole oldRole = this.role;
        LogEntry lastLog = this.logSystem.getLast();
        this.role = new CandidateNodeRole(oldRole.getTerm() + 1);
        this.timeoutStartTime = System.currentTimeMillis();
        this.timeoutFuture = ctx.channel().eventLoop().schedule(new TimeoutCheckTask(ctx, this.randomTimeoutMills), this.randomTimeoutMills, TimeUnit.MILLISECONDS);
        ctx.channel().eventLoop().execute(new ElectionRunnable(ctx, this.role.getTerm(), lastLog.getTerm(), lastLog.getIndex(), clusterContext.getOtherNodes()));
    }

    private void becomeLeader(ChannelHandlerContext ctx) {
        this.role = new LeaderNodeRole(role.getTerm());
        this.timeoutStartTime = -1;
        if (this.timeoutFuture != null) {
            this.timeoutFuture.cancel(false);
            this.timeoutFuture = null;
        }
        startLogReplication(ctx);
    }

    private void startLogReplication(ChannelHandlerContext ctx) {
        for (InetSocketAddress follower : clusterContext.getOtherNodes()) {
            ScheduledFuture<?> future = this.logReplicationExecutor.scheduleAtFixedRate(new LogReplicationTask(ctx, follower), 0, logReplicationTimeoutMills, TimeUnit.MILLISECONDS);
            this.logReplicationFutureMap.put(follower, future);
        }
    }

    private final static class LogReplicationTask extends AbstractRoleTask {

        private final int term;

        private final int packageLogSize;

        private final LogSystem logSystem;

        private final InetSocketAddress follower;

        public LogReplicationTask(ChannelHandlerContext ctx, int term, int packageLogSize, LogSystem logSystem, InetSocketAddress follower) {
            super(ctx);
            this.term = term;
            this.packageLogSize = packageLogSize;
            this.logSystem = logSystem;
            this.follower = follower;
        }

        @Override
        protected void run(ChannelHandlerContext ctx) {
            Integer commitIndex = null;
            List<LogEntry> logEntries;
            int preLogIndex;
            LogReplicationInfo logReplicationInfo = leaderNodeRole.logReplicationInfo(follower);
            if (logReplicationInfo == null) {
                logEntries = logSystem.sub(logSystem.size() - 1, -packageLogSize - 1);
                preLogIndex = logSystem.size() - 1 - logEntries.size();
            } else {
                logEntries = logSystem.sub(logReplicationInfo.getIndex(), logReplicationInfo.isMatched() ? packageLogSize + 1 : -packageLogSize - 1);
                preLogIndex = logReplicationInfo.getIndex() - logEntries.size();
            }
            LogEntry firstLog = logEntries.remove(0);
            ReplicationLogRequest lastRequest = new ReplicationLogRequest(term, firstLog.getTerm(), preLogIndex, logEntries, commitIndex);
            String jsonBody = JSON.toJSONString(lastRequest, JSONWriter.Feature.WriteClassName);
            DatagramPacket datagramPacket = new DatagramPacket(Unpooled.copiedBuffer(jsonBody, StandardCharsets.UTF_8), follower);
            ctx.channel().writeAndFlush(datagramPacket);
        }

    }
}
