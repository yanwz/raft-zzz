package org.example;

import lombok.Getter;

@Getter
public class RoleChangedEvent {

    private NodeRole before;

    private NodeRole after;

    public RoleChangedEvent(NodeRole before, NodeRole after) {
        this.before = before;
        this.after = after;
    }
}
