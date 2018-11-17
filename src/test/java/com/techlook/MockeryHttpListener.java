package com.techlook;

import com.techlook.http.HttpListener;

import java.nio.charset.Charset;

public class MockeryHttpListener implements HttpListener {
    private final StringBuilder response = new StringBuilder();
    private final ResultedCompletion<String> progress;


    public MockeryHttpListener(ResultedCompletion<String> completion) {
        progress = completion;
    }

    @Override
    public void response(byte[] content, Charset charset) {
        response.append(new String(content, charset));
    }

    @Override
    public void complete() {
        progress.finish(response.toString());
    }

    @Override
    public void failure(Fault fault, Object... args) {
        progress.failure(fault.format(args));
    }
}
