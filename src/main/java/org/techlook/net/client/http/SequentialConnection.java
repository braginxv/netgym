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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In this case HTTP requests follow each other using the same connection, so each request waits until
 * the previous to complete. This connection kind may be used in cases when the remote host supports
 * keep-alive connections but pipelining doesn't.
 */
public class SequentialConnection implements HttpConnection {
    private static final int MAX_WAITING_REQUESTS_IN_SEQUENCE = 128;

    private final HttpAsyncClient httpClient;
    private final BlockingQueue<ResponseListener> requestSequence =
            new LinkedBlockingDeque<>(MAX_WAITING_REQUESTS_IN_SEQUENCE);
    private final AtomicBoolean isRequestRunning = new AtomicBoolean(false);

    /**
     * Constructor
     *
     * @param server      a remote host
     * @param port        TCP port
     * @param asyncClient asynchronous SocketClient instance used as a transport
     */
    public SequentialConnection(String server, int port, SocketClient asyncClient) {
        httpClient = new HttpAsyncClient(server, port, true, asyncClient);
    }

    @Override
    public void head(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new BaseListener(url, additionalHeaders, parameters, listener) {
            @Override
            void doRequest() {
                httpClient.head(url, additionalHeaders, parameters, this);
            }
        });
    }

    @Override
    public void get(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new BaseListener(url, additionalHeaders, parameters, listener) {
            @Override
            void doRequest() {
                httpClient.get(url, additionalHeaders, parameters, this);
            }
        });
    }

    @Override
    public void put(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        putListener(new BasePutListener(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener) {
            @Override
            void doRequest() {
                httpClient.put(url, additionalHeaders, urlParameters, contentType, contentCharset, content, this);
            }
        });
    }

    @Override
    public void delete(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        putListener(new DeleteListener(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener));
    }

    @Override
    public void patch(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        putListener(new BasePutListener(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener) {
            @Override
            void doRequest() {
                httpClient.patch(url, additionalHeaders, urlParameters, contentType, contentCharset, content, this);
            }
        });
    }

    @Override
    public void connect(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new BaseListener(url, additionalHeaders, parameters, listener) {
            @Override
            void doRequest() {
                httpClient.connect(url, additionalHeaders, parameters, this);
            }
        });
    }

    @Override
    public void trace(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new BaseListener(url, additionalHeaders, parameters, listener) {
            @Override
            void doRequest() {
                httpClient.trace(url, additionalHeaders, parameters, this);
            }
        });
    }

    @Override
    public void postContent(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> urlParameters, String contentType, Charset contentCharset, byte[] content, HttpListener listener) {
        putListener(new PostContentListener(url, additionalHeaders, urlParameters, contentType, contentCharset, content, listener));
    }

    @Override
    public void postWithEncodedParameters(String url, Set<Pair<String, String>> additionalHeaders, Set<Pair<String, String>> parameters, HttpListener listener) {
        putListener(new PostWithEncodedParametersListener(url, additionalHeaders, parameters, listener));
    }

    @Override
    public void postFormData(String url, Set<Pair<String, String>> additionalHeaders, FormRequestData requestData, HttpListener listener) {
        putListener(new PostFormDataListener(url, additionalHeaders, requestData, listener));
    }

    @Override
    public void optionsWithUrl(String url,
                               Set<Pair<String, String>> headers,
                               Set<Pair<String, String>> urlParameters,
                               HttpListener listener) {
        putListener(new OptionsWithUrlListener(url, headers, urlParameters, listener));
    }

    @Override
    public void options(
            Set<Pair<String, String>> headers,
            HttpListener listener) {
        putListener(new OptionsListener(headers, listener));
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

    private abstract class BaseListener extends ResponseListener {
        protected final String url;
        protected final Set<Pair<String, String>> additionalHeaders;
        protected final Set<Pair<String, String>> parameters;

        private BaseListener(String url,
                             Set<Pair<String, String>> additionalHeaders,
                             Set<Pair<String, String>> parameters, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;
            this.parameters = parameters;
        }
    }

    private abstract class BasePutListener extends ResponseListener {
        protected final String url;
        protected final Set<Pair<String, String>> additionalHeaders;
        protected final Set<Pair<String, String>> urlParameters;
        protected final String contentType;
        protected final Charset contentCharset;
        protected final byte[] content;

        private BasePutListener(String url, Set<Pair<String, String>> additionalHeaders,
                                Set<Pair<String, String>> urlParameters, String contentType,
                                Charset contentCharset, byte[] content, HttpListener listener) {
            super(listener);
            this.url = url;
            this.additionalHeaders = additionalHeaders;
            this.urlParameters = urlParameters;
            this.contentType = contentType;
            this.contentCharset = contentCharset;
            this.content = content;
        }
    }

    private class DeleteListener extends ResponseListener {
        private final String url;
        private final Set<Pair<String, String>> additionalHeaders;
        private final Set<Pair<String, String>> urlParameters;
        private final String contentType;
        private final Charset contentCharset;
        private final byte[] content;

        private DeleteListener(String url, Set<Pair<String, String>> additionalHeaders,
                               Set<Pair<String, String>> urlParameters, String contentType,
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
            httpClient.delete(url, additionalHeaders, urlParameters, contentType, contentCharset, content, this);
        }
    }


    private class PostContentListener extends ResponseListener {
        private final String url;
        private final Set<Pair<String, String>> additionalHeaders;
        private final Set<Pair<String, String>> urlParameters;
        private final String contentType;
        private final Charset contentCharset;
        private final byte[] content;

        private PostContentListener(String url, Set<Pair<String, String>> additionalHeaders,
                                    Set<Pair<String, String>> urlParameters, String contentType,
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
        private final Set<Pair<String, String>> additionalHeaders;
        private final Set<Pair<String, String>> parameters;

        private PostWithEncodedParametersListener(String url, Set<Pair<String, String>> additionalHeaders,
                                                  Set<Pair<String, String>> parameters, HttpListener listener) {
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
        private final Set<Pair<String, String>> additionalHeaders;
        private final FormRequestData requestData;

        private PostFormDataListener(String url, Set<Pair<String, String>> additionalHeaders,
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
        private final Set<Pair<String, String>> headers;
        private final Set<Pair<String, String>> urlParameters;

        private OptionsWithUrlListener(String url,
                                       Set<Pair<String, String>> headers,
                                       Set<Pair<String, String>> urlParameters,
                                       HttpListener listener) {
            super(listener);
            this.url = url;
            this.headers = headers;
            this.urlParameters = urlParameters;
        }

        @Override
        void doRequest() {
            httpClient.optionsWithUrl(url, headers, urlParameters, this);
        }
    }

    private class OptionsListener extends ResponseListener {
        private final Set<Pair<String, String>> headers;

        private OptionsListener(
                Set<Pair<String, String>> headers,
                HttpListener listener) {
            super(listener);
            this.headers = headers;
        }

        @Override
        void doRequest() {
            httpClient.options(headers, this);
        }
    }
}
