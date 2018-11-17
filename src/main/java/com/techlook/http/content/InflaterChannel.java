package com.techlook.http.content;

import com.techlook.AsyncSocketClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.ZipException;


public class InflaterChannel implements WritableByteChannel {
    private final WritableByteChannel output;
    private final BlockingQueue<ByteBuffer> chunks = new ArrayBlockingQueue<>(2 * AsyncSocketClient.PARALLELISM_LEVEL);
    private final InflaterAction inflation;

    private volatile boolean closed = false;


    public InflaterChannel(WritableByteChannel output, boolean useGzip) {
        this.output = output;
        this.inflation = new InflaterAction(useGzip);
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;

        inflation.shakeUp();
        inflation.join();

        output.close();
    }

    @Override
    public int write(ByteBuffer src) {
        int size = src.limit();

        chunks.add(src);
        inflation.shakeUp();

        return size;
    }

    private interface QueueSink {
        void process() throws Exception;

        boolean isCompleted();

        ByteBuffer getRemain();

        void setStartBuffer(ByteBuffer buffer);
    }

    private class InflaterAction extends RecursiveAction implements QueueSink {
        private final static int TRAILER_SIZE = 8;
        private final Inflater inflater;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private final CRC32 crc = new CRC32();

        private QueueSink chunksSink = new HeaderCompiler();
        private ByteBuffer startChunk;
        private ByteBuffer remaining;
        private boolean completed = false;

        InflaterAction(boolean useGzip) {
            inflater = new Inflater(useGzip);
        }

        @Override
        protected void compute() {
            try {
                chunksSink.process();
                if (chunksSink.isCompleted() && chunksSink != this) {
                    ByteBuffer remain = chunksSink.getRemain();

                    chunksSink = this;
                    chunksSink.setStartBuffer(remain);

                    compute();
                }
            } catch (Exception error) {
                completeExceptionally(error);
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
                chunkBuffer = chunks.poll();
            }

            while (chunkBuffer != null) {
                byte[] chunk = new byte[chunkBuffer.limit()];
                chunkBuffer.get(chunk);
                inflater.setInput(chunk);
                byte[] block = new byte[AsyncSocketClient.DEFAULT_READ_BUFFER_LENGTH];
                int inflatingSize;
                while (!inflater.needsInput() && (inflatingSize = inflater.inflate(block)) > 0) {
                    crc.update(block, 0, inflatingSize);
                    output.write(ByteBuffer.wrap(block, 0, inflatingSize));
                }

                if (inflater.finished() || inflater.needsDictionary()) {
                    if (remaining == null) {
                        int remainingBytes = inflater.getRemaining();
                        int startIndex = chunk.length - remainingBytes;

                        remaining = ByteBuffer.allocate(TRAILER_SIZE);
                        remaining.put(chunk, startIndex, Math.min(remainingBytes, TRAILER_SIZE));
                    }

                    while (remaining.hasRemaining() && (chunkBuffer = chunks.poll()) != null) {
                        byte[] array = new byte[chunkBuffer.limit()];
                        chunkBuffer.get(array);
                        remaining.put(array, 0, Math.min(remaining.remaining(), array.length));
                    }

                    if (!remaining.hasRemaining()) {
                        remaining.flip();
                        remaining.order(ByteOrder.LITTLE_ENDIAN);

                        if ((remaining.getInt() & 0xffffffffL) != crc.getValue()
                                || remaining.getInt() != (inflater.getBytesWritten() & 0xffffffffL)) {
                            throw new IllegalStateException("Corrupt GZIP stream");
                        }
                        if (!completed) {
                            closed = true;
                            output.close();
                            completed = true;
                        }
                    }

                    isRunning.set(false);
                    return;
                }

                chunkBuffer = chunks.poll();
            }

            isRunning.set(false);
        }

        void shakeUp() {
            if (isRunning.compareAndSet(false, true)) {
                fork();
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
    }

    private class HeaderCompiler implements QueueSink {
        private final static short GZIP_MAGIC = 0x1f8b;
        private final static char DEFLATE_METHOD_CODE = 8;
        private final static int BYTE_SIZE = 1;
        private final static int SHORT_SIZE = 2;
        private final static int FHCRC = 2;
        private final static int FEXTRA = 4;
        private final static int FNAME = 8;
        private final static int FCOMMENT = 16;
        private boolean compileHeaderCompleted = false;
        private ByteBuffer remain;
        private ByteBuffer headerBuffer;
        private int readOffset;

        @Override
        public void process() throws IOException {
            ByteBuffer buffer = chunks.poll();

            while (buffer != null) {
                ensureCapacity(buffer.limit());
                headerBuffer.put(buffer);

                compileHeaderCompleted = headerCompiled();
                buffer = compileHeaderCompleted ? null : chunks.poll();
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

        private void ensureCapacity(int size) {
            if (headerBuffer == null) {
                headerBuffer = ByteBuffer.allocate(size);
            } else if (headerBuffer.capacity() - headerBuffer.position() < size) {
                ByteBuffer newBuffer = ByteBuffer.allocate(headerBuffer.position() + size);
                newBuffer.put(headerBuffer);
                headerBuffer = newBuffer;
            }
        }

        private boolean headerCompiled() throws IOException {
            int writtenSize = headerBuffer.position();

            readOffset = 0;
            headerBuffer.flip();

            if (cantReadShort()) return false;
            check(readShort() == GZIP_MAGIC, "No GZIP magic header");

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

        private boolean cantReadByte() {
            return readOffset + BYTE_SIZE >= headerBuffer.limit();
        }

        private byte readByte() {
            byte result = headerBuffer.get(readOffset);
            readOffset += BYTE_SIZE;

            return result;
        }

        private boolean cantReadShort() {
            return readOffset + SHORT_SIZE >= headerBuffer.limit();
        }

        private short readShort() {
            short result = headerBuffer.getShort(readOffset);
            readOffset += SHORT_SIZE;

            return result;
        }

        private boolean cantSkipBytes(int bytesNumber) {
            return readOffset + bytesNumber >= headerBuffer.limit();
        }

        private void skipBytes(int bytesNumber) {
            readOffset += bytesNumber;
            headerBuffer.position(readOffset);
        }

        private void check(boolean condition, String errorMessage) throws IOException {
            if (!condition) {
                throw new ZipException(errorMessage);
            }
        }

        private void continueWriteBuffer() {
            headerBuffer.position(headerBuffer.limit());
            headerBuffer.limit(headerBuffer.capacity());
        }
    }
}
