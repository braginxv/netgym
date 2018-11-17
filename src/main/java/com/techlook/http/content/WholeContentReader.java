package com.techlook.http.content;

import com.techlook.http.HttpListener;

import java.nio.charset.Charset;


public class WholeContentReader extends ContentReader {
    private final int size;
    private int readSize = 0;

    public WholeContentReader(HttpListener listener, Charset charset, Integer size, byte[] buffer) {
        super(listener, charset);
        this.size = size != null ? size : buffer.length;
    }

    @Override
    public void read(byte[] chunk) {
        if (chunk == null) {
            return;
        }

        transmitChunk(chunk);
        readSize += chunk.length;

        if (readSize == size) {
            completeTransmitting();
        }
    }
}
