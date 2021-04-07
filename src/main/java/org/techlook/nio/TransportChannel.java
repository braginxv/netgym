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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;

/**
 * Both TCP and UDP are supported as transport
 */
public enum TransportChannel {
    TCP {
        @Override
        public void createAndConnect(Selector selector, Object attachment, SocketAddress remote) throws IOException {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_CONNECT, attachment);
            channel.connect(remote);
        }

        @Override
        public void finishKeyProcessing(SelectionKey key,
                                 ChannelBundle bundle) throws IOException {
            SocketChannel channel = (SocketChannel) key.channel();
            if (key.isConnectable()) {
                if (!channel.finishConnect()) {
                    throw new IOException("Inconsistent state of channel");
                }

                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                return;
            }

            super.finishKeyProcessing(key, bundle);
        }
    },
    UDP {
        @Override
        public void createAndConnect(Selector selector, Object attachment, SocketAddress remote) throws IOException {
            DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ, attachment);
            channel.connect(remote);
        }
    };

    /**
     * Creation of the socket-based connection
     * @param selector   nio Selector
     * @param attachment selector attachment that will be used when events registered on this channel
     * @param remote     the remote server address to be connected to
     * @throws IOException      when exception is thrown in an underlying nio layer
     */
    public abstract void createAndConnect(Selector selector, Object attachment, SocketAddress remote)
            throws IOException;

    /**
     * finishing (outside basic logic in the SocketChannel) the selection key processing
     * @param key      SelectionKey to be processed
     * @param bundle   associated with it data
     * @throws IOException      when exception is thrown in an underlying nio layer
     */
    public void finishKeyProcessing(SelectionKey key,
                             ChannelBundle bundle) throws IOException {
        ByteChannel channel = (ByteChannel) key.channel();

        if (!key.isValid()) {
            bundle.close();
            return;
        }

        if (key.isReadable()) {
            bundle.read(channel);
        } else if (key.isWritable()) {
            bundle.write(channel);
        }

        int interestOps = SelectionKey.OP_READ;
        if (bundle.shouldWrite()) {
            interestOps |= SelectionKey.OP_WRITE;
        }

        if (!key.isValid()) {
            bundle.close();
        } else if (interestOps != key.interestOps()) {
            key.interestOps(interestOps);
        }
    }
}

