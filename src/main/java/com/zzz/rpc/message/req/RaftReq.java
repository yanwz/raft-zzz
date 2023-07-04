package com.zzz.rpc.message.req;

import com.zzz.rpc.message.RaftMessage;

public abstract class RaftReq extends RaftMessage {

    public RaftReq(int term) {
        super(term);
    }
}
