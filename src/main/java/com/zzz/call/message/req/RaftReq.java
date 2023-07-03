package com.zzz.call.message.req;

import com.zzz.call.message.RaftMessage;

public abstract class RaftReq extends RaftMessage {

    public RaftReq(int term) {
        super(term);
    }
}
