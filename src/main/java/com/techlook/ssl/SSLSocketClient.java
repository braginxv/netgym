package com.techlook.ssl;

import com.techlook.AsyncSocketClient;
import com.techlook.ChannelListener;
import com.techlook.Fault;
import com.techlook.SocketClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;


public class SSLSocketClient implements SocketClient {
    private final AsyncSocketClient client;
    private final SSLContext context;
    private final ForkJoinPool threadPool = new ForkJoinPool();
    private final ConcurrentMap<Integer, SSLChannel> sslChannels = new ConcurrentHashMap<>();

    public SSLSocketClient(AsyncSocketClient client, SSLContext context) {
        this.client = client;
        this.context = context;
    }

    @Override
    public int connect(final SocketAddress server, final ChannelListener listener) throws IOException {
        SSLChannel sslChannel = configureSSLEngine(server, listener);

        int id = client.connect(server, sslChannel);
        sslChannel.setChannelId(id);

        return id;
    }

    @Override
    public int connect(SocketAddress server, ChannelListener listener, Integer readBufferSize, Integer writtenBufferSize, Float readBufferFillFactor) throws IOException {
        SSLChannel sslChannel = configureSSLEngine(server, listener);

        int id = client.connect(server, sslChannel, readBufferSize, writtenBufferSize, readBufferFillFactor);
        sslChannel.setChannelId(id);

        return id;
    }

    @Override
    public void send(byte[] data, int offset, int length, Integer channelId) {
    }

    @Override
    public void shutdown() throws IOException {
        client.shutdown();
    }


    @Override
    public void close(int channel) {
        try {
            client.close(channel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isConnected(int channel) {
        return false;
    }

    @Override
    public void await() throws ExecutionException, InterruptedException {
        client.await();
    }

    @Override
    public ForkJoinPool getThreadPool() {
        return client.threadPool;
    }

    private SSLChannel configureSSLEngine(SocketAddress server, ChannelListener listener) {
        if (!(server instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Server address should be an InetSocketAddress for SSL async client");
        }
        InetSocketAddress inetAddress = (InetSocketAddress) server;

        SSLEngine engine = context.createSSLEngine(inetAddress.getHostString(), inetAddress.getPort());
        engine.setUseClientMode(true);

        SSLChannel sslChannel = new SSLChannel(engine, listener);
        sslChannel.beginHandshake();

        return sslChannel;
    }

    private class SSLChannel implements ChannelListener {
        private final SSLEngine engine;
        private Integer channelId;
        private final ChannelListener listener;

        public SSLChannel(SSLEngine engine, final ChannelListener listener) {
            this.engine = engine;
            this.listener = listener;

        }

        @Override
        public void channelError(Fault error) {
        }

        @Override
        public void response(byte[] chunk) {
        }

        @Override
        public void close() {
        }

        void setChannelId(Integer channelId) {
            this.channelId = channelId;
            sslChannels.put(channelId, this);
        }

        void beginHandshake() {
            try {
                engine.beginHandshake();

                processHandshake();
            } catch (SSLException e) {
                listener.channelError(Fault.SSLError);
            }
        }

        private boolean processHandshake() {
            SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
            if (status == SSLEngineResult.HandshakeStatus.FINISHED) {
                return true;
            }

            switch (status) {
                case NEED_WRAP:
                    break;
                case NEED_TASK:
                    dispatchBlockingTasks();
                    break;
                case NOT_HANDSHAKING:
                    break;
            }

            return true;
        }

        private void dispatchBlockingTasks() {
            Runnable task;
            while ((task = engine.getDelegatedTask()) != null) {
                final Runnable blockingTask = task;
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                                @Override
                                public boolean block() throws InterruptedException {
                                    blockingTask.run();

                                    return true;
                                }

                                @Override
                                public boolean isReleasable() {
                                    return false;
                                }
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }

        private void unwrap() {
        }
    }
}
