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

package org.techlook.net.client.ssl;

import org.techlook.net.client.ChannelListener;
import org.techlook.net.client.Fault;
import org.techlook.net.client.SocketClient;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.concurrent.ForkJoinPool;

class SSLChannel extends AbstractSSLAction implements ChannelListener {
    private final OutgoingAction outgoing;
    private final OutgoingAction outgoingHandshakingAction;
    private volatile ByteBuffer incomingNetData;
    private volatile ByteBuffer incomingAppData;
    private volatile ByteBuffer residueChunk;


    public SSLChannel(final SSLEngine engine,
                      final ChannelListener listener, final ForkJoinPool threadPool, final SocketClient transport) {
        super(engine, listener, threadPool, transport, null);

        incomingNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        incomingAppData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        outgoing = new OutgoingAction(engine, this, threadPool, transport, this);
        outgoingHandshakingAction = new OutgoingHandshakingAction(engine, this, threadPool, transport, this);
    }

    @Override
    public void waitFinishing() {
        outgoing.waitFinishing();
        super.waitFinishing();
    }

    @Override
    public void channelError(String message) {
        listener.channelError(message);
    }

    @Override
    public void chunkIsReceived(byte[] chunk) {
        chunks.add(ByteBuffer.wrap(chunk));
        shakeUp();
    }

    @Override
    public void close() {
        listener.close();
    }

    @Override
    protected void processAction() {
        do {
            if (!processHandshake()) {
                return;
            }

            pullIncomingChunks();
            if (incomingNetData.position() > 0) {
                incomingNetData.flip();
                try {
                    while (handleUnwrap(engine.unwrap(incomingNetData, incomingAppData))) ;
                } catch (SSLException e) {
                    closeOnError(e);
                    break;
                } finally {
                    incomingNetData.compact();
                }
            }
        } while (!chunks.isEmpty() || residueChunk != null);
    }

    @Override
    void setChannelId(Integer channelId) {
        super.setChannelId(channelId);
        outgoing.setChannelId(channelId);
        outgoingHandshakingAction.setChannelId(channelId);
    }

    public void send(final byte[] data, final int offset, final int length) {
        outgoing.send(data, offset, length);
    }

    private void reset() {
        incomingAppData.clear();
        outgoingHandshakingAction.reset();
        outgoing.shakeUp();
    }

    private boolean processHandshake() {
        if (isHandshakingDone()) {
            return true;
        }
        pullIncomingChunks();
        SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
        try {
            while (!isHandshakingDone()) {
                switch (status) {
                    case NEED_WRAP:
                        outgoingHandshakingAction.processOutgoing();
                        break;
                    case NEED_UNWRAP:
                        pullIncomingChunks();
                        if (incomingNetData.position() > 0) {
                            incomingNetData.flip();
                            SSLEngineResult result = engine.unwrap(incomingNetData, incomingAppData);
                            if (result.getStatus() == SSLEngineResult.Status.CLOSED
                                    && engine.isOutboundDone()) {
                                incomingNetData.compact();
                                return false;
                            }
                            handleUnwrap(result);
                            incomingNetData.compact();
                        } else {
                            return false;
                        }
                        break;
                    case NEED_TASK:
                        dispatchBlockingTasks();
                        break;
                    case NOT_HANDSHAKING:
                        engine.beginHandshake();
                        outgoingHandshakingAction.processOutgoing();
                        break;
                    default:
                        throw new IllegalStateException(
                                Fault.SSLError.format("Unknown handshake status:" + status));
                }
                status = engine.getHandshakeStatus();
            }
            if (isHandshakingDone()) {
                logAction(engine.getHandshakeStatus().toString());
                logAction(engine.getSession().getCipherSuite());
                reset();
            }
        } catch (Exception e) {
            closeOnError(e);
            return false;
        }

        return true;
    }

    private boolean handleUnwrap(SSLEngineResult result) {
        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                if (chunks.isEmpty() && residueChunk == null) {
                    return false;
                }
                incomingNetData.compact();
                pullIncomingChunks();
                incomingNetData.flip();
                return incomingNetData.hasRemaining();
            case BUFFER_OVERFLOW:
                tryToSendAlreadyReceivedAppData();
                enlargeAppBuffer();
                return true;
            case OK:
                incomingNetData.compact();
                pullIncomingChunks();
                incomingNetData.flip();
                return tryToSendAlreadyReceivedAppData() && incomingNetData.hasRemaining();
            case CLOSED:
                engine.closeOutbound();
                break;
        }
        return false;
    }

    private boolean tryToSendAlreadyReceivedAppData() {
        if (incomingAppData.position() == 0) {
            return false;
        }
        incomingAppData.flip();
        byte[] chunk = new byte[incomingAppData.limit()];
        incomingAppData.get(chunk);
        incomingAppData.limit(incomingAppData.capacity());
        incomingAppData.position(0);

        listener.chunkIsReceived(chunk);
        return true;
    }

    private void pullIncomingChunks() {
        int packetBufferSize = engine.getSession().getPacketBufferSize();

        synchronized(this) {
            if (incomingNetData.capacity() < packetBufferSize) {
                incomingNetData = enlargeBuffer(incomingNetData, packetBufferSize);
            }
        }

        if (residueChunk != null) {
            int incomingRemaining = incomingNetData.remaining();
            if (residueChunk.remaining() > incomingRemaining) {
                byte[] buffer = new byte[incomingRemaining];
                residueChunk.get(buffer);
                incomingNetData.put(buffer);
                return;
            }

            incomingNetData.put(residueChunk);
            residueChunk = null;
        }

        if (incomingNetData.remaining() == 0) {
            return;
        }

        ByteBuffer chunk;
        int filledSize = incomingNetData.position();
        while (filledSize < packetBufferSize && (chunk = chunks.poll()) != null) {
            filledSize += chunk.limit();
            if (incomingNetData.remaining() < chunk.limit()) {
                residueChunk = chunk;
                return;
            }

            incomingNetData.put(chunk);
        }
    }

    private synchronized void enlargeAppBuffer() {
        incomingAppData = enlargeBuffer(incomingAppData, engine.getSession().getApplicationBufferSize());
    }

    private void dispatchBlockingTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            final Runnable blockingTask = task;
            try {
                ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                    boolean hasFinished = false;

                    @Override
                    public boolean block() {
                        blockingTask.run();
                        hasFinished = true;

                        SSLChannel.this.shakeUp();
                        return true;
                    }

                    @Override
                    public boolean isReleasable() {
                        return hasFinished;
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    boolean hasNoUnprocessedData() {
        return chunks.isEmpty() && residueChunk == null;
    }
}
