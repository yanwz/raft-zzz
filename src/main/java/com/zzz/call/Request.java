package com.zzz.call;

import com.zzz.call.message.req.RaftReq;
import lombok.Getter;

import java.util.Objects;

@Getter
public class Request {

    private final String id;

    private final RaftReq content;


    public Request(String id, RaftReq content) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(content);
        this.id = id;
        this.content = content;
    }
}
