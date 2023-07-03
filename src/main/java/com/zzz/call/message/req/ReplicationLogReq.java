package com.zzz.call.message.req;

import com.zzz.log.LogReplicationEntry;
import lombok.Getter;

@Getter
public class ReplicationLogReq extends RaftReq {

    private final LogReplicationEntry logReplicationEntry;

    private final Integer commitIndex;


    public ReplicationLogReq(int term, LogReplicationEntry logReplicationEntry, int commitIndex) {
        super(term);
        this.logReplicationEntry = logReplicationEntry;
        this.commitIndex = commitIndex;
    }


}
