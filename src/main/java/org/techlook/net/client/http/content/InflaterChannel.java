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

import org.techlook.net.client.AsyncAction;
import org.techlook.net.client.nio.AsyncSocketClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * This is the WritableByteChannel instance which is used for unpacking chunks using deflate and gzip algorithms
 */
public class InflaterChannel implements WritableByteChannel {
    private final WritableByteChannel output;
    private final InflaterAction inflation;
    private final ForkJoinPool threadPool;

    /**
     * Creation
     * @param output next WritableByteChannel sink where unpacked chunks will be transmitted
     * @param useGzip true if gzip should be applied (deflate otherwise)
     * @param threadPool common thread pool used in all actions withing that library
     */
    public InflaterChannel(WritableByteChannel output, boolean useGzip, ForkJoinPool threadPool) {
        this.threadPool = threadPool;
        this.output = output;
        this.inflation = new InflaterAction(useGzip);
    }

    /**
     * @return true if channel hasn't been closed yet
     */
    @Override
    public boolean isOpen() {
        return !inflation.completed;
    }

    /**
     * Asynchronously writing the buffer to be unpacked
     * @param src the buffer to be unpacked
     * @return actual number of bytes were written
     */
    @Override
    public int write(ByteBuffer src) {
        int size = src.limit();

        inflation.putChunk(src);
        inflation.shakeUp();

        return size;
    }

    /**
     * close this channel
     */
    @Override
    public void close() throws IOException {
        inflation.waitFinishing();
    }

    private interface QueueSink {
        void process() throws Exception;

        boolean isCompleted();

        ByteBuffer getRemain();

        void setStartBuffer(ByteBuffer buffer);
    }

    private class InflaterAction extends AsyncAction implements QueueSink {
        private final static int TRAILER_SIZE = 8;
        private final Inflater inflater;

        private volatile QueueSink chunksSink;
        private volatile HeaderCompiler headerCompiler;
        private volatile ByteBuffer startChunk;
        private volatile ByteBuffer remaining;
        private volatile boolean completed = false;

        InflaterAction(boolean useGzip) {
            super(threadPool);
            inflater = new Inflater(useGzip);
        }

        @Override
        protected void processAction() {
            try {
                if (chunksSink == null) {
                    synchronized (this) {
                        if (chunksSink == null) {
                            ByteBuffer buffer = chunks.peek();
                            if (buffer != null && buffer.limit() > 1) {
                                short headerMagic = buffer.getShort();

                                if (equalsToGzipMagic(headerMagic)) {
                                    headerCompiler = new GzipCompiler();
                                    chunksSink = headerCompiler;
                                } else if (equalsToAnyZlibMagic(headerMagic)) {
                                    headerCompiler = new ZlibCompiler();
                                    chunksSink = headerCompiler;
                                } else {
                                    headerCompiler = new EmptyHeaderCompiler();
                                    chunksSink = this;
                                }
                            } else {
                                return;
                            }
                        }
                    }
                }
                chunksSink.process();
                if (chunksSink != this && chunksSink.isCompleted()) {
                    ByteBuffer remain = chunksSink.getRemain();

                    chunksSink = this;
                    chunksSink.setStartBuffer(remain);
                }
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }

        @Override
        public void process() throws Exception {
            if (completed) {
                return;
            }

            ByteBuffer chunkBuffer;
            if (startChunk != null) {
                chunkBuffer = startChunk;
                startChunk = null;
            } else {
                chunkBuffer = pollChunk();
            }

            while (chunkBuffer != null) {
                byte[] chunk = new byte[chunkBuffer.limit() - chunkBuffer.position()];
                chunkBuffer.get(chunk);
                inflater.setInput(chunk);
                byte[] block = new byte[
                        (int) (AsyncSocketClient.DEFAULT_READ_BUFFER_SIZE)];
                int inflatingSize;
                while (!inflater.needsInput() && (inflatingSize = inflater.inflate(block)) > 0) {
                    headerCompiler.updateChecksum(block, 0, inflatingSize);
                    output.write(ByteBuffer.wrap(block, 0, inflatingSize));
                }

                if (inflater.finished() || inflater.needsDictionary()) {
                    if (remaining == null) {
                        int remainingBytes = inflater.getRemaining();
                        int startIndex = chunk.length - remainingBytes;

                        remaining = ByteBuffer.allocate(TRAILER_SIZE);
                        remaining.put(chunk, startIndex, Math.min(remainingBytes, TRAILER_SIZE));
                    }

                    while (remaining.hasRemaining() && (chunkBuffer = pollChunk()) != null) {
                        byte[] array = new byte[chunkBuffer.limit()];
                        chunkBuffer.get(array);
                        remaining.put(array, 0, Math.min(remaining.remaining(), array.length));
                    }

                    if (!remaining.hasRemaining()) {
                        remaining.flip();
                        remaining.order(ByteOrder.LITTLE_ENDIAN);

                        if (!headerCompiler.isChecksumCorrect(remaining.getInt())
                                || remaining.getInt() != (inflater.getBytesWritten() & 0xffffffffL)) {
                            throw new IllegalStateException("Corrupt compression stream");
                        }
                        if (!completed) {
                            output.close();
                            completed = true;
                        }
                    }

                    return;
                }

                chunkBuffer = pollChunk();
            }
        }

        @Override
        public boolean isCompleted() {
            return completed;
        }

        @Override
        public ByteBuffer getRemain() {
            return remaining;
        }

        @Override
        public void setStartBuffer(ByteBuffer buffer) {
            startChunk = buffer;
        }

        ByteBuffer pollChunk() {
            return chunks.poll();
        }

        void putChunk(ByteBuffer chunk) {
            chunks.add(chunk);
            shakeUp();
        }
    }

    private final static short GZIP_MAGIC = 0x1f8b;

    private boolean equalsToGzipMagic(short number) {
        return GZIP_MAGIC == number;
    }

    private abstract class HeaderCompiler implements QueueSink {
        private final static int BYTE_SIZE = 1;
        private final static int SHORT_SIZE = 2;

        protected volatile boolean compileHeaderCompleted = false;
        protected volatile ByteBuffer remain;
        protected volatile ByteBuffer headerBuffer;
        protected volatile int readOffset;

        @Override
        public void process() throws IOException {
            ByteBuffer buffer = inflation.pollChunk();

            while (buffer != null) {
                ensureCapacity(buffer.limit());
                headerBuffer.put(buffer);

                compileHeaderCompleted = headerCompiled();
                buffer = compileHeaderCompleted ? null : inflation.pollChunk();
                continueWriteBuffer();
            }
        }

        @Override
        public boolean isCompleted() {
            return compileHeaderCompleted;
        }

        @Override
        public ByteBuffer getRemain() {
            return remain;
        }

        @Override
        public void setStartBuffer(ByteBuffer buffer) {
            // not callable
        }

        public abstract void updateChecksum(byte[] buffer, int from, int to);

        public abstract boolean isChecksumCorrect(int checksum);

        protected void ensureCapacity(int size) {
            if (headerBuffer == null) {
                headerBuffer = ByteBuffer.allocate(size);
            } else if (headerBuffer.remaining() < size) {
                headerBuffer.flip();
                ByteBuffer newBuffer = ByteBuffer.allocate(headerBuffer.remaining() + size);
                newBuffer.put(headerBuffer);
                headerBuffer = newBuffer;
            }
        }

        protected abstract boolean headerCompiled() throws IOException;

        protected boolean cantReadByte() {
            return readOffset + BYTE_SIZE >= headerBuffer.limit();
        }

        protected byte readByte() {
            byte result = headerBuffer.get(readOffset);
            readOffset += BYTE_SIZE;

            return result;
        }

        protected boolean cantReadShort() {
            return readOffset + SHORT_SIZE >= headerBuffer.limit();
        }

        protected short readShort() {
            short result = headerBuffer.getShort(readOffset);
            readOffset += SHORT_SIZE;

            return result;
        }

        protected boolean cantSkipBytes(int bytesNumber) {
            return readOffset + bytesNumber >= headerBuffer.limit();
        }

        protected void skipBytes(int bytesNumber) {
            readOffset += bytesNumber;
            headerBuffer.position(readOffset);
        }

        protected void check(boolean condition, String errorMessage) throws IOException {
            if (!condition) {
                throw new ZipException(errorMessage);
            }
        }

        protected void continueWriteBuffer() {
            headerBuffer.position(headerBuffer.limit());
            headerBuffer.limit(headerBuffer.capacity());
        }
    }

    private class GzipCompiler extends HeaderCompiler {
        private final static char DEFLATE_METHOD_CODE = 8;
        private final static int FHCRC = 2;
        private final static int FEXTRA = 4;
        private final static int FNAME = 8;
        private final static int FCOMMENT = 16;
        private final CRC32 crc = new CRC32();

        @Override
        public void updateChecksum(byte[] buffer, int from, int to) {
            crc.update(buffer, from, to);
        }

        @Override
        public boolean isChecksumCorrect(int checksum) {
            return checksum == (int) (crc.getValue() & 0xffffffffL);
        }

        protected boolean headerCompiled() throws IOException {
            int writtenSize = headerBuffer.position();

            readOffset = 0;
            headerBuffer.flip();

            if (cantReadByte()) return false;
            check(readByte() == DEFLATE_METHOD_CODE, "Only DEFLATE compressed method is supported");

            if (cantReadByte()) return false;
            byte flags = readByte();

            if (cantSkipBytes(6)) return false;
            skipBytes(6);

            if ((flags & FEXTRA) != 0) {
                if (cantReadShort()) return false;

                short skipBytesNumber = readShort();
                if (cantSkipBytes(skipBytesNumber)) return false;
                skipBytes(skipBytesNumber);
            }

            if ((flags & FNAME) != 0) {
                do {
                    if (cantReadByte()) return false;
                } while (readByte() != 0);
            }

            if ((flags & FCOMMENT) != 0) {
                do {
                    if (cantReadByte()) return false;
                } while (readByte() != 0);
            }

            if ((flags & FHCRC) != 0) {
                if (cantReadShort()) return false;
                short checkCRC = readShort();

                byte[] buffer = new byte[readOffset - 2];
                headerBuffer.rewind();
                headerBuffer.get(buffer);
                CRC32 crc = new CRC32();
                crc.update(buffer);

                if (checkCRC != (crc.getValue() & 0xffff)) {
                    throw new ZipException("Corrupt gzip header");
                }
            }

            headerBuffer.position(readOffset);
            headerBuffer.limit(writtenSize);

            headerBuffer.compact();
            headerBuffer.flip();

            int remainSize = writtenSize - readOffset;
            headerBuffer.limit(remainSize);

            remain = ByteBuffer.allocate(remainSize);
            remain.put(headerBuffer);
            remain.flip();

            return true;
        }
    }

    private final static short[] ZLIB_MAGICS;

    static {
        final short[] zlibMagicNumbers = new short[]{
                0x7801,
                0x785E,
                0x789C,
                0x78DA,
                0x7820,
                0x787D,
                0x78BB,
                0x78F9
        };

        Arrays.sort(zlibMagicNumbers);
        ZLIB_MAGICS = zlibMagicNumbers;
    }

    private boolean equalsToAnyZlibMagic(short number) {
        return Arrays.binarySearch(ZLIB_MAGICS, number) >= 0;
    }

    private class ZlibCompiler extends HeaderCompiler {
        private final Adler32 adler = new Adler32();

        @Override
        public void updateChecksum(byte[] buffer, int from, int to) {
            adler.update(buffer, from, to);
        }


        @Override
        public boolean isChecksumCorrect(int checksum) {
            return checksum == adler.getValue();
        }

        @Override
        protected boolean headerCompiled() throws IOException {
            readOffset = 0;
            headerBuffer.flip();

            return true;
        }
    }

    private class EmptyHeaderCompiler extends HeaderCompiler {
        @Override
        public void updateChecksum(byte[] buffer, int from, int to) {
        }

        @Override
        public boolean isChecksumCorrect(int checksum) {
            return true;
        }

        @Override
        protected boolean headerCompiled() throws IOException {
            return true;
        }
    }
}
