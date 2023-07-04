package com.zzz.rpc.client.exception;

public class ErrorResException extends RuntimeException {
    private final Integer errorCode;

    public ErrorResException(Integer errorCode) {
        this.errorCode = errorCode;
    }
}
