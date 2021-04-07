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
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In this client HTTP requests follow each other using same connection to the server, thus each request waits till
 * another request is completed. This client may me used in cases if server supports keep-alive connections but
 * pipelining doesn't.
 */
public class SequentialConnection implements HttpConnection {
    private static final int MAX_WAITING_REQUESTS_IN_SEQUENCE = 128;

    private final HttpAsyncClient httpClient;
    private final BlockingQueue<ResponseListener> requestSequence =
            new LinkedBlockingDeque<>(MAX_WAITING_REQUESTS_IN_SEQUENCE);
    private final AtomicBoolean isRequestRunning = new AtomicBoolean(false);

    /**
     * Creation of this client and connection parameters saving
     * @param server       the remote server we need connect to
     * @param port         TCP port
     * @param asyncClient  asynchronous SocketClient instance to be used as transport for transmitting request
     */
    public SequentialConnection(String server, int port, SocketClient asyncClient) {
        httpClient = new HttpAsyncClient(server, port, true, asyncClient);
    }

    @Override
    public void get(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new GetListener(url, additionalHeaders, parameters, listener));
    }

    @Override
    public void put(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        putListener(new PutListener(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener));
    }

    @Override
    public void postContent(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        putListener(new PostContentListener(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener));
    }

    @Override
    public void postWithEncodedParameters(String url, List<Pair<String, String>> additionalHeaders, List<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new PostWithEncodedParametersListener(url, additionalHeaders, parameters, listener));
    }

    @Override
    public void postFormData(String url, List<Pair<String, String>> additionalHeaders, FormRequestData requestData, HttpListener listener) {
        putListener(new PostFormDataListener(url, additionalHeaders, requestData, listener));
    }

    @Override
    public void optionsWithUrl(String url, HttpListener listener) {
        putListener(new OptionsWithUrlListener(url, listener));
    }

    @Override
    public void options(HttpListener listener) {
        putListener(new OptionsListener(listener));
    }

    private synchronized void putListener(ResponseListener listener) {
        try {
            if (isRequestRunning.compareAndSet(false, true)) {
                listener.doRequest();
            } else {
                requestSequence.put(listener);
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void pollRequests() {
            ResponseListener nextListener = requestSequence.poll();
            if (nextListener != null) {
                isRequestRunning.set(true);
                nextListener.doRequest();
        }
    }

    private abstract class ResponseListener extends HttpListener {
        private final HttpListener listener;

        private ResponseListener(HttpListener listener) {
            this.listener = listener;
        }

        abstract void doRequest();

        @Override
        public void responseCode(int code, String httpVersion, String description) {
            listener.responseCode(code, httpVersion, description);
        }

        @Override
        public void respond(byte[] chunk) {
            listener.respond(chunk);
        }

        @Override
        public void complete() {
            isRequestRunning.set(false);
            pollRequests();
            listener.complete();
        }

        @Override
        public void failure(String message) {
            isRequestRunning.set(false);
            pollRequests();
            listener.failure(message);
        }

        @Override
        public Charset getCharset() {
            return listener.getCharset();
        }

        @Override
        public void respondHttpHeaders(Map<String, String> headers) {
            listener.respondHttpHeaders(headers);
        }

        @Override
        public void connectionClosed() {
            listener.connectionClosed();
        }
    }

    private class GetListener extends ResponseListener {
        private final String url;
        private final List<Pair<String, String>> additionalHeaders;
        private final List<Pair<String, String>> parameters;

        private GetListener(String url,
                            List<Pair<String, String>> additionalHeaders,
                            List<Pair<String, String>> parameters, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;
            this.parameters = parameters;
        }

        @Override
        void doRequest() {
            httpClient.get(url, additionalHeaders, parameters, this);
        }
    }

    private class PutListener extends ResponseListener {
        private final String url;
        private final List<Pair<String, String>> additionalHeaders;
        private final List<Pair<String, String>> urlParameters;
        private final String contentType;
        private final Charset contentCharset;
        private final byte[] content;

        private PutListener(String url, List<Pair<String, String>> additionalHeaders,
                            List<Pair<String, String>> urlParameters, String contentType,
                            Charset contentCharset, byte[] content, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;
            this.urlParameters = urlParameters;
            this.contentType = contentType;
            this.contentCharset = contentCharset;
            this.content = content;
        }

        @Override
        void doRequest() {
            httpClient.put(url, additionalHeaders, urlParameters, contentType, contentCharset, content, this);
        }
    }


    private class PostContentListener extends ResponseListener {
        private final String url;
        private final List<Pair<String, String>> additionalHeaders;
        private final List<Pair<String, String>> urlParameters;
        private final String contentType;
        private final Charset contentCharset;
        private final byte[] content;

        private PostContentListener(String url, List<Pair<String, String>> additionalHeaders,
                                    List<Pair<String, String>> urlParameters, String contentType,
                                    Charset contentCharset, byte[] content, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;
            this.urlParameters = urlParameters;
            this.contentType = contentType;
            this.contentCharset = contentCharset;
            this.content = content;
        }

        @Override
        void doRequest() {
            httpClient.postContent(url, additionalHeaders, urlParameters, contentType, contentCharset, content, this);
        }
    }

    private class PostWithEncodedParametersListener extends ResponseListener {
        private final String url;
        private final List<Pair<String, String>> additionalHeaders;
        private final List<Pair<String, String>> parameters;

        private PostWithEncodedParametersListener(String url, List<Pair<String, String>> additionalHeaders,
                                                  List<Pair<String, String>> parameters, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;

            this.parameters = parameters;
        }

        @Override
        void doRequest() {
            httpClient.postWithEncodedParameters(url, additionalHeaders, parameters, this);
        }
    }

    private class PostFormDataListener extends ResponseListener {
        private final String url;
        private final List<Pair<String, String>> additionalHeaders;
        private final FormRequestData requestData;

        private PostFormDataListener(String url, List<Pair<String, String>> additionalHeaders,
                                     FormRequestData requestData, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;
            this.requestData = requestData;
        }

        @Override
        void doRequest() {
            httpClient.postFormData(url, additionalHeaders, requestData, this);
        }
    }

    private class OptionsWithUrlListener extends ResponseListener {
        private final String url;

        private OptionsWithUrlListener(String url, HttpListener listener) {
            super(listener);
            this.url = url;
        }

        @Override
        void doRequest() {
            httpClient.optionsWithUrl(url, this);
        }
    }

    private class OptionsListener extends ResponseListener {
        private OptionsListener(HttpListener listener) {
            super(listener);
        }

        @Override
        void doRequest() {
            httpClient.options(this);
        }
    }
}
