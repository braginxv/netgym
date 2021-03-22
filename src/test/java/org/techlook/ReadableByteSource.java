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

package org.techlook;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class ReadableByteSource extends ByteChunksEmitter implements ReadableByteChannel {
    private boolean isOpened = true;
    private boolean readBufferFully = true;

    @Override
    public int read(ByteBuffer dst) {
        if (!content.hasRemaining()) {
            return -1;
        }

        if (!dst.hasRemaining()) {
            return 0;
        }

        int readSize = Math.min(
                readBufferFully ? dst.remaining() : Math.max(dst.remaining() / 2, 1), content.remaining());
        readBufferFully = !readBufferFully;

        byte[] buffer = new byte[readSize];
        content.get(buffer);
        dst.put(buffer);

        return readSize;
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public void close() {
        isOpened = false;
    }

    public byte[] content() {
        return content.array();
    }
}
