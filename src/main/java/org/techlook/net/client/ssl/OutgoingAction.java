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

import org.techlook.net.client.AsyncAction;
import org.techlook.net.client.ChannelListener;
import org.techlook.net.client.SocketClient;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.concurrent.ForkJoinPool;

class OutgoingAction extends AbstractSSLAction {
    protected final SSLEngine engine;
    protected ByteBuffer outgoingAppData;

    protected volatile ByteBuffer outgoingNetData;
    protected volatile ByteBuffer residueChunk;

    public OutgoingAction(SSLEngine engine,
                          ChannelListener listener, ForkJoinPool threadPool, SocketClient transport, AsyncAction hostAction) {
        super(engine, listener, threadPool, transport, hostAction);
        this.engine = engine;
        outgoingAppData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        outgoingNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        residueChunk = ByteBuffer.allocate(0);
    }

    @Override
    protected void processAction() {
        if (!isHandshakingDone()) {
            hostAction.shakeUp();
            return;
        }

        processOutgoing();
    }

    void reset() {
        outgoingAppData.clear();
        outgoingNetData.clear();
    }

    void processOutgoing() {
        fillAppBuffer();
        outgoingAppData.flip();
        while (outgoingAppData.hasRemaining()) {
            if (!isHandshakingDone()) {
                outgoingAppData.compact();
                return;
            }
            try {
                handleWrap(engine.wrap(outgoingAppData, outgoingNetData));
            } catch (SSLException e) {
                closeOnError(e);
                return;
            }
        }
        outgoingAppData.compact();
    }

    void send(byte[] data, int offset, int length) {
        this.chunks.add(ByteBuffer.wrap(data, offset, length));
        shakeUp();
    }

    protected void handleWrap(SSLEngineResult result) {
        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                fillAppBuffer();
                break;
            case BUFFER_OVERFLOW:
                enlargeOutgoingNetBuffer();
                break;
            case OK:
                if (outgoingNetData.position() == 0) {
                    break;
                }
                outgoingNetData.flip();
                byte[] chunk = new byte[outgoingNetData.limit()];
                outgoingNetData.get(chunk);
                outgoingNetData.clear();
                transport.send(chunk, 0, chunk.length, channelId);
                fillAppBuffer();
                break;
            case CLOSED:
                break;
        }
    }

    private void fillAppBuffer() {
        if (residueChunk.position() > 0) {
            residueChunk.flip();
            if (hasResidueEnoughToProcessChunk(residueChunk)) {
                return;
            }
        }

        ByteBuffer chunk;
        while ((chunk = this.chunks.poll()) != null && !hasResidueEnoughToProcessChunk(chunk)) ;
    }

    private boolean hasResidueEnoughToProcessChunk(ByteBuffer chunk) {
        int remaining = outgoingAppData.remaining();
        if (remaining < chunk.remaining()) {
            byte[] data = new byte[remaining];
            chunk.get(data);
            outgoingAppData.put(data);
            data = new byte[chunk.remaining()];
            chunk.get(data);
            chunk.clear();
            putResidue(data);
            return true;
        }
        outgoingAppData.put(chunk);
        return false;
    }

    private void putResidue(byte[] block) {
        if (block.length > residueChunk.remaining()) {
            residueChunk.flip();
            ByteBuffer newResidue = ByteBuffer.allocate(residueChunk.position() + block.length);
            newResidue.put(residueChunk);
            newResidue.put(block);
            residueChunk = newResidue;
        }
    }

    protected void enlargeOutgoingNetBuffer() {
        outgoingNetData = enlargeBuffer(outgoingNetData, engine.getSession().getPacketBufferSize());
    }
}
