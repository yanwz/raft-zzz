package org.example;

import lombok.Getter;

@Getter
public class LogTermAndIndex {

    private int term;

    private int index;


    public LogTermAndIndex(int term, int index) {
        this.term = term;
        this.index = index;
    }
}
