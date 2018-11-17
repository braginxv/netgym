package com.techlook.http.content;

import com.techlook.Fault;
import com.techlook.http.HttpListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.List;


public abstract class ContentReader implements WritableByteChannel {
    protected final HttpListener listener;
    private final Charset charset;
    private WritableByteChannel sink;
    private boolean isOpen = true;

    public ContentReader(final HttpListener listener, final Charset charset) {
        this.listener = listener;
        this.charset = charset;

        sink = this;
    }

    public abstract void read(byte[] chunk);

    public void setDecoders(List<Decoder> decoders) {
        if (decoders == null || decoders.isEmpty()) {
            return;
        }

        int index = decoders.size() - 1;
        InflaterChannel channel = decoders.get(index).createChannel(this);

        while (--index >= 0) {
            channel = decoders.get(index).createChannel(channel);
        }

        sink = channel;
    }

    void transmitChunk(final byte[] chunk) {
        try {
            sink.write(ByteBuffer.wrap(chunk));
        } catch (IOException e) {
            listener.failure(Fault.AsyncClientChannelWriteError, e);
        }
    }

    void completeTransmitting() {
        try {
            sink.close();
        } catch (Exception e) {
            listener.failure(Fault.AsyncClientError, String.format("%s: '%s'", e.toString(), e.getMessage()));
        }
        listener.complete();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int size = src.remaining();
        if (size > 0) {
            byte[] block = new byte[size];
            src.get(block);
            listener.response(block, charset);
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
