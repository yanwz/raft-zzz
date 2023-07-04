package com.zzz.rpc.message.req;

import com.zzz.log.LogMeta;
import lombok.Getter;

@Getter
public class VoteReq extends RaftReq {
    private final LogMeta lastLogMeta;

    public VoteReq(int term, LogMeta lastLogMeta) {
        super(term);
        this.lastLogMeta = lastLogMeta;
    }


}
