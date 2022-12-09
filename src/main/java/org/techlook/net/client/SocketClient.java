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
     * Establishes socket-based (TCP, UDP) connection to a remote host
     * @param server    the remote host to connect to
     * @param listener  event listener
     * @throws IOException      something went wrong in an underlying nio layer
     * @return          connection ID
     */
    int connect(SocketAddress server, ChannelListener listener) throws IOException;

    /**
     * Establishes socket-based (TCP, UDP) connection to a remote host
     * @param server            the remote host to connect to
     * @param listener          event listener
     * @param transportChannel  TCP or UDP
     * @param readBufferSize    buffer size
     * @throws IOException      something went wrong in an underlying nio layer
     * @return          connection ID
     */
    int connect(SocketAddress server, ChannelListener listener, TransportChannel transportChannel,
                int readBufferSize) throws IOException;

    /**
     * Non-blocking writing a data to send
     * @param data       the data to be send
     * @param offset     start index to copy
     * @param length     length of chunk copied from data
     * @param channelId  the connection ID that is assigned when connecting
     * @return   true if the data has been sent successfully
     */
    boolean send(byte[] data, int offset, int length, Integer channelId);

    /**
     * Stopping the client to dispatch
     */
    void shutdown();

    /**
     * Close the previously opened channel
     * @param channel the connection ID that is assigned when connecting
     */
    void close(int channel);

    /**
     * Blocks thread until the terminating is completed
     * @throws ExecutionException if the data processing threw an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    void awaitTerminating() throws ExecutionException, InterruptedException;

    /**
     * Fork-Join pool shared to perform all asynchronous operations
     * @return thread pool instance
     */
    ForkJoinPool getThreadPool();

    /**
     * Wait for the client to terminate using this completion
     * @return completion
     * @see ResultedCompletion
     */
    ResultedCompletion<Void> completion();
}
