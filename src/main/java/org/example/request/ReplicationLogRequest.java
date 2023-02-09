package org.example.request;

import lombok.Getter;
import org.example.LogEntry;
import org.example.Message;

import java.util.List;

@Getter
public class ReplicationLogRequest extends Message {

    private int preLogTerm;

    private int preLogIndex;

    private List<LogEntry> entries;

    private int commitIndex;

    public ReplicationLogRequest(int term, int preLogTerm, int preLogIndex, List<LogEntry> entries, int commitIndex) {
        super(term);
        this.preLogTerm = preLogTerm;
        this.preLogIndex = preLogIndex;
        this.entries = entries;
        this.commitIndex = commitIndex;
    }


    public static class entry {

        private int index;

        private int term;

        private byte[] data;

    }
}
