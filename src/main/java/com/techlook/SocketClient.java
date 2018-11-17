package com.techlook;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public interface SocketClient {
    int connect(SocketAddress server, ChannelListener listener) throws IOException;

    int connect(SocketAddress server, ChannelListener listener,
                Integer readBufferSize, Integer writtenBufferSize, Float readBufferFillFactor) throws IOException;

    void send(byte[] data, int offset, int length, Integer channelId);

    void shutdown() throws IOException;

    void close(int channel) throws IOException;

    boolean isConnected(int channel);

    void await() throws ExecutionException, InterruptedException;

    ForkJoinPool getThreadPool();
}
