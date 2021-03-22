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

public enum Fault {
    AsyncClientError("Async client common error: %s"),
    AsyncClientChannelConnectError("Async client: channel not connected"),
    AsyncClientChannelConfigureError("Client channel setup error"),
    AsyncClientChannelWriteError("Client channel write error"),
    AsyncClientChannelReadError("Client channel read error"),
    AsyncClientChannelClosingError("Client channel close error"),
    BadEncoding("Bad encoding: %s"),
    NonChunkedContentWithoutLength("Content length or chuncked response not specified"),
    ContentLengthNotRecognized("Content length not recognized: %s"),
    ContentSizeIsTooLarge("Content size is too large and content cannot be received whole at once"),
    TransferEncodingUnsupported("Only 'chunked' transfer encoding with whole single content compressing is supported, but server responded: %s"),
    UnsupportedCompressMethods("Only GZIP, Deflate methods or their combinations are supported, but server responded: %s"),
    ByteBufferFillError("Error during fill byte buffer: %s"),
    RequiredSessionHasBeenClosed("Response cannot be processing because corresponding session has been closed"),
    DecompressionError("Error during decompression of response content: %s"),
    SSLError("SSL error: %s"),
    BAD_RESPONSE_HEAD("Bad response head: %s"),
    ForkJoinError("Error during fork-join pool multithreading: %s");


    private final String description;

    Fault(String description) {
        this.description = description;
    }

    public String format(Object... args) {
        return String.format(description, args);
    }


    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
