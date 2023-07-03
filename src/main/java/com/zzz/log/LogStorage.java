package com.zzz.log;

import java.util.List;

public interface LogStorage extends AutoCloseable{

    int append(int term, byte[] command);

    boolean replication(int preLogTerm,int preLogIndex,List<LogEntry> entries);

    Log get(int index);

    LogMeta getLastLogMeta();

    List<Log> sub(int index, int size);

    List<Log> subLast(int size);

    Integer lastIndex();

}
