package org.example;


import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class LeaderNodeRole extends NodeRole {

    private Integer commitIndex;

    private Map<InetSocketAddress, LogReplicationInfo> nodeLogReplicationInfoMap = new HashMap<>();

    public LeaderNodeRole(int term) {
        super(term);
    }

    public Integer getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(Integer commitIndex) {
        this.commitIndex = commitIndex;
    }

    public LogReplicationInfo logReplicationInfo(InetSocketAddress address) {
        return nodeLogReplicationInfoMap.get(address);
    }

    public void updateLogReplicationInfo(InetSocketAddress address, LogReplicationInfo logReplicationInfo) {
        nodeLogReplicationInfoMap.put(address, syncIndex);
    }

}
