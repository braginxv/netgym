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

package org.techlook.net.client.http.client;

import org.techlook.net.client.Fault;
import org.techlook.net.client.http.content.ChunkedContentReader;
import org.techlook.net.client.http.content.ContentReader;
import org.techlook.net.client.http.content.Decoder;
import org.techlook.net.client.http.content.WholeContentReader;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and recognizes the HTTP parts from the input stream.
 */
public class HttpSession {
    private final static String CONTENT_LENGTH = "content-length";
    private final static String TRANSFER_ENCODING = "transfer-encoding";
    private final static String TRANSFER_ENCODING_CHUNKED = "chunked";
    private final static String CONTENT_ENCODING = "content-encoding";
    private final static String CONNECTION = "connection";
    private final static Pattern RESPONSE_HEAD = Pattern.compile("^(\\S+)\\s+(\\d+)\\s+(.+)$");

    private final StringBuilder responseBuilder = new StringBuilder();
    private final Map<String, String> headers = new TreeMap<>();
    private final List<Decoder> decompressionMethods = new ArrayList<>();
    private final HttpListener listener;
    private final ForkJoinPool threadPool;
    private final AtomicBoolean responseCodeIsntParsedYet = new AtomicBoolean(true);

    private volatile int position = 0;
    private volatile boolean continueParse = true;
    private volatile long contentLength;
    private volatile ContentReader contentReader;


    /**
     * Creation
     * @param listener    HTTP listener
     * @param threadPool  shared fork-join thread pool
     */
    public HttpSession(HttpListener listener, ForkJoinPool threadPool) {
        this.listener = listener;
        this.threadPool = threadPool;
    }

    /**
     * parsed content length
     * @return content length
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * event listener
     * @return http listener
     */
    public HttpListener getListener() {
        return listener;
    }

    /**
     * read incoming chunk from channel
     * @param chunk chunk
     */
    public void read(byte[] chunk) {
        responseBuilder.append(new String(chunk));
        if (continueParse) {
            int index;

            while ((index = responseBuilder.indexOf("\n", position)) > 0) {
                int residueIndex = index + 1;
                if (responseBuilder.charAt(index - 1) == '\r') {
                    --index;
                }

                String line = responseBuilder.substring(position, index).trim();
                if (responseCodeIsntParsedYet.compareAndSet(true, false)) {
                    parseResponseCode(line);
                }

                if (line.isEmpty()) {
                    int start = -1;
                    for (int k = 3; k < chunk.length; ++k) {
                        if (chunk[k - 3] == '\r' && chunk[k - 2] == '\n' && chunk[k - 1] == '\r' && chunk[k] == '\n') {
                            start = k + 1;
                            break;
                        }
                    }
                    completeResponseHeader(Arrays.copyOfRange(chunk, start, chunk.length));

                    break;
                } else {
                    pushLine(line);
                }

                position = residueIndex;
            }
        } else {
            contentReader.read(chunk);
        }
    }

    private void parseResponseCode(String line) {
        Matcher matcher = RESPONSE_HEAD.matcher(line);
        if (!matcher.matches()) {
            listener.failure(Fault.BAD_RESPONSE_HEAD.format(line));
            return;
        }

        listener.responseCode(Integer.parseInt(matcher.group(2)), matcher.group(1), matcher.group(3));
    }

    private void completeResponseHeader(byte[] residue) {
        continueParse = false;

        listener.respondHeaders(headers);

        if (headers.containsKey(CONTENT_ENCODING)) {
            String contentEncoding = headers.get(CONTENT_ENCODING);
            String[] compressMethods = contentEncoding.split(",");

            for (String method : compressMethods) {
                if (Decoder.GZIP.matchToken(method)) {
                    decompressionMethods.add(Decoder.GZIP);
                } else if (Decoder.Deflate.matchToken(method)) {
                    decompressionMethods.add(Decoder.Deflate);
                } else {
                    listener.failure(Fault.UnsupportedCompressMethods.format(contentEncoding));
                    return;
                }
            }
        }

        if (headers.containsKey(CONTENT_LENGTH)) {
            String lengthValue = headers.get(CONTENT_LENGTH);
            try {
                contentLength = Long.parseLong(lengthValue);
            } catch (NumberFormatException e) {
                listener.failure(Fault.ContentLengthNotRecognized.format(lengthValue));
                return;
            }

            if (contentLength == 0 && headers.containsKey(CONNECTION)) {
                String connection = headers.get(CONNECTION);
                if ("close".equalsIgnoreCase(connection)) {
                    listener.connectionClosed();
                } else {
                    listener.complete();
                }
            }

            if (contentLength > Integer.MAX_VALUE) {
                listener.failure(Fault.ContentSizeIsTooLarge.getDescription());
                return;
            }
            contentReader = new WholeContentReader(listener,
                    (int) contentLength, residue, decompressionMethods, threadPool);
        } else if (headers.containsKey(TRANSFER_ENCODING)) {
            String transfer = headers.get(TRANSFER_ENCODING).trim().toLowerCase();

            if (!transfer.equals(TRANSFER_ENCODING_CHUNKED)) {
                listener.failure(Fault.TransferEncodingUnsupported.format(transfer));
                return;
            }

            contentReader = new ChunkedContentReader(listener, residue, decompressionMethods, threadPool);
        } else {
            listener.failure(Fault.NonChunkedContentWithoutLength.getDescription());
        }
    }

    private void pushLine(String line) {
        int index = line.indexOf(':');
        if (index < 0) {
            return;
        }

        String key = line.substring(0, index).trim().toLowerCase();
        String value = line.substring(index + 1).trim();

        headers.put(key, value);
    }
}
