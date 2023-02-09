package org.example;

public class DerbyLogSystem implements LogSystem{

    @Override
    public int append(int term, byte[] command) {
        return 0;
    }

    @Override
    public LogEntry get(int index) {
        return null;
    }

    @Override
    public LogEntry getLast() {
        return null;
    }

    @Override
    public LogEntry[] sub(int endIndex, int size) {
        return new LogEntry[0];
    }

    @Override
    public int size() {
        return 0;
    }
}
