package org.example;

import lombok.Getter;

@Getter
public abstract class Message {

    private final int term;

    public Message(int term) {
        this.term = term;
    }

    public int getTerm() {
        return term;
    }

}
