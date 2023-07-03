package com.zzz.log;

import lombok.Getter;

@Getter
public class LogMeta {

    private final int term;

    private final int index;

    public LogMeta(int term, int index) {
        if (term < 0 || index < 0) {
            throw new IllegalArgumentException();
        }

        this.index = index;
        this.term = term;
    }

    public boolean isUpdateToDate(LogMeta logMeta) {
        return this.term >= term && this.index >= index;
    }


}
