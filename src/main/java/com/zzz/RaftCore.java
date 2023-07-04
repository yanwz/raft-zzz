package com.zzz;


import com.zzz.rpc.client.Call;
import com.zzz.rpc.message.req.PreVoteReq;
import com.zzz.rpc.message.req.RaftReq;
import com.zzz.rpc.message.req.ReplicationLogReq;
import com.zzz.rpc.message.req.VoteReq;
import com.zzz.rpc.message.res.PreVoteRes;
import com.zzz.rpc.message.res.RaftRsp;
import com.zzz.rpc.message.res.ReplicationLogRes;
import com.zzz.rpc.message.res.VoteRes;
import com.zzz.config.ElectConfig;
import com.zzz.log.*;
import com.zzz.net.Cluster;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RaftCore {

    private final Cluster cluster;

    private final ElectConfig electConfig;

    private final LogStorage logStorage;

    private RoleHandler roleHandler;

    private final EventExecutor executor;

    private final Call call;


    public RaftCore(Cluster cluster, ElectConfig electConfig, LogStorage logStorage, EventExecutor executor, Call call, RoleHandler roleHandler) {
        this.cluster = cluster;
        this.electConfig = electConfig;
        this.logStorage = logStorage;
        this.executor = executor;
        this.call = call;
        this.roleHandler = roleHandler;
        this.roleHandler.init();
    }


    private void become(RoleHandler roleHandler) {
        if (this.roleHandler != null) {
            this.roleHandler.destory();
        }
        this.roleHandler = roleHandler;
        this.roleHandler.init();
    }

    public Future<RaftRsp> handle(SocketAddress remoteAddress, RaftReq raftReq,Promise<RaftRsp> promise) {
        if(this.executor.inEventLoop()){
            handle0(remoteAddress, raftReq,promise);
        }else {
            this.executor.execute(()->handle0(remoteAddress, raftReq,promise));
        }
        return promise;
    }


    public void handle0(SocketAddress remoteAddress, RaftReq raftReq,Promise<RaftRsp> promise) {
        try {
            assert this.executor.inEventLoop();
            RaftRsp raftRsp = roleHandler.handle(remoteAddress, raftReq);
            promise.trySuccess(raftRsp);
        }catch (Throwable var){
            promise.tryFailure(var);
        }
    }


    static abstract class RoleHandler {

        protected final Integer term;

        protected final RoleEnum roleEnum;

        protected SocketAddress voteFor;

        protected Integer commitIndex;

        public RoleHandler(int term, RoleEnum roleEnum, SocketAddress voteFor) {
            this.term = term;
            this.roleEnum = roleEnum;
            this.voteFor = voteFor;
        }

        public abstract void init();

        public void appendLog(byte[] command){
        }

        public abstract RaftRsp handle(SocketAddress remoteAddress, RaftReq raftReq);

        public abstract void destory();

    }


    class FollowerHandler extends RoleHandler {

        private Long lastPingTime;

        private Future<?> timeoutFuture;

        public FollowerHandler(int term, SocketAddress voteFor) {
            super(term, RoleEnum.FOLLOWER, voteFor);
        }


        @Override
        public RaftRsp handle(SocketAddress remoteAddress, RaftReq raftReq) {
            final int term = this.term;
            if (raftReq instanceof PreVoteReq) {
                PreVoteReq preVoteReq = (PreVoteReq) raftReq;
                int requestTerm = preVoteReq.getTerm();
                boolean vote = false;
                //leader lease
                boolean leaseExpire = System.currentTimeMillis() - lastPingTime > RaftCore.this.electConfig.timeout();
                if (leaseExpire) {
                    if ((requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) || requestTerm > term) {
                        vote = preVoteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                    }
                }
                return new PreVoteRes(term, vote);
            }

            if (raftReq instanceof VoteReq) {
                VoteReq voteReq = (VoteReq) raftReq;
                int requestTerm = voteReq.getTerm();
                if (requestTerm > term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                boolean vote = false;
                if (requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) {
                    vote = voteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                    if (vote && voteFor == null) {
                        voteFor = remoteAddress;
                    }
                }
                return new VoteRes(term, vote);
            }


            if (raftReq instanceof ReplicationLogReq) {
                ReplicationLogReq replicationLogReq = (ReplicationLogReq) raftReq;
                int requestTerm = replicationLogReq.getTerm();
                if (requestTerm > term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                boolean replicationResult = false;
                if (requestTerm == term) {
                    resetLastPingTime();
                    LogReplicationEntry logReplicationEntry = replicationLogReq.getLogReplicationEntry();
                    LogMeta preLogMeta = logReplicationEntry.getPreLogMeta();
                    replicationResult = RaftCore.this.logStorage.replication(preLogMeta.getIndex(), preLogMeta.getTerm(), logReplicationEntry.entries());
                }
                return new ReplicationLogRes(term, replicationResult);
            }
            return null;
        }

        private void resetLastPingTime() {
            lastPingTime = System.currentTimeMillis();
        }


        @Override
        public void init() {
            long timeout = RaftCore.this.electConfig.randomTimeout();
            this.timeoutFuture = RaftCore.this.executor.schedule(new Runnable() {
                @Override
                public void run() {
                    long nextDelay;
                    if (lastPingTime == null || (nextDelay = timeout - (System.currentTimeMillis() - lastPingTime)) <= 0) {
                        become(new PreCandidateHandler(FollowerHandler.this.term, FollowerHandler.this.voteFor));
                    } else {
                        RaftCore.this.executor.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);

        }


        @Override
        public void destory() {
            if (this.timeoutFuture != null) {
                this.timeoutFuture.cancel(false);
                this.timeoutFuture = null;
            }
        }

    }


    public class PreCandidateHandler extends RoleHandler {

        private final Map<SocketAddress, String> preVoteRequestIdMap;

        private final Set<SocketAddress> preVoteGrantedSet;

        private Future<?> timeoutFuture;


        public PreCandidateHandler(int term, SocketAddress voteFor) {
            super(term, RoleEnum.PRE_CANDIDATE, voteFor);
            this.preVoteRequestIdMap = new HashMap<>();
            this.preVoteGrantedSet = new HashSet<>();
        }


        @Override
        public RaftRsp handle(SocketAddress remoteAddress, RaftReq raftReq) {
            final int term = this.term;
            if (raftReq instanceof PreVoteReq) {
                PreVoteReq preVoteReq = (PreVoteReq) raftReq;
                int requestTerm = preVoteReq.getTerm();
                boolean vote = false;
                if ((requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) || requestTerm > term) {
                    vote = preVoteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                }
                return new PreVoteRes(term, vote);
            }

            if (raftReq instanceof VoteReq) {
                VoteReq voteReq = (VoteReq) raftReq;
                int requestTerm = voteReq.getTerm();
                if (requestTerm > term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                boolean vote = false;
                if (requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) {
                    vote = voteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                    if (vote && voteFor == null) {
                        voteFor = remoteAddress;
                    }
                }
                return new VoteRes(term, vote);
            }


            if (raftReq instanceof ReplicationLogReq) {
                ReplicationLogReq replicationLogReq = (ReplicationLogReq) raftReq;
                int requestTerm = replicationLogReq.getTerm();
                if (requestTerm >= term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, requestTerm == term ? PreCandidateHandler.this.voteFor : null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                return new ReplicationLogRes(term, false);

            }
            return null;
        }

        @Override
        public void init() {
            long timeout = RaftCore.this.electConfig.randomTimeout();

            this.timeoutFuture = RaftCore.this.executor.schedule(() -> become(new FollowerHandler(PreCandidateHandler.this.term, PreCandidateHandler.this.voteFor)), timeout, TimeUnit.MILLISECONDS);
            preVoteGrantedSet.add(RaftCore.this.cluster.self());

            for (SocketAddress remoteAddress : cluster.otherNodes()) {
                String id = UUID.randomUUID().toString().replaceAll("-", "");
                preVoteRequestIdMap.put(remoteAddress, id);
                PreVoteReq preVoteReq = new PreVoteReq(this.term + 1, RaftCore.this.logStorage.getLastLogMeta());
                Future<PreVoteRes> promise = RaftCore.this.call.call(remoteAddress, preVoteReq, RaftCore.this.executor.newPromise());
                promise.addListener((GenericFutureListener<Future<PreVoteRes>>) future -> {
                    if (future.isSuccess()) {
                        PreVoteRes preVoteRes = future.get();
                        if (preVoteRes.getVoteGranted()) {
                            if (preVoteGrantedSet.add(remoteAddress) && preVoteGrantedSet.size() > (cluster.size() / 2)) {
                                become(new CandidateHandler(PreCandidateHandler.this.term + 1, null));
                            }
                        }
                    }
                });
            }
        }


        @Override
        public void destory() {
            if (this.timeoutFuture != null) {
                this.timeoutFuture.cancel(false);
                this.timeoutFuture = null;
            }
            preVoteRequestIdMap.clear();
            preVoteGrantedSet.clear();
        }
    }


    class CandidateHandler extends RoleHandler {

        private final List<Future<VoteRes>> voteRequestFutureList;

        private final Set<SocketAddress> voteGrantedSet;

        private Future<?> timeoutFuture;


        public CandidateHandler(int term, SocketAddress voteFor) {
            super(term, RoleEnum.CANDIDATE, voteFor);
            this.voteRequestFutureList = new ArrayList<>();
            this.voteGrantedSet = new HashSet<>();
        }


        @Override
        public RaftRsp handle(SocketAddress remoteAddress, RaftReq raftReq) {
            final int term = this.term;
            if (raftReq instanceof PreVoteReq) {
                PreVoteReq preVoteReq = (PreVoteReq) raftReq;
                int requestTerm = preVoteReq.getTerm();
                boolean vote = false;
                if ((requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) || requestTerm > term) {
                    vote = preVoteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                }
                return new PreVoteRes(term, vote);
            }

            if (raftReq instanceof VoteReq) {
                VoteReq voteReq = (VoteReq) raftReq;
                int requestTerm = voteReq.getTerm();
                if (requestTerm > term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                boolean vote = false;
                if (requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) {
                    vote = voteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                    if (vote && voteFor == null) {
                        voteFor = remoteAddress;
                    }
                }
                return new VoteRes(term, vote);
            }

            if (raftReq instanceof ReplicationLogReq) {
                ReplicationLogReq replicationLogReq = (ReplicationLogReq) raftReq;
                int requestTerm = replicationLogReq.getTerm();
                if (requestTerm >= term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, requestTerm == term ? CandidateHandler.this.voteFor : null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                return new ReplicationLogRes(term, false);

            }
            return null;
        }


        @Override
        public void init() {
            long timeout = RaftCore.this.electConfig.randomTimeout();
            this.timeoutFuture = RaftCore.this.executor.schedule(() -> become(new FollowerHandler(CandidateHandler.this.term, CandidateHandler.this.voteFor)), timeout, TimeUnit.MILLISECONDS);

            this.voteFor = RaftCore.this.cluster.self();
            voteGrantedSet.add(RaftCore.this.cluster.self());

            for (SocketAddress remoteAddress : cluster.otherNodes()) {
                VoteReq voteReq = new VoteReq(this.term + 1, RaftCore.this.logStorage.getLastLogMeta());
                Future<VoteRes> promise = RaftCore.this.call.call(remoteAddress, voteReq, RaftCore.this.executor.newPromise());
                voteRequestFutureList.add(promise);
                promise.addListener((GenericFutureListener<Future<VoteRes>>) future -> {
                    voteRequestFutureList.remove(future);
                    if (future.isSuccess()) {
                        VoteRes voteResponse = future.get();
                            if (voteResponse.getVoteGranted()) {
                                if (voteGrantedSet.add(remoteAddress) && voteGrantedSet.size() > (cluster.size() / 2)) {
                                    become(new LeaderHandler(CandidateHandler.this.term, CandidateHandler.this.voteFor));
                                }
                        }
                    }
                });
            }
        }

        @Override
        public void destory() {
            if (this.timeoutFuture != null) {
                this.timeoutFuture.cancel(false);
                this.timeoutFuture = null;
            }
            Iterator<Future<VoteRes>> iterator = voteRequestFutureList.iterator();
            while (iterator.hasNext()) {
                Future<VoteRes> next = iterator.next();
                next.cancel(false);
                iterator.remove();
            }
            voteGrantedSet.clear();
        }
    }


    class LeaderHandler extends RoleHandler {

        private final Map<SocketAddress, Integer> logReplicationIndexMap;

        private final Map<SocketAddress, Future<?>> logReplicationFutureMap;

        public LeaderHandler(int term, SocketAddress voteFor) {
            super(term, RoleEnum.LEADER, voteFor);
            this.logReplicationIndexMap = new HashMap<>();
            this.logReplicationFutureMap = new HashMap<>();
        }

        @Override
        public RaftRsp handle(SocketAddress remoteAddress, RaftReq raftReq) {
            final int term = this.term;
            if (raftReq instanceof PreVoteReq) {
                return new PreVoteRes(term, false);
            }

            if (raftReq instanceof VoteReq) {
                VoteReq voteReq = (VoteReq) raftReq;
                int requestTerm = voteReq.getTerm();
                if (requestTerm > term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                boolean vote = false;
                if (requestTerm == term && (voteFor == null || voteFor.equals(remoteAddress))) {
                    vote = voteReq.getLastLogMeta().isUpdateToDate(RaftCore.this.logStorage.getLastLogMeta());
                    if (vote && voteFor == null) {
                        voteFor = remoteAddress;
                    }
                }
                return new VoteRes(term, vote);
            }


            if (raftReq instanceof ReplicationLogReq) {
                ReplicationLogReq replicationLogReq = (ReplicationLogReq) raftReq;
                int requestTerm = replicationLogReq.getTerm();
                if (requestTerm == term) {
                    System.exit(1);
                }
                if (requestTerm > term) {
                    RoleHandler newRoleHandler = new FollowerHandler(requestTerm, null);
                    become(newRoleHandler);
                    return newRoleHandler.handle(remoteAddress, raftReq);
                }
                return new ReplicationLogRes(term, false);
            }
            return null;
        }

        @Override
        public void init() {
            for (SocketAddress remoteAddress : cluster.otherNodes()) {
                LeaderHandler.this.doReplicationLog(remoteAddress);
            }
        }


        @Override
        public void destory() {
            Iterator<Future<?>> iterator = logReplicationFutureMap.values().iterator();
            while (iterator.hasNext()) {
                iterator.next().cancel(false);
                iterator.remove();
            }
            logReplicationIndexMap.clear();
        }


        private void doReplicationLog(SocketAddress remoteAddress) {
            Future<?> logReplicationFuture = logReplicationFutureMap.remove(remoteAddress);
            if (logReplicationFuture != null) {
                logReplicationFuture.cancel(false);
            }
            ReplicationLogReq replicationLogRequest = nextReplicationLogRequest(remoteAddress);
            logReplicationFuture = executor.scheduleAtFixedRate(new Runnable() {
                private Future<ReplicationLogRes> promise;

                @Override
                public void run() {
                    if (promise != null) {
                        promise.cancel(false);
                        promise = null;
                    }
                    promise = RaftCore.this.call.call(remoteAddress, replicationLogRequest, RaftCore.this.executor.newPromise());
                    promise.addListener((GenericFutureListener<Future<ReplicationLogRes>>) future -> {
                                promise = null;
                                if (future.isSuccess()) {
                                    ReplicationLogRes replicationLogResponse = future.get();
                                        if (replicationLogResponse.getMatched()) {
                                            LogReplicationEntry logReplicationEntry = replicationLogRequest.getLogReplicationEntry();
                                            if (!logReplicationEntry.isEmpty()) {
                                                int logReplicationIndex = replicationLogResponse.getMatched() ? logReplicationEntry.getLastIndex() : -logReplicationEntry.getPreIndex();
                                                LeaderHandler.this.logReplicationIndexMap.put(remoteAddress, logReplicationIndex);
                                                if (isNotLatest(remoteAddress)) {
                                                    doReplicationLog(remoteAddress);
                                                }
                                            }
                                    }
                                }
                            }
                    );

                }
            }, 0, RaftCore.this.electConfig.electInterval(), TimeUnit.MILLISECONDS);
            logReplicationFutureMap.put(remoteAddress, logReplicationFuture);
        }


        private boolean isLatest(SocketAddress remoteAddress) {
            return Objects.equals(logReplicationIndexMap.get(remoteAddress), RaftCore.this.logStorage.lastIndex());
        }

        private boolean isNotLatest(SocketAddress remoteAddress) {
            return !isLatest(remoteAddress);
        }

        private ReplicationLogReq nextReplicationLogRequest(SocketAddress remoteAddress) {
            String id = UUID.randomUUID().toString().replaceAll("-", "");
            Integer logReplicationIndex = logReplicationIndexMap.get(remoteAddress);
            List<Log> logList;
            if (logReplicationIndex == null) {
                logList = RaftCore.this.logStorage.subLast(17);
            } else {
                logList = RaftCore.this.logStorage.sub(logReplicationIndex, 17);
            }
            LogMeta logMeta;
            if (logList.isEmpty()) {
                logMeta = null;
            } else {
                Log preLog = logList.remove(0);
                logMeta = new LogMeta(preLog.getTerm(), preLog.getIndex());
            }
            List<LogEntry> logEntryList = new ArrayList<>(logList.size());
            for (Log log : logList) {
                logEntryList.add(new LogEntry(log.getTerm(), log.getCommand()));
            }
            return new ReplicationLogReq(term, new LogReplicationEntry(logMeta, logEntryList), commitIndex);
        }

    }

}

