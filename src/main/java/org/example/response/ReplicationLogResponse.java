package org.example.response;

import lombok.Getter;
import org.example.Message;

@Getter
public class ReplicationLogResponse extends Message {

    private final int replicationLogIndexByRequest;

    private final boolean success;


    public ReplicationLogResponse(int term,int replicationLogIndexByRequest,boolean success) {
        super(term);
        this.replicationLogIndexByRequest = replicationLogIndexByRequest;
        this.success = success;
    }
}
