/*
 * The MIT License
 *
 * Copyright (c) 2022 Vladimir Bragin
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

package org.techlook.net.client;

import org.techlook.net.client.nio.TransportChannel;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public interface SocketClient {
    /**
     * Establishes socket-based (TCP, UDP) connection to the remote server.
     * This method equals invocation of the connect(SocketAddress server,
     *   ChannelListener listener, TransportChannel channel, DEFAULT_BUFFERS_MEMORY_USAGE)
     * @param server    the remote server address to be connecting to
     * @param listener  is used for asynchronous events listening
     * @throws IOException      when exception is thrown in an underlying nio layer
     * @return          identifier of the opening connection
     */
    int connect(SocketAddress server, ChannelListener listener) throws IOException;

    /**
     * Establishes socket-based (TCP, UDP) connection to the remote server.
     * This method equals invocation of the connect(SocketAddress server,
     *   ChannelListener listener, TransportChannel transportChannel, DEFAULT_BUFFERS_MEMORY_USAGE)
     * @param server            the remote server address to be connecting to
     * @param listener          is used for asynchronous events listening
     * @param transportChannel  TCP or UDP
     * @param readBufferSize    size of buffer for read operations, tune this parameter for increase read performance
     * @throws IOException      when exception is thrown in an underlying nio layer
     * @return          identifier of the opening connection
     */
    int connect(SocketAddress server, ChannelListener listener, TransportChannel transportChannel,
                int readBufferSize) throws IOException;

    /**
     * Asynchronous write data for sending
     * @param data       the data to be send
     * @param offset     start index to copy
     * @param length     length of chunk copied from data
     * @param channelId  the channel identifier which have got by connecting
     * @return   true if data was sent successfully
     */
    boolean send(byte[] data, int offset, int length, Integer channelId);

    /**
     * Stopping the client to dispatch network messages
     */
    void shutdown();

    /**
     * Close previously opened channel
     * @param channel the channel identifier received on connecting
     */
    void close(int channel);

    /**
     * Blocks thread until terminating will be completed
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    void awaitTerminating() throws ExecutionException, InterruptedException;

    /**
     * This ForkJoinPool used for all asynchronous operations within the whole client
     * @return the thread pool instance
     */
    ForkJoinPool getThreadPool();

    /**
     * Use this completion for getting client termination result
     * @return completion
     * @see ResultedCompletion
     */
    ResultedCompletion<Void> completion();
}
