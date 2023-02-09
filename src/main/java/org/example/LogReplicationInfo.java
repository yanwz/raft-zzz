package org.example;


public class LogReplicationInfo {

    private boolean matched;

    private int index;

    public LogReplicationInfo(int index) {
        this(false,index);
    }

    public LogReplicationInfo(boolean matched, int index) {
        this.matched = matched;
        this.index = index;
    }

    public boolean isMatched() {
        return matched;
    }

    public int getIndex() {
        return index;
    }
}
