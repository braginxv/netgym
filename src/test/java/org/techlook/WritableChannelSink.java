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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;

public class WritableChannelSink implements WritableByteChannel, ChannelListener {
    private boolean hasClosed = false;
    private final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    private String errorMessage;

    @Override
    public int write(ByteBuffer byteBuffer) throws IOException {
        byte[] chunk = new byte[byteBuffer.limit()];
        byteBuffer.get(chunk);
        byteStream.write(chunk);

        return chunk.length;
    }

    @Override
    public boolean isOpen() {
        return !hasClosed;
    }

    @Override
    public void channelError(String message) {
        this.errorMessage = message;
    }

    @Override
    public void chunkIsReceived(byte[] chunk) {
        try {
            byteStream.write(chunk);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasError() {
        return errorMessage != null;
    }

    @Override
    public void close() {
        hasClosed = true;
    }

    public boolean internalBufferEqualsTo(byte[] buffer) {
        byte[] a = byteStream.toByteArray();
        return Arrays.equals(a, buffer);
    }

    public boolean internalBufferEqualsTo(ByteChunksEmitter contentStub) {
        return internalBufferEqualsTo(contentStub.getContentAsBytes());
    }
}
