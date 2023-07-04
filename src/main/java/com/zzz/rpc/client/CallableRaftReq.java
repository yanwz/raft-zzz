package com.zzz.rpc.client;

import com.zzz.rpc.message.req.RaftReq;
import io.netty.util.concurrent.Promise;
import lombok.Getter;

import java.util.Objects;

@Getter
public class CallableRaftReq {

    private final boolean oneway;

    private final RaftReq raftReq;

    private final Promise<?> promise;


    public CallableRaftReq(RaftReq raftReq, Promise<?> promise) {
        Objects.requireNonNull(raftReq);
        Objects.requireNonNull(promise);
        this.oneway = true;
        this.raftReq = raftReq;
        this.promise = promise;
    }

    public CallableRaftReq(RaftReq raftReq, Promise<?> promise,boolean oneway) {
        Objects.requireNonNull(raftReq);
        Objects.requireNonNull(promise);
        this.oneway = oneway;
        this.raftReq = raftReq;
        this.promise = promise;
    }

}
