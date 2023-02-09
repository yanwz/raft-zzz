package org.example;

import lombok.Getter;

@Getter
public class NodeInfo {

    private int term;

    private RoleEnum state;

    private Node voteFor;

}
