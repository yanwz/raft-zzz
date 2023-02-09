package org.example;

import java.net.InetSocketAddress;

public class FollowerNodeRole extends NodeRole{

    private InetSocketAddress votedFor;

    public FollowerNodeRole(int term) {
        super(term);
    }

    @Override
    public RoleEnum getRole() {
        return RoleEnum.FOLLOWER;
    }

    public InetSocketAddress getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(InetSocketAddress votedFor) {
        if(this.votedFor != null){
            throw new UnsupportedOperationException();
        }
        this.votedFor = votedFor;
    }
}
