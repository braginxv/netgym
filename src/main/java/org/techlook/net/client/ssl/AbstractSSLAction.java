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
import org.techlook.net.client.Fault;
import org.techlook.net.client.SocketClient;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.nio.ByteBuffer;
import java.util.concurrent.ForkJoinPool;

/**
 * Base class for all SSL operations (handshaking, incoming data, outgoing data) providing asynchronous (non-blocking)
 * and parallel performing each of these phases
 */
abstract class AbstractSSLAction extends AsyncAction {
    protected final SocketClient transport;
    protected final SSLEngine engine;
    protected final ChannelListener listener;
    protected final AsyncAction hostAction;
    protected Integer channelId;

    /**
     * @param engine      The SSLEngine itself
     * @param listener    listener of channel's events
     * @param threadPool  shared
     * @param transport   TCP or UDP transport
     */
    AbstractSSLAction(SSLEngine engine, ChannelListener listener, ForkJoinPool threadPool, SocketClient transport,
                      AsyncAction hostAction) {
        super(threadPool);
        this.engine = engine;
        this.listener = listener;
        this.transport = transport;
        this.hostAction = hostAction;
    }

    /**
     * @return true if handshaking already has been done
     */
    protected boolean isHandshakingDone() {
        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        return engine.getSession().isValid() && (handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED ||
                handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
    }

    /**
     * close this SSL phase on error popping
     * @param e thrown exception
     */
    protected void closeOnError(Exception e) {
        engine.closeOutbound();
        e.printStackTrace();
        listener.channelError(Fault.SSLError.format(e.getMessage()));
        listener.close();
    }

    /**
     * increasing buffer size when BUFFER_OVERFLOW message did receive
     * @param buffer the buffer to be enlarged
     * @param size   if new size is known and larger than old buffer size it is used
     *                 otherwise buffer will be increased in two times
     * @return       enlarged buffer
     */
    protected ByteBuffer enlargeBuffer(ByteBuffer buffer, int size) {
        ByteBuffer newBuffer =
                ByteBuffer.allocate(buffer.capacity() < size ? size : buffer.capacity() * 2);
        buffer.flip();
        newBuffer.put(buffer);

        return newBuffer;
    }

    void setChannelId(Integer channelId) {
        this.channelId = channelId;
    }
}
