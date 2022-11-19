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

package org.techlook.net.client.http;

import org.techlook.net.client.SocketClient;
import org.techlook.net.client.http.client.HttpAsyncClient;
import org.techlook.net.client.http.client.HttpListener;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * This is the most advanced and performance kind of HTTP/1.1 client using HTTP pipelining.
 * Most servers blocks multiple parallels requests to prevent dos-attacks,
 * therefore it is recommended to send requests with slight delays.
 */
public class PipeliningConnection implements HttpConnection {
    public static final long DEFAULT_SENDING_INTERVAL = 50;

    private final HttpAsyncClient httpClient;
    private final long sendingTimeInterval;

    /**
     * Creation of this client and connection parameters saving
     * @param server       the remote server we need connect to
     * @param port         TCP port
     * @param asyncClient  asynchronous SocketClient instance to be used as transport for transmitting request
     */
    public PipeliningConnection(String server, int port, SocketClient asyncClient) {
        this(server, port, asyncClient, DEFAULT_SENDING_INTERVAL);
    }

    /**
     * Creation of this client and connection parameters saving
     * @param server       the remote server we need connect to
     * @param port         TCP port
     * @param asyncClient  asynchronous SocketClient instance to be used as transport for transmitting request
     * @param sendingTimeInterval  the time interval of delaying send requests
     */
    public PipeliningConnection(String server, int port, SocketClient asyncClient, long sendingTimeInterval) {
        this.sendingTimeInterval = sendingTimeInterval;
        httpClient = new HttpAsyncClient(server, port, true, asyncClient);
    }

    @Override
    public void head(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        httpClient.head(url, additionalHeaders, parameters, listener);
        throttle();
    }

    @Override
    public synchronized void get(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        httpClient.get(url, additionalHeaders, parameters, listener);
        throttle();
    }

    @Override
    public synchronized void put(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        httpClient.put(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
        throttle();
    }

    @Override
    public void delete(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        httpClient.delete(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
        throttle();
    }

    @Override
    public void patch(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        httpClient.patch(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
        throttle();
    }

    @Override
    public void connect(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        httpClient.connect(url, additionalHeaders, parameters, listener);
        throttle();
    }

    @Override
    public void trace(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        httpClient.trace(url, additionalHeaders, parameters, listener);
        throttle();
    }

    @Override
    public void postContent(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        throw new UnsupportedOperationException("Only methods HEAD, GET, PUT, DELETE can support HTTP Pipelining");
    }

    @Override
    public void postWithEncodedParameters(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        throw new UnsupportedOperationException("Only methods HEAD, GET, PUT, DELETE can support HTTP Pipelining");
    }

    @Override
    public void postFormData(String url, Set<Pair<String, String>> additionalHeaders, FormRequestData requestData, HttpListener listener) {
        throw new UnsupportedOperationException("Only methods HEAD, GET, PUT, DELETE can support HTTP Pipelining");
    }

    @Override
    public void optionsWithUrl(String url,
                               Set<Pair<String, String>> headers,
                               Set<Pair<String, String>> urlParameters,
                               HttpListener listener) {
        throw new UnsupportedOperationException("Only methods HEAD, GET, PUT, DELETE can support HTTP Pipelining");
    }

    @Override
    public void options(
            Set<Pair<String, String>> headers,
            HttpListener listener) {
        throw new UnsupportedOperationException("Only methods HEAD, GET, PUT, DELETE can support HTTP Pipelining");
    }

    private synchronized void throttle() {
        if (sendingTimeInterval > 0) {
            try {
                wait(sendingTimeInterval);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
