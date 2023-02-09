package org.example;

import java.util.List;

public interface LogSystem {

    int append(int term, byte[] command);

    boolean replication(int preLogTerm,int preLogIndex,List<LogEntry> entries);

    LogEntry get(int index);

    LogEntry getLast();

    List<LogEntry> sub(int index, int size);

    int size();

}
