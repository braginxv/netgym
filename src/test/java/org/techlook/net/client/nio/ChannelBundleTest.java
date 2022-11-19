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

package org.techlook.net.client.nio;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.techlook.net.client.ByteChunksEmitter;
import org.techlook.net.client.ReadableByteSource;
import org.techlook.net.client.SocketClient;
import org.techlook.net.client.WritableChannelSink;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ChannelBundleTest {
    private ChannelBundle channelBundle;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private SocketClient socketClient;

    private WritableChannelSink channelListener;

    private int channelId;

    @Before
    public void standUp() {
        channelListener = new WritableChannelSink();
        channelId = 10;
        channelBundle = new ChannelBundle(TransportChannel.TCP,
                channelListener, channelId, socketClient, AsyncSocketClient.DEFAULT_READ_BUFFER_SIZE);
    }

    @Test
    public void doesntWriteChannelWhenEmptyQueue() throws IOException {
        final WritableByteChannel channel = mock(WritableByteChannel.class);
        verify(channel, never()).write(any(ByteBuffer.class));
        channelBundle.write(channel);
    }

    @Test
    public void writeChunksIntoChannel() {
        ByteChunksEmitter source = new ByteChunksEmitter();
        checkWriteIntegrity(source, source.smallChunksEmitter());
        checkWriteIntegrity(source, source.largeChunksEmitter());
        checkWriteIntegrity(source, source.mediumSizeEmitterWithBlankChunks());
    }

    @Test
    public void readChunksFromChannel() {
        ReadableByteSource source = new ReadableByteSource();
        channelBundle.read(source);
        assertTrue(channelListener.internalBufferEqualsTo(source.content()));
        verify(socketClient).close(channelId);
    }

    private void checkWriteIntegrity(ByteChunksEmitter source, Iterable<ByteBuffer> emittedChunks) {
        for (ByteBuffer chunk: emittedChunks) {
            byte[] buffer = new byte[chunk.remaining()];
            chunk.get(buffer);
            channelBundle.appendToWrite(buffer, 0, buffer.length);
        }
        WritableChannelSink sink = new WritableChannelSink();
        channelBundle.write(sink);
        assertTrue(sink.internalBufferEqualsTo(source));
    }
}
