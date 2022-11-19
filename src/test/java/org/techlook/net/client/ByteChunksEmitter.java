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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

public class ByteChunksEmitter {
    private static final long CONTENT_ENDING = 0x1122334455667788L;
    private static final byte[] CONTENT_ENDING_ARRAY;
    private static final int LONG_SIZE = Long.SIZE / 8;

    static {

        ByteBuffer ending = ByteBuffer.allocate(LONG_SIZE);
        ending.putLong(CONTENT_ENDING);
        CONTENT_ENDING_ARRAY = ending.array();
    }

    public static final int CONTENT_LENGTH = 0x100000;
    public final static int SMALL_STEP = Math.max(CONTENT_LENGTH, 100) / 100;
    public final static int BIG_STEP = Math.max(CONTENT_LENGTH, 5) / 5;
    public final static int MEDIUM_STEP = Math.max(CONTENT_LENGTH, 20) / 20;

    protected final ByteBuffer content;

    {
        byte[] buffer = new byte[CONTENT_LENGTH];
        new Random().nextBytes(buffer);
        System.arraycopy(CONTENT_ENDING_ARRAY, 0, buffer, buffer.length - LONG_SIZE, LONG_SIZE);
        content = ByteBuffer.wrap(buffer);
    }

    public byte[] getContentAsBytes() {
        return content.array();
    }

    public Iterable<ByteBuffer> smallChunksEmitter() {
        return new Iterable<ByteBuffer>() {
            @Override
            public Iterator<ByteBuffer> iterator() {
                return new ChunksGenerator(SMALL_STEP);
            }
        };
    }

    public Iterable<ByteBuffer> largeChunksEmitter() {
        return new Iterable<ByteBuffer>() {
            @Override
            public Iterator<ByteBuffer> iterator() {
                return new ChunksGenerator(BIG_STEP);
            }
        };
    }

    public Iterable<ByteBuffer> mediumSizeEmitterWithBlankChunks() {
        return new Iterable<ByteBuffer>() {
            @Override
            public Iterator<ByteBuffer> iterator() {
                return new ChunksGenerator(MEDIUM_STEP) {
                    final int COUNTER_RESET = 5;
                    int counter = COUNTER_RESET;

                    @Override
                    public ByteBuffer next() {
                        if (--counter == 0) {
                            counter = COUNTER_RESET;
                            return ByteBuffer.allocate(0);
                        }

                        return super.next();
                    }
                };
            }
        };
    }

    private class ChunksGenerator implements Iterator<ByteBuffer> {
        final int step;
        int index = 0;

        private ChunksGenerator(int step) {
            this.step = step;
        }

        @Override
        public boolean hasNext() {
            return index < CONTENT_LENGTH;
        }

        @Override
        public ByteBuffer next() {
            int chunkSize = Math.min(step, CONTENT_LENGTH - index);
            content.position(index);

            int nextIndex = index + chunkSize;
            content.limit(nextIndex);
            index = nextIndex;

            return content.slice();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove chunks");
        }
    }

    public static boolean isEndingChunk(byte[] chunk) {
        if (chunk.length < LONG_SIZE) {
            return false;
        }

        return Arrays.equals(CONTENT_ENDING_ARRAY,
                Arrays.copyOfRange(chunk, chunk.length - LONG_SIZE, chunk.length));
    }
}
