package org.example;

public class LogContext {

    private Log lastLog;

    public Log appendLastLog(LogEntry logEntry) {
        this.lastLog = new Log(lastLog.getIndex() + 1, logEntry);
        return this.lastLog;
    }

    public boolean syncLog(int preLogTerm, int preLogIndex, LogEntry[] logEntry) {
        return false;
    }

    public int getLastLogTerm() {
        return lastLog.getEntry().getTerm();
    }

    public int getLastLogIndex() {
        return lastLog.getIndex();
    }

}
