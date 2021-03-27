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

package org.techlook.nio;

import org.techlook.ChannelListener;
import org.techlook.Fault;
import org.techlook.ResultedCompletion;
import org.techlook.SocketClient;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class AsyncSocketClient implements SocketClient {
    public static final int DEFAULT_READ_BUFFER_SIZE = 0x10000;

    public static final int MINIMUM_THREAD_POOL_SIZE = 4;
    public static final int PARALLELISM_LEVEL = Math.max(Runtime.getRuntime().availableProcessors() + 1, MINIMUM_THREAD_POOL_SIZE);

    public final ForkJoinPool threadPool = new ForkJoinPool(PARALLELISM_LEVEL);

    private final Selector selector;
    private final ConcurrentMap<Integer, ChannelBundle> channels = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final ResultedCompletion<Void> completion = new ResultedCompletion<>();

    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile Future<?> await;
    private volatile boolean dispatched = false;


    private AsyncSocketClient(Selector selector) {
        this.selector = selector;
    }

    public static AsyncSocketClient run() throws IOException {
        return new AsyncSocketClient(Selector.open());
    }

    public ResultedCompletion<Void> completion() {
        return completion;
    }

    @Override
    public int connect(SocketAddress server, ChannelListener listener) throws IOException {
        return connect(server, listener, TransportChannel.TCP, DEFAULT_READ_BUFFER_SIZE);
    }

    @Override
    public int connect(SocketAddress server, ChannelListener listener, TransportChannel transportChannel,
                       int readBufferSize) throws IOException {
        if (!isRunning.get()) {
            throw new IllegalStateException("Cannot connect because client has been stopped");
        }

        int sequenceNumber = counter.incrementAndGet();
        ChannelBundle channelBundle = new ChannelBundle(transportChannel,
                listener,
                sequenceNumber,
                this,
                readBufferSize);

        selector.wakeup();
        transportChannel.createAndConnect(selector, channelBundle, server);
        channels.put(sequenceNumber, channelBundle);

        if (!dispatched) {
            dispatch();
        }

        return sequenceNumber;
    }

    @Override
    public void send(byte[] data, int offset, int length, Integer channelId) {
        ChannelBundle channelBundle = channels.get(channelId);
        channelBundle.appendToWrite(data, offset, length);
    }

    @Override
    public void shutdown() {
        isRunning.set(false);
        selector.wakeup();
        threadPool.shutdown();
    }

    @Override
    public void close(int channel) {
//        channels.remove(channel).close();
        selector.wakeup();
    }

    private void dispatch() {
        Runnable scanning = new Runnable() {
            @Override
            public void run() {
                try {
                    while (isRunning.get()) {
                        int numberOfChannelsReady;

                        try {
                            numberOfChannelsReady = selector.select(1000);
                        } catch (IOException e) {
                            completion.failure(Fault.AsyncClientError.format(e.getMessage()));
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

                            Object attachment = key.attachment();
                            ChannelBundle channelBundle = (ChannelBundle) attachment;
                            channelBundle.setSelectionKey(key);

                            try {
                                if (!channelBundle.hasThisChannelBeenClosed(key.channel())) {
                                    channelBundle.getTransport().finishKeyProcessing(key, channelBundle);
                                }
                            } catch (IOException e) {
                                channelBundle.listener().channelError(Fault.AsyncClientChannelConfigureError.getDescription());
                                channelBundle.closeChannel(key.channel());
                                break;
                            }
                        }
                    }
                    selector.close();
                    completion.finish();
                } catch (Exception e) {
                    String message = e.getMessage() != null ? e.getMessage() : e.toString();
                    completion.failure(Fault.AsyncClientError.format(message));
                }
            }
        };

        await = threadPool.submit(scanning);
        dispatched = true;
    }

    @Override
    public void awaitTerminating() throws ExecutionException, InterruptedException {
        if (await != null) {
            await.get();
        }
    }

    @Override
    public ForkJoinPool getThreadPool() {
        return threadPool;
    }
}