/*
 * The MIT License
 *
 * Copyright (c) 2021 Vladimir Bragin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.techlook.http.content;

import org.techlook.Fault;
import org.techlook.http.client.HttpListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.concurrent.ForkJoinPool;


public abstract class ContentReader implements WritableByteChannel {
    protected final HttpListener listener;
    private final ForkJoinPool threadPool;
    private volatile WritableByteChannel sink;
    private volatile boolean isOpen = true;

    public ContentReader(final HttpListener listener, List<Decoder> decoders, ForkJoinPool threadPool) {
        this.listener = listener;
        this.threadPool = threadPool;

        sink = this;

        setDecoders(decoders);
    }

    public abstract void read(byte[] chunk);

    private void setDecoders(List<Decoder> decoders) {
        if (decoders == null || decoders.isEmpty()) {
            return;
        }

        int index = decoders.size() - 1;
        InflaterChannel channel = decoders.get(index).createChannel(this, threadPool);

        while (--index >= 0) {
            channel = decoders.get(index).createChannel(channel, threadPool);
        }

        sink = channel;
    }

    void transmitChunk(final byte[] chunk) {
        try {
            sink.write(ByteBuffer.wrap(chunk));
        } catch (IOException e) {
            listener.failure(Fault.AsyncClientChannelWriteError.format(e.getMessage()));
        }
    }

    void completeTransmitting() {
        try {
            sink.close();
        } catch (Exception e) {
            listener.failure(Fault.AsyncClientError.format(String.format("%s: '%s'", e.toString(), e.getMessage())));
            return;
        }
        listener.complete();
    }

    @Override
    public int write(ByteBuffer src) {
        int size = src.remaining();
        if (size > 0) {
            byte[] block = new byte[size];
            src.get(block);
            listener.respond(block);
        }

        src.compact();
        return size;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() {
        isOpen = false;
    }
}
