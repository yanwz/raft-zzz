package com.zzz.call;

import com.zzz.call.exception.TypeMismatchException;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.TypeParameterMatcher;

public abstract class TypeParameterPromise<V> extends DefaultPromise<V> {

    private final TypeParameterMatcher matcher;

    public TypeParameterPromise(EventExecutor executor) {
        super(executor);
        this.matcher = TypeParameterMatcher.find(this, SimpleChannelInboundHandler.class, "I");
    }

    public boolean matchType(Object result) {
        return result == null || matcher.match(result);
    }

    @Override
    public Promise<V> setSuccess(V result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trySuccess(V result) {
        if (matchType(result)) {
            return super.trySuccess(result);
        }else {
            super.tryFailure(new TypeMismatchException());
            return false;
        }
    }

}
