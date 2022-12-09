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

package org.techlook.net.client.http.adapters;

import org.techlook.net.client.Fault;
import org.techlook.net.client.http.client.HttpListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * One of most convenient way to obtain large HTTP response content. Just create this listener and get toInputStream()
 * input stream, then read it as an ordinary stream. Thus, depending on what you need
 *   ByteStreamListener byteStreamListener = new ByteStreamListener(...);
 *   new DataInputStream(byteStreamListener.toInputStream()). ... or
 *   new BufferedReader(new InputStreamReader(byteStreamListener.toInputStream())) ...
 *
 * The obtained input stream blocks user thread if it has no data and it is uncompleted
 * and until a new portion of data is received.
 */
public abstract class ByteStreamListener extends HttpListener {
    protected final PipedOutputStream outputStream = new PipedOutputStream();
    protected final PipedInputStream inputStream = new PipedInputStream(outputStream);

    public ByteStreamListener() throws IOException {
    }

    @Override
    public final void respond(byte[] chunk) {
        try {
            outputStream.write(chunk);
        } catch (IOException e) {
            failure(Fault.ByteBufferFillError.format(e.getMessage()));
        }
    }

    @Override
    public final void complete() {
        try {
            outputStream.close();
        } catch (IOException e) {
            failure(Fault.ByteBufferFillError.format(e.getMessage()));
        }
    }

    @Override
    public final void connectionClosed() {
        try {
            outputStream.close();
        } catch (IOException e) {
            failure(Fault.ByteBufferFillError.format(e.getMessage()));
        }
    }

    /**
     * @return  InputStream - stream to be read synchronously
     */
    public InputStream toInputStream() {
        return inputStream;
    }
}
