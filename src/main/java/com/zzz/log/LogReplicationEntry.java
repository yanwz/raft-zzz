package com.zzz.log;

import java.util.List;
import java.util.Objects;

public class LogReplicationEntry {

    private final LogMeta preLogMeta;

    private final List<LogEntry> entries;


    public LogReplicationEntry(LogMeta preLogMeta, List<LogEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        this.preLogMeta = preLogMeta;
        this.entries = entries;
    }

    public List<LogEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public LogMeta getPreLogMeta() {
        return preLogMeta;
    }

    public Integer getPreIndex() {
        if (preLogMeta == null) {
            return null;
        }
        return preLogMeta.getIndex();
    }

    public Integer getFirstIndex() {
        if(entries.isEmpty()){
            return null;
        }
        Integer preIndex = getPreIndex();
        return preIndex == null ? 1 : preIndex + 1;
    }


    public Integer getLastIndex() {
        if(entries.isEmpty()){
            return null;
        }
        Integer preIndex = getPreIndex();
        return preIndex == null ? 1 : preIndex + entries.size();
    }

}
