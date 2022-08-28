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

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.techlook.ByteChunksEmitter;
import org.techlook.SocketClient;
import org.techlook.WritableChannelSink;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class SSLChannelTest {
    @Parameters
    public static Iterable<Integer[]> data() {
        return Arrays.asList(new Integer[][] {{0x1000, 0x1000}, {0x1500, 0x1000}, {0x1000, 0x1500}});
    }

    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private SSLEngine sslEngine;

    @Mock
    private SSLSession sslSession;

    @Mock
    private SocketClient socketClient;

    @Parameter
    public int SSL_APP_BUFFER_SIZE;

    @Parameter(1)
    public int SSL_PACKET_BUFFER_SIZE;

    private static ForkJoinPool threadPool;

    private SSLChannel sslChannel;

    private WritableChannelSink channelSink;

    @BeforeClass
    public static void beforeAllTests() {
        threadPool = new ForkJoinPool();
    }

    @AfterClass
    public static void afterAllTests() {
        threadPool.shutdown();
        boolean shutdownGracefully = false;
        try {
            shutdownGracefully = threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

        if (!shutdownGracefully) {
            throw new IllegalStateException("Thread pool didn't shutdown gracefully");
        }
    }

    @Before
    public void standUp() throws SSLException {
        when(sslEngine.getSession()).thenReturn(sslSession);
        when(sslSession.getApplicationBufferSize()).thenReturn(SSL_APP_BUFFER_SIZE);
        when(sslSession.getPacketBufferSize()).thenReturn(SSL_PACKET_BUFFER_SIZE);
        when(sslSession.isValid()).thenReturn(true);
        when(sslEngine.getHandshakeStatus()).thenReturn(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
        channelSink = new WritableChannelSink();
        sslChannel = new SSLChannel(sslEngine, channelSink, threadPool, socketClient);
    }

    @Test
    public void smallChunksCollecting() {
        unwrapping(0);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(0, chunksEmitter, chunksEmitter.smallChunksEmitter());
    }

    @Test
    public void mediumChunksCollecting() {
        unwrapping(0);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(0, chunksEmitter, chunksEmitter.mediumSizeEmitterWithBlankChunks());
    }

    @Test
    public void largeChunksCollecting() {
        unwrapping(0);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(0, chunksEmitter, chunksEmitter.largeChunksEmitter());
    }

    @Test
    public void chunksCollectingWhenUnwrappingSlowerThanSmallChunksFeed() {
        unwrapping(10);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(1, chunksEmitter, chunksEmitter.smallChunksEmitter());
    }

    @Test
    public void chunksCollectingWhenUnwrappingSlowerThanMediumChunksFeed() {
        unwrapping(10);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(1, chunksEmitter, chunksEmitter.mediumSizeEmitterWithBlankChunks());
    }

    @Test
    public void chunksCollectingWhenUnwrappingSlowerThanLargeChunksFeed() {
        unwrapping(10);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(1, chunksEmitter, chunksEmitter.largeChunksEmitter());
    }

    @Test
    public void chunksCollectingWhenUnwrappingFasterThanChunksFeed() {
        unwrapping(1);
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(10, chunksEmitter, chunksEmitter.smallChunksEmitter());
    }

    @Test
    public void chunksCollectingWithBufferUnderflow() {
        unwrappingWithBufferUnderflow((int) (SSL_PACKET_BUFFER_SIZE * 0.75));
        ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        checkCollectingIntegrity(1, chunksEmitter, chunksEmitter.smallChunksEmitter());
    }

    @Test
    public void noIntermediateBuffersWhichHasNotBeenTransmitted() {
        unwrappingWithBufferUnderflow((int) (SSL_PACKET_BUFFER_SIZE * 0.75));

        final int CHECK_COUNTER_RESET = ByteChunksEmitter.CONTENT_LENGTH / ByteChunksEmitter.SMALL_STEP / 5;
        int counter = CHECK_COUNTER_RESET;

        final ByteChunksEmitter chunksEmitter = new ByteChunksEmitter();
        for (ByteBuffer buffer : chunksEmitter.smallChunksEmitter()) {
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            sslChannel.chunkIsReceived(chunk);

            if (--counter == 0) {
                counter = CHECK_COUNTER_RESET;
                do {
                    sleep(10);
                } while (!sslChannel.isTaskCompleted());

                assertTrue("No unprocessed chunks", sslChannel.hasNoUnprocessedData());
            }
        }

        sslChannel.waitFinishing();
        assertTrue(channelSink.internalBufferEqualsTo(chunksEmitter));
    }

    private void unwrappingWithBufferUnderflow(final int minRequiredBufferLength) {
        try {
            when(sslEngine.unwrap(any(ByteBuffer.class), any(ByteBuffer.class))).then(new Answer<SSLEngineResult>() {
                @Override
                public SSLEngineResult answer(InvocationOnMock invocationOnMock) {
                    ByteBuffer srcBuffer = invocationOnMock.getArgument(0);
                    ByteBuffer dstBuffer = invocationOnMock.getArgument(1);

                    byte[] chunk = new byte[srcBuffer.remaining()];
                    srcBuffer.mark();
                    srcBuffer.get(chunk);
                    srcBuffer.reset();
                    if (!ByteChunksEmitter.isEndingChunk(chunk) && srcBuffer.remaining() < minRequiredBufferLength) {
                        return new SSLEngineResult(SSLEngineResult.Status.BUFFER_UNDERFLOW,
                                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
                    }

                    return basicTransmit(srcBuffer, dstBuffer);
                }
            });
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    private void unwrapping(final long unwrapDelay) {
        try {
            when(sslEngine.unwrap(any(ByteBuffer.class), any(ByteBuffer.class))).then(new Answer<SSLEngineResult>() {
                @Override
                public SSLEngineResult answer(InvocationOnMock invocationOnMock) {
                    ByteBuffer srcBuffer = invocationOnMock.getArgument(0);
                    ByteBuffer dstBuffer = invocationOnMock.getArgument(1);

                    SSLEngineResult result = basicTransmit(srcBuffer, dstBuffer);
                    if (unwrapDelay > 0) {
                        sleep(unwrapDelay);
                    }

                    return result;
                }
            });
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkCollectingIntegrity(long feedDelay,
                                          ByteChunksEmitter emitter, Iterable<ByteBuffer> chunksSource) {
        for (ByteBuffer buffer : chunksSource) {
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            sslChannel.chunkIsReceived(chunk);

            if (feedDelay > 0) {
                sleep(feedDelay);
            }
        }

        sslChannel.waitFinishing();
        assertTrue(channelSink.internalBufferEqualsTo(emitter));
    }

    private void sleep(long milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException ignored) {
        }
    }

    private SSLEngineResult basicTransmit(ByteBuffer srcBuffer, ByteBuffer dstBuffer) {
        int sizeTransmitted;
        if (dstBuffer.remaining() < srcBuffer.remaining()) {
            sizeTransmitted = dstBuffer.remaining();

            byte[] chunk = new byte[sizeTransmitted];
            srcBuffer.get(chunk);
            dstBuffer.put(chunk);

            return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sizeTransmitted, sizeTransmitted);
        }

        sizeTransmitted = srcBuffer.remaining();
        dstBuffer.put(srcBuffer);

        return new SSLEngineResult(SSLEngineResult.Status.OK,
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sizeTransmitted, sizeTransmitted);
    }
}
