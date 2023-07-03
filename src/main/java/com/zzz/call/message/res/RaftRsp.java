package com.zzz.call.message.res;

import com.zzz.call.message.RaftMessage;

public abstract class RaftRsp extends RaftMessage {

    public RaftRsp(int term) {
        super(term);
    }

}
