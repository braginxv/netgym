package com.techlook.http;

import com.techlook.Fault;

import java.nio.charset.Charset;

public interface HttpListener {
    void response(byte[] content, Charset charset);

    void complete();

    void failure(Fault fault, Object... args);
}
