package com.zzz.net;

import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.handler.ipfilter.IpFilterRuleType;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

public class ClusterIpFilterRule implements IpFilterRule {

    private final Cluster cluster;

    public ClusterIpFilterRule(Cluster cluster) {
        this.cluster = cluster;
    }
    @Override
    public boolean matches(InetSocketAddress inetSocketAddress) {
        Set<SocketAddress> socketAddresses = cluster.otherNodes();
        return socketAddresses.contains(inetSocketAddress);
    }

    @Override
    public IpFilterRuleType ruleType() {
        return IpFilterRuleType.ACCEPT;
    }
}
