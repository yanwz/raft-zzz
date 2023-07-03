package com.zzz.net;

import java.net.SocketAddress;
import java.util.Set;

public class Cluster {

    private final SocketAddress self;

    private final Set<SocketAddress> otherNodes;


    public Cluster(SocketAddress self, Set<SocketAddress> otherNodes) {
        this.self = self;
        this.otherNodes = otherNodes;
    }

    public SocketAddress self() {
        return self;
    }

    public Set<SocketAddress> otherNodes() {
        return otherNodes;
    }

    public int size(){
        return otherNodes.size() + 1;

    }


}
