package com.zzz.call.message.req;

import com.zzz.log.LogMeta;
import lombok.Getter;


@Getter
public class PreVoteReq extends RaftReq {

    private final LogMeta lastLogMeta;

    public PreVoteReq(int term,LogMeta lastLogMeta) {
        super(term);
        this.lastLogMeta = lastLogMeta;
    }

}
