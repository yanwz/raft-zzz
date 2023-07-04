package com.zzz.rpc.message.res;

import lombok.Getter;

@Getter
public class ReplicationLogRes extends RaftRsp {
    private final Boolean matched;

    public ReplicationLogRes(int term, boolean matched) {
        super(term);
        this.matched = matched;
    }
}
