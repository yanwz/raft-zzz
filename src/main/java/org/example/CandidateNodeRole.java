package org.example;


import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

public class CandidateNodeRole extends NodeRole {

    private final Set<InetSocketAddress> voteGrantedNodes = new HashSet<>();

    public CandidateNodeRole(int term) {
        super(term);
    }

    public void voteGranted(InetSocketAddress address){
         voteGrantedNodes.add(address);
    }

    public int voteGrantedCount(){
        return voteGrantedNodes.size();
    }

}
