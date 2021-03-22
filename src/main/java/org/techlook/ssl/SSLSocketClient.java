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

package org.techlook.ssl;

import org.techlook.ChannelListener;
import org.techlook.ResultedCompletion;
import org.techlook.SocketClient;
import org.techlook.nio.TransportChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;


public class SSLSocketClient implements SocketClient {
    private final SocketClient transport;
    private final ConcurrentMap<Integer, SSLChannel> sslChannels = new ConcurrentHashMap<>();

    public SSLSocketClient(SocketClient client) {
        this.transport = client;
    }

    @Override
    public int connect(final SocketAddress server, final ChannelListener listener) throws IOException {
        SSLChannel sslChannel = configureSSLEngine(server, listener);

        int id = transport.connect(server, sslChannel);
        sslChannels.put(id, sslChannel);
        sslChannel.setChannelId(id);
        return id;
    }

    @Override
    public int connect(SocketAddress server, ChannelListener listener, TransportChannel transportChannel,
                       int readBufferSize) throws IOException {
        SSLChannel sslChannel = configureSSLEngine(server, listener);

        int id = transport.connect(server, sslChannel, transportChannel, readBufferSize);
        sslChannels.put(id, sslChannel);
        sslChannel.setChannelId(id);
        return id;
    }

    @Override
    public void send(byte[] data, int offset, int length, Integer channelId) {
        sslChannels.get(channelId).send(data, offset, length);
    }

    @Override
    public void shutdown() {
        transport.shutdown();
    }

    @Override
    public void close(int channel) {
        sslChannels.remove(channel).waitFinishing();
        transport.close(channel);
    }

    @Override
    public void awaitTerminating() throws ExecutionException, InterruptedException {
        transport.awaitTerminating();
    }

    @Override
    public ResultedCompletion<Void> completion() {
        return transport.completion();
    }

    @Override
    public ForkJoinPool getThreadPool() {
        return transport.getThreadPool();
    }

    private SSLChannel configureSSLEngine(SocketAddress server, ChannelListener listener) {
        if (!(server instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Server address should be an InetSocketAddress for SSL async client");
        }

        SSLContext context;
        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create ssl context");
        }

        InetSocketAddress inetAddress = (InetSocketAddress) server;

        SSLEngine engine = context.createSSLEngine(inetAddress.getHostString(), inetAddress.getPort());
        engine.setUseClientMode(true);

        return new SSLChannel(engine, listener, transport.getThreadPool(), transport);
    }
}
