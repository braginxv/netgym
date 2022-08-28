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
import org.techlook.SocketClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelBundle {
    private final TransportChannel transport;
    private final ChannelListener listener;
    private final Integer channelId;
    private final SocketClient socketClient;
    private final int readBufferSize;
    private final AtomicBoolean shouldBeClosed = new AtomicBoolean(false);
    private final AtomicBoolean hasBeenClosed = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();
    private volatile SelectionKey selectionKey;
    private volatile ByteBuffer writtenResidueBuffer;


    public ChannelBundle(TransportChannel transport,
                         ChannelListener listener,
                         Integer channelId,
                         SocketClient socketClient,
                         int readBufferSize) {
        this.listener = listener;
        this.transport = transport;
        this.channelId = channelId;
        this.socketClient = socketClient;
        this.readBufferSize = readBufferSize;
    }

    void appendToWrite(byte[] buffer, int offset, int length) {
        if (!shouldBeClosed.get()) {
            chunks.add(ByteBuffer.wrap(buffer, offset, length));
            acceptWritingMessages();
        }
    }

    boolean shouldWrite() {
        return !chunks.isEmpty();
    }

    void read(ReadableByteChannel channel) {
        if (hasThisChannelBeenClosed(channel)) return;

        int readBytesNumber;
        ByteBuffer readBuffer = ByteBuffer.allocate(readBufferSize);
        do {
            try {
                readBytesNumber = channel.read(readBuffer);
                if (readBytesNumber < 0) {
                    closeChannel(channel);
                    return;
                } else if (readBytesNumber > 0) {
                    respond(readBuffer);
                }
            } catch (IOException e) {
                hasBeenClosed.set(true);
                listener.channelError(Fault.AsyncClientChannelReadError.getDescription());

                return;
            }
        } while (readBytesNumber > 0);
    }

    void write(WritableByteChannel channel) {
        if (hasThisChannelBeenClosed(channel)) return;

        ByteBuffer chunk;
        if (writtenResidueBuffer != null) {
            if (writeChunkFully(channel, writtenResidueBuffer)) {
                writtenResidueBuffer = null;
            } else {
                return;
            }
        }
        while ((chunk = chunks.poll()) != null) {
            if (!writeChunkFully(channel, chunk)) {
                writtenResidueBuffer = chunk;
                break;
            }
        }
    }

    private boolean writeChunkFully(WritableByteChannel channel, ByteBuffer chunk) {
        try {
            int size = chunk.remaining();
            return size == channel.write(chunk);
        } catch (IOException e) {
            listener.channelError(Fault.AsyncClientChannelWriteError.getDescription());
            closeChannel(channel);
            return false;
        }
    }

    void close() {
        shouldBeClosed.set(true);
    }

    ChannelListener listener() {
        return listener;
    }

    TransportChannel getTransport() {
        return transport;
    }

    void closeChannel(Channel channel) {
        try {
            if (channel.isOpen()) channel.close();
        } catch (IOException e) {
            listener.channelError(Fault.AsyncClientChannelClosingError.format(e.getMessage()));
        } finally {
            socketClient.close(channelId);
            listener.close();
            hasBeenClosed.set(true);
        }
    }

    boolean hasThisChannelBeenClosed(Channel channel) {
        if (shouldBeClosed.get()) {
            if (!hasBeenClosed.get()) {
                closeChannel(channel);
                return true;
            }
        }
        return false;
    }

    void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    private void respond(ByteBuffer buffer) {
        if (buffer.position() > 0) {
            buffer.flip();

            byte[] response = new byte[buffer.limit()];
            buffer.get(response);
            buffer.clear();

            listener.chunkIsReceived(response);
        }
    }

    private void acceptWritingMessages() {
        if (selectionKey != null) {
            if (!selectionKey.isValid()) {
                close();
            } else {
                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
            }
        }
    }
}
