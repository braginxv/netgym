package com.techlook;

public interface AsyncResult<T> {
    void onSuccess(T result);

    void onFailure(Throwable e);
}
