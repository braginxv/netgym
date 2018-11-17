package com.techlook;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;


public class AsyncSocketClient implements SocketClient {
    public static final int DEFAULT_WRITE_BUFFER_LENGTH = 0x50_000;
    public static final int DEFAULT_READ_BUFFER_LENGTH = 0x50;
    public static final float DEFAULT_READ_BUFFER_FILL_FACTOR = 0.5f;

    public static final int MINIMUM_THREAD_POOL_SIZE = 4;
    public static final int PARALLELISM_LEVEL = Math.max(Runtime.getRuntime().availableProcessors() + 1, MINIMUM_THREAD_POOL_SIZE);

    public final ForkJoinPool threadPool = new ForkJoinPool(PARALLELISM_LEVEL);

    private final Selector selector;
    private final ClientListener listener;
    private final Map<Integer, ChannelBundle> channels = new HashMap<>();

    private volatile Integer counter = 0;
    private volatile boolean isRunning = true;
    private volatile Future<?> await;
    private ReentrantLock selectorLock = new ReentrantLock();
    private boolean dispatched = false;


    private AsyncSocketClient(Selector selector, ClientListener listener) {
        this.selector = selector;
        this.listener = listener;
    }

    public static AsyncSocketClient create(ClientListener listener) throws IOException {
        return new AsyncSocketClient(Selector.open(), listener);
    }

    @Override
    public int connect(SocketAddress server, ChannelListener listener) throws IOException {
        return connect(server, listener, null, null, null);
    }

    @Override
    public int connect(SocketAddress server, ChannelListener listener,
                       Integer readBufferSize, Integer writtenBufferSize, Float readBufferFillFactor) throws IOException {
        if (!isRunning) {
            throw new IllegalStateException("Cannot connect because client has been stopped");
        }
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);

        selectorLock.lock();

        counter++;
        channels.put(counter, new ChannelBundle(
                counter,
                channel,
                listener,
                readBufferSize == null ? DEFAULT_READ_BUFFER_LENGTH : readBufferSize,
                writtenBufferSize == null ? DEFAULT_WRITE_BUFFER_LENGTH : writtenBufferSize,
                readBufferFillFactor == null ? DEFAULT_READ_BUFFER_FILL_FACTOR : readBufferFillFactor));

        selector.wakeup();
        channel.register(selector, SelectionKey.OP_CONNECT, counter);
        channel.connect(server);

        if (!dispatched) {
            dispatch();
        }

        selectorLock.unlock();

        return counter;
    }

    @Override
    public void send(byte[] data, int offset, int length, Integer channelId) {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            listener.asyncClientError(Fault.AsyncClientError);
        }
        ChannelBundle channelBundle = channels.get(channelId);
        channelBundle.appendToWrite(data, offset, length);
        channelBundle.maybeWritten();
    }

    @Override
    public void shutdown() throws IOException {
        selectorLock.lock();

        isRunning = false;
        selector.wakeup();

        selectorLock.unlock();
        threadPool.shutdown();
    }

    @Override
    public void close(int channel) throws IOException {
        selectorLock.lock();

        channels.get(channel).channel().close();

        selectorLock.unlock();
    }

    @Override
    public boolean isConnected(int channel) {
        return channels.get(channel).channel().isConnected();
    }

    private void dispatch() {
        Runnable scanning = new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    int numberOfChannelsReady;

                    try {
                        selectorLock.lock();
                        selectorLock.unlock();

                        numberOfChannelsReady = selector.select();
                    } catch (IOException e) {
                        listener.asyncClientError(Fault.AsyncClientError);

                        return;
                    }

                    if (numberOfChannelsReady <= 0) {
                        continue;
                    }
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        SelectableChannel channel = key.channel();
                        ChannelBundle channelBundle = channels.get(key.attachment());

                        if (key.isConnectable()) {
                            try {
                                if (!channelBundle.channel().finishConnect()) {
                                    channelBundle.listener().channelError(Fault.AsyncClientChannelConnectError);
                                    channelBundle.close();

                                    continue;
                                }
                                channel.configureBlocking(false);
                                channel.register(selector, SelectionKey.OP_READ, channelBundle.id);
                                channelBundle.connected();
                                channelBundle.maybeWritten();
                            } catch (IOException e) {
                                channelBundle.listener().channelError(Fault.AsyncClientChannelConfigureError);
                                channelBundle.close();

                                continue;
                            }
                        }

                        if (key.isReadable()) {
                            read(channelBundle);
                        }

                        if (key.isWritable()) {
                            write(channelBundle);
                        }
                    }
                }
                try {
                    selector.close();
                } catch (IOException e) {
                    listener.asyncClientError(Fault.AsyncClientError);
                }
            }
        };

        await = threadPool.submit(scanning);
        dispatched = true;
    }

    @Override
    public void await() throws ExecutionException, InterruptedException {
        if (await != null) {
            await.get();
        }
    }

    private void read(ChannelBundle channel) {
        channel.read();
    }

    private void write(ChannelBundle channel) {
        channel.write();
    }

    public interface ClientListener {
        void asyncClientError(Fault error, Object ...args);
    }

    @Override
    public ForkJoinPool getThreadPool() {
        return threadPool;
    }

    private class ChannelBundle {
        private final int id;
        private final SocketChannel channel;
        private final ChannelListener listener;
        private final ByteBuffer readBuffer;
        private final ByteBuffer writtenBuffer;
        private final float readBufferFillFactor;
        private boolean isConnected = false;


        ChannelBundle(
                int id,
                SocketChannel channel,
                ChannelListener listener,
                int readBufferSize,
                int writtenBufferSize,
                float readBufferFillFactor) {
            this.id = id;
            this.channel = channel;
            this.listener = listener;
            this.readBufferFillFactor = readBufferFillFactor;

            readBuffer = ByteBuffer.allocateDirect(readBufferSize);
            writtenBuffer = ByteBuffer.allocateDirect(writtenBufferSize);
        }

        void appendToWrite(byte[] buffer, int offset, int length) {
            synchronized (writtenBuffer) {
                int remaining = writtenBuffer.remaining();

                if (remaining - length >= 0) {
                    writtenBuffer.put(buffer, offset, length);

                    return;
                }

                while (remaining - length < 0 && channel.isConnected()) {
                    remaining = writtenBuffer.remaining();
                    writtenBuffer.put(buffer, offset, remaining);
                    length -= remaining;
                    offset += remaining;

                    try {
                        writtenBuffer.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        void read() {
            synchronized (readBuffer) {
                try {
                    if (channel.read(readBuffer) < 0) {
                        close();
                        respond();
                    }
                } catch (IOException e) {
                    close();
                    listener.channelError(Fault.AsyncClientChannelReadError);

                    return;
                }

                if (1.0f * readBuffer.remaining() / readBuffer.capacity() >= readBufferFillFactor) {
                    respond();
                }
            }
        }

        void write() {
            synchronized (writtenBuffer) {
                writtenBuffer.flip();
                try {
                    channel.write(writtenBuffer);

                    if (writtenBuffer.hasRemaining()) {
                        writtenBuffer.compact();
                    } else {
                        selectorLock.lock();
                        selector.wakeup();
                        channel.register(selector, SelectionKey.OP_READ, id);
                        writtenBuffer.clear();
                    }

                } catch (IOException e) {
                    listener.channelError(Fault.AsyncClientChannelWriteError);

                    close();
                } finally {
                    selectorLock.unlock();
                    writtenBuffer.notify();
                }
            }
        }

        void close() {
            try {
                channel.close();
            } catch (IOException ignored) {
            } finally {
                channels.remove(id);
                listener.close();
            }
        }

        void respond() {
            if (readBuffer.position() > 0) {
                readBuffer.flip();

                byte[] response = new byte[readBuffer.limit()];
                readBuffer.get(response);
                readBuffer.clear();

                listener.response(response);
            }
        }

        void maybeWritten() {
            try {
                selectorLock.lock();
                selector.wakeup();
                channel.register(
                        selector,
                        isConnected && writtenBuffer.hasRemaining() ? SelectionKey.OP_WRITE : SelectionKey.OP_READ,
                        id);
            } catch (ClosedChannelException e) {
                listener.channelError(Fault.AsyncClientChannelWriteError);
            } finally {
                selectorLock.unlock();
            }
        }

        void connected() {
            isConnected = true;
        }

        SocketChannel channel() {
            return channel;
        }

        ChannelListener listener() {
            return listener;
        }
    }
}
