package org.example;

import lombok.Getter;

import java.net.InetSocketAddress;
import java.util.Set;

@Getter
public class ClusterContext {

    private final Set<InetSocketAddress> otherNodes;

    private final Node selfNode;

    public ClusterContext(Set<InetSocketAddress> otherNodes, Node selfNode) {
        this.otherNodes = otherNodes;
        this.selfNode = selfNode;
    }

    public boolean inCluster(InetSocketAddress address) {
        for (InetSocketAddress node : otherNodes) {
            if (node.equals(address)) {
                return true;
            }
        }

        if (selfNode.getAddress().equals(address)) {
            return true;
        }
        return false;
    }

    public int clusterNodeSize(){
        return otherNodes.size() + 1;
    }

}
