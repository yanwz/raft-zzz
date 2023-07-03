package com.zzz.call.exception;

public class TypeMismatchException extends RuntimeException{

    public TypeMismatchException() {
    }

    public TypeMismatchException(String message) {
        super(message);
    }
}
