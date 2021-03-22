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

package org.techlook.http;

import org.techlook.SocketClient;
import org.techlook.http.client.HttpAsyncClient;
import org.techlook.http.client.HttpListener;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Single connection supposes each HTTP request opens and closes one TCP connection. This type of HTTP client should
 * use in cases when server doesn't support keep-alive connections (in particular pipelining connections). To be assumed
 * only one instance of the SocketClient will be used to performing all requests.
 */
public class SingleConnectionHttp implements Http {
    private final HttpAsyncClient httpClient;

    /**
     * Creation of this client and connection parameters saving
     * @param server       the remote server we need connect to
     * @param port         TCP port
     * @param asyncClient  asynchronous SocketClient instance to be used as transport for transmitting request
     */
    public SingleConnectionHttp(String server, int port, SocketClient asyncClient) {
        httpClient = new HttpAsyncClient(server, port, false, asyncClient);
    }

    @Override
    public void get(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> parameters, HttpListener listener) {
        httpClient.get(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void put(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        httpClient.put(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
    }

    @Override
    public void postContent(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        httpClient.postContent(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener);
    }

    @Override
    public void postWithEncodedParameters(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> parameters, HttpListener listener) {
        httpClient.postWithEncodedParameters(url, additionalHeaders, parameters, listener);
    }

    @Override
    public void postFormData(String url, List<Pair<String, String>> additionalHeaders, FormRequestData requestData, HttpListener listener) {
        httpClient.postFormData(url, additionalHeaders, requestData, listener);
    }

    @Override
    public void optionsWithUrl(String url, HttpListener listener) {
        httpClient.optionsWithUrl(url, listener);
    }

    @Override
    public void options(HttpListener listener) {
        httpClient.options(listener);
    }
}
