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

package org.techlook.http.content;

import org.techlook.http.client.HttpListener;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ForkJoinPool;


public class ChunkedContentReader extends ContentReader {
    private static final int NUMBER_BUFFER_LENGTH = 0x100;

    private volatile int chunkSize = -1;
    private volatile int transmittedSize = 0;
    private volatile ByteBuffer numberBuffer = ByteBuffer.allocate(NUMBER_BUFFER_LENGTH);

    public ChunkedContentReader(HttpListener listener, byte[] initialChunk, List<Decoder> decoders, ForkJoinPool threadPool) {
        super(listener, decoders, threadPool);

        if (initialChunk != null && initialChunk.length > 0) {
            tryTransmit(initialChunk);
        }
    }

    @Override
    public synchronized void read(byte[] chunk) {
        tryTransmit(chunk);
    }

    private void tryTransmit(byte[] buffer) {
        if (chunkSize < 0) {
            putIntoNumberBuffer(buffer, 0, buffer.length);
            detectChunkSize();
            return;
        }

        processChunk(buffer);
    }

    private void processChunk(byte[] buffer) {
        numberBuffer.flip();
        int remainingInNumberBuffer = numberBuffer.remaining();
        int newLength = buffer.length + remainingInNumberBuffer;
        if (transmittedSize + newLength <= chunkSize) {
            transmittedSize += newLength;
            if (remainingInNumberBuffer > 0) {
                byte[] fullChunk = new byte[newLength];
                numberBuffer.get(fullChunk, 0, remainingInNumberBuffer);
                System.arraycopy(buffer, 0, fullChunk, remainingInNumberBuffer, buffer.length);
                buffer = fullChunk;
            }
            transmitChunk(buffer);

            if (transmittedSize == chunkSize) {
                chunkSize = -1;
                transmittedSize = 0;
            }

            numberBuffer.compact();
            return;
        }
        numberBuffer.compact();

        int transmittingLength = chunkSize - transmittedSize;
        byte[] transmittedChunk = new byte[transmittingLength];
        System.arraycopy(buffer, 0, transmittedChunk, 0, transmittingLength);
        transmitChunk(transmittedChunk);
        putIntoNumberBuffer(buffer, transmittingLength, buffer.length - transmittingLength);
        chunkSize = -1;
        transmittedSize = 0;
        detectChunkSize();
    }

    private int crlfPosition() {
        int lastIndex = numberBuffer.limit() - 1;
        int bias = numberBuffer.position();
        for (int index = bias; index < lastIndex; index++) {
            if (numberBuffer.get(index) == '\r' && numberBuffer.get(index + 1) == '\n') {
                return index - bias;
            }
        }

        return -1;
    }

    private void skipCrlf() {
        int index = numberBuffer.position() + 1;
        while (index < numberBuffer.limit()
                && (numberBuffer.get(index - 1) == '\r' && numberBuffer.get(index) == '\n')) {
            ++index;
            numberBuffer.position(index);
        }
    }

    private void detectChunkSize() {
        numberBuffer.flip();
        skipCrlf();
        int length = crlfPosition();

        if (length == 0) {
            numberBuffer.compact();
            throw new IllegalStateException("the chunk size hasn't been detected");
        }

        if (length > 0) {
            byte[] array = new byte[length];
            numberBuffer.get(array);
            skipCrlf();
            chunkSize = Integer.valueOf(new String(array), 16);
            if (chunkSize == 0) {
                completeTransmitting();
                return;
            }
            if (numberBuffer.remaining() > 0) {
                byte[] residueExcludeNumberItself = new byte[numberBuffer.remaining()];
                numberBuffer.get(residueExcludeNumberItself);
                numberBuffer.compact();
                tryTransmit(residueExcludeNumberItself);
                return;
            }
        }
        numberBuffer.compact();
    }

    private void putIntoNumberBuffer(byte[] buffer, int offset, int length) {
        if (numberBuffer.remaining() < length) {
            ByteBuffer newBuffer = ByteBuffer.allocate(numberBuffer.position() + length);
            numberBuffer.flip();
            newBuffer.put(numberBuffer);
            numberBuffer = newBuffer;
        }
        numberBuffer.put(buffer, offset, length);
    }
}
