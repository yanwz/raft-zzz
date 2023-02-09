package org.example;

import lombok.Getter;

@Getter
public class LogEntry {

    private final int index;

    private final int term;

    private final byte[] command;

    public LogEntry(int term,int index, byte[] command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }
}
