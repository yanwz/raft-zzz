package com.zzz.rpc.client.exception;

public class ErrorCodeException extends RuntimeException {
    private final Integer errorCode;

    public ErrorCodeException(Integer errorCode) {
        this.errorCode = errorCode;
    }
}
