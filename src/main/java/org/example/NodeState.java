package org.example;

public class NodeState {

    private int term;

    private RoleEnum state;

    public NodeState(int term, RoleEnum state) {
        this.term = term;
        this.state = state;
    }

    public void receiveNodeState(int newTerm, RoleEnum newState) {
        if(newTerm < this.term){
            return;
        }

        if(newTerm == this.term  ){
            if(newState == this.state){
                return;
            }
        }
        this.term = term;
        this.state = state;
    }

}
