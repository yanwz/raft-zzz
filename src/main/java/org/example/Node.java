package org.example;

import lombok.Getter;

import java.net.InetSocketAddress;
import java.util.Objects;

@Getter
public class Node {

    private InetSocketAddress address;

    public Node(InetSocketAddress address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(address, node.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
