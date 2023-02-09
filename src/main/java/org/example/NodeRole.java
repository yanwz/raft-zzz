package org.example;

import lombok.Getter;

@Getter
public abstract class NodeRole {

    private final int term;

    private boolean isExpire = false;

    public NodeRole(int term) {
        this.term = term;
    }

    public void expire(){
        this.isExpire = true;
    }

    public boolean isExpire(){
        return this.isExpire;
    }

}
