package com.zzz.log;

import lombok.Getter;

@Getter
public class Log {
    private final int index;
    private final int term;
    private final byte[] command;

    public Log(int index, int term, byte[] command) {
        this.index = index;
        this.term = term;
        this.command = command;
    }
}
