package org.example;

import java.util.concurrent.atomic.AtomicReference;

public class NodeRoleContext {

    private final AtomicReference<NodeRole> role;

    public NodeRoleContext(NodeRole role) {
        this.role = new AtomicReference<>(role);
    }

    public NodeRole getRole() {
        return role.get();
    }

    public void setRole(NodeRole newRole) {
        role.set(newRole);
    }

    public boolean casRole(NodeRole oldRole, NodeRole newRole) {
         return role.compareAndSet(oldRole, newRole);
    }

}

