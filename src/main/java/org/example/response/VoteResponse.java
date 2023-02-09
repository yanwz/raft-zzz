package org.example.response;

import lombok.Getter;
import org.example.Message;

@Getter
public class VoteResponse extends Message {

    private final int voteTermByRequest;

    private final boolean voteGranted;

    public VoteResponse(int term,int voteTermByRequest, boolean voteGranted) {
        super(term);
        this.voteTermByRequest = voteTermByRequest;
        this.voteGranted = voteGranted;
    }

}
