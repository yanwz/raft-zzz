package com.zzz.call.message.res;

import lombok.Getter;

@Getter
public class VoteRes extends RaftRsp {

    private final Boolean voteGranted;

    public VoteRes(int term, boolean voteGranted) {
        super(term);
        this.voteGranted = voteGranted;
    }


}
