package com.techlook.http.content;

import com.techlook.http.HttpListener;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;


public class ChunkedContentReader extends ContentReader {
    private ByteBuffer buffer;
    private int chunkSize;
    private boolean isSizeRead = true;

    public ChunkedContentReader(HttpListener listener, Charset charset, byte[] initialBuffer) {
        super(listener, charset);
        final int MAX_SIZE_WITH_CRLF_LENGTH = 10;

        buffer = ByteBuffer.allocate(initialBuffer.length + MAX_SIZE_WITH_CRLF_LENGTH);
        read(initialBuffer);
    }

    @Override
    public void read(byte[] chunk) {
        insureBufferCapacity(chunk.length);

        buffer.put(chunk);

        if (isSizeRead) {
            tryRead();
        } else {
            buffer.flip();
            isSizeRead = readChunk();

            if (isSizeRead) {
                tryRead();
            }
        }
    }

    private void tryRead() {
        int position;
        while ((position = crlfPosition()) != -1) {
            buffer.flip();
            byte[] array = new byte[position];
            buffer.get(array);
            chunkSize = Integer.valueOf(new String(array), 16);

            if (chunkSize == 0) {
                completeTransmitting();
                return;
            }
            buffer.get();
            buffer.get();

            if (!readChunk()) {
                isSizeRead = false;
                break;
            }
        }
    }

    private boolean readChunk() {
        boolean result = false;
        if (buffer.remaining() >= chunkSize) {
            byte[] array = new byte[chunkSize];
            buffer.get(array);
            buffer.get();
            buffer.get();

            transmitChunk(array);
            result = true;
        }

        buffer.compact();

        return result;
    }

    private void insureBufferCapacity(int length) {
        if (buffer.capacity() - buffer.position() < length) {
            buffer.flip();
            byte[] array = new byte[buffer.limit()];
            buffer.get(array);

            buffer = ByteBuffer.allocate(array.length + length);
            buffer.put(array);
        }
    }

    private int crlfPosition() {
        int lastIndex = buffer.limit() - 1;
        for (int index = 0; index < lastIndex; index++) {
            if (buffer.get(index) == '\r' && buffer.get(index + 1) == '\n') {
                return index;
            }
        }

        return -1;
    }
}
