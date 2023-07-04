package com.zzz.rpc.message.res;

import com.zzz.rpc.message.RaftMessage;

public abstract class RaftRsp extends RaftMessage {

    public RaftRsp(int term) {
        super(term);
    }

}
