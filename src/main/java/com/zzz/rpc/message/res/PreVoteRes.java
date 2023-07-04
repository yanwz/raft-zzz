package com.zzz.rpc.message.res;

import lombok.Getter;

@Getter
public class PreVoteRes extends RaftRsp {

    private final Boolean voteGranted;

    public PreVoteRes(int term, boolean voteGranted) {
        super(term);
        this.voteGranted = voteGranted;
    }


}
