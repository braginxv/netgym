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
import org.techlook.SocketClient;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.util.concurrent.ForkJoinPool;

class OutgoingHandshakingAction extends OutgoingAction {
    OutgoingHandshakingAction(SSLEngine engine,
                              ChannelListener listener, ForkJoinPool threadPool, SocketClient transport) {
        super(engine, listener, threadPool, transport);
    }

    @Override
    void processOutgoing() {
        try {
            handleWrap(engine.wrap(outgoingAppData, outgoingNetData));
        } catch (SSLException e) {
            closeOnError(e);
        }
    }

    @Override
    protected void handleWrap(SSLEngineResult result) {
        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                throw new IllegalStateException("Unreachable point within the outgoing handshake data");
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
                outgoingNetData.position(0);
                outgoingNetData.limit(outgoingNetData.capacity());
                transport.send(chunk, 0, chunk.length, channelId);
                outgoingAppData.limit(0);
                outgoingAppData.compact();
                break;
            case CLOSED:
                break;
        }
    }
}
