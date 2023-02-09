package org.example.request;

import lombok.Getter;
import org.example.Message;

@Getter
public class VoteRequest extends Message {

    private int lastLogTerm;

    private int lastLogIndex;

    public VoteRequest(int term, int lastLogTerm, int lastLogIndex) {
        super(term);
        this.lastLogTerm = lastLogTerm;
        this.lastLogIndex = lastLogIndex;
    }

}
