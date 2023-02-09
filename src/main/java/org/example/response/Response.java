package org.example.response;

import org.example.Message;

public class Response extends Message{

    private final String requestId;

    public Response(int term, String requestId) {
        super(term);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
