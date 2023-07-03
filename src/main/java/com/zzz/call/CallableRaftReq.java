package com.zzz.call;

import com.zzz.call.message.req.RaftReq;
import io.netty.util.concurrent.Promise;
import lombok.Getter;

import java.util.Objects;

@Getter
public class CallableRaftReq {

    private final RaftReq raftReq;

    private final Promise<Object> promise;

    public CallableRaftReq(RaftReq raftReq, Promise<Object> promise) {
        Objects.requireNonNull(raftReq);
        Objects.requireNonNull(promise);
        this.raftReq = raftReq;
        this.promise = promise;
    }

}
