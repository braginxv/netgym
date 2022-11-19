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

package org.techlook.net.client.http.content;

import org.techlook.net.client.http.client.HttpListener;

import java.util.List;
import java.util.concurrent.ForkJoinPool;


public class WholeContentReader extends ContentReader {
    private final int size;
    private volatile int readSize = 0;

    public WholeContentReader(HttpListener listener,
                              int size, byte[] initialChunk, List<Decoder> decoders, ForkJoinPool threadPool) {
        super(listener, decoders, threadPool);
        this.size = size;

        if (initialChunk != null && initialChunk.length > 0) {
            read(initialChunk);
        }
    }

    @Override
    public synchronized void read(byte[] chunk) {
        if (chunk == null) {
            return;
        }

        transmitChunk(chunk);
        readSize += chunk.length;

        if (readSize == size) {
            completeTransmitting();
        }
    }
}
