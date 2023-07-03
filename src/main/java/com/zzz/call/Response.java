package com.zzz.call;


import com.zzz.call.message.res.RaftRsp;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Response {

    private final String id;

    private final boolean success;

    private final Integer errorCode;

    private final RaftRsp content;


    public Response(String id, RaftRsp content) {
        this.id = id;
        this.success = true;
        this.errorCode = null;
        this.content = content;
    }

    public Response(String id, int errorCode) {
        this.id = id;
        this.success = false;
        this.errorCode = errorCode;
        this.content = null;
    }
}
