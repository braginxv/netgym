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

public enum Fault {
    AsyncClientError("Async client general error: %s"),
    AsyncClientChannelConnectError("Async client: the channel is not connected"),
    AsyncClientChannelConfigureError("Client channel setup error"),
    AsyncClientChannelWriteError("Client channel write error"),
    AsyncClientChannelReadError("Client channel read error"),
    AsyncClientChannelClosingError("Client channel close error"),
    BadEncoding("Bad encoding: %s"),
    NonChunkedContentWithoutLength("A content length or a chuncked response is not specified"),
    ContentLengthNotRecognized("A content length is not recognized: %s"),
    ContentSizeIsTooLarge("A content size is too large and a content cannot be received entirely at once"),
    TransferEncodingUnsupported("Only 'chunked' transfer encoding is supported, but server responded: %s"),
    UnsupportedCompressMethods("Only GZIP, Deflate methods or their combinations are supported, but server responded: %s"),
    ByteBufferFillError("An error occurred while filling the byte buffer: %s"),
    RequiredSessionHasBeenClosed("A response cannot be processed because the corresponding session has been closed"),
    DecompressionError("An error occurred while decompression of the response content: %s"),
    SSLError("SSL error: %s"),
    BAD_RESPONSE_HEAD("Bad response head: %s"),
    ForkJoinError("An error occurred while the fork-join pool submits task: %s");


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
