package org.example;

import lombok.Getter;

@Getter
public class Log {

    private int index;

    private LogEntry entry;

    public Log(int index, LogEntry entry) {
        this.index = index;
        this.entry = entry;
    }
}
