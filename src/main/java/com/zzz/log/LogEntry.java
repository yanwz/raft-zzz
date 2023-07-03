package com.zzz.log;

import lombok.Getter;

@Getter
public class LogEntry {

    private final int term;
    private final byte[] command;

    public LogEntry(int term, byte[] command) {
        this.term = term;
        this.command = command;
    }
}
