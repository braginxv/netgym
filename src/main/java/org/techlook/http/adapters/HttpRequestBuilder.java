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

package org.techlook.http.adapters;

import org.techlook.*;
import org.techlook.http.*;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpRequestBuilder {
    private static final Pattern BASE_URL_PATTERN = Pattern.compile("(https?)://([^:/]+)(:\\d+)?(/.*)?");
    private final AtomicReference<HttpConnection> httpConnection = new AtomicReference<>();
    private HttpClient httpClient;
    private String server;
    private int port;
    private String baseUrl;
    private List<Pair<String, String>> additionalHeaders;
    private List<Pair<String, String>> parameters;


    public HttpRequestBuilder baseUrl(String url) {
        Matcher matcher = BASE_URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Request should be HTTP or HTTPS");
        }

        String protocol = matcher.group(1);
        httpClient = HttpClient.fromProtocol(protocol);
        server = matcher.group(2);
        String tcpPort = matcher.group(3);
        port = tcpPort == null ? httpClient.port() : Integer.parseInt(tcpPort.substring(1));
        baseUrl = matcher.group(4);

        return this;
    }

    public HttpRequestBuilder configureConnection(ConnectionType connectionType) {
        checkBaseUrlSpecified();

        switch (connectionType) {
            case Single:
                httpConnection.set(new SingleConnection(server, port, httpClient.socketClient()));
                break;
            case Persistent:
                httpConnection.set(new SequentialConnection(server, port, httpClient.socketClient()));
                break;
            case Pipelining:
                httpConnection.set(new PipeliningConnection(server, port, httpClient.socketClient()));
                break;
        }

        return this;
    }

    public HttpRequestBuilder configurePipeliningConnection(long sendingInterval) {
        httpConnection.set(new PipeliningConnection(server, port, httpClient.socketClient(), sendingInterval));

        return this;
    }

    public HttpRequestBuilder addHeader(String header, String value) {
        if (additionalHeaders == null) {
            additionalHeaders = new LinkedList<>();
        }

        additionalHeaders.add(new Pair<>(header, value));

        return this;
    }

    public HttpRequestBuilder addRequestParameter(String name, String value) {
        if (parameters == null) {
            parameters = new LinkedList<>();
        }

        parameters.add(new Pair<>(name, value));

        return this;
    }

    public void asyncRawGET(String resource, final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().get(baseUrl + resource, additionalHeaders, parameters, listener);
    }

    public ResultedCompletion<Response> syncRawGET(String resource) {
        prepareConnection();
        SyncByteResponseListener listener = new SyncByteResponseListener();
        httpConnection.get().get(baseUrl + resource, additionalHeaders, parameters, listener);

        return listener.watchCompletion();
    }

    public void asyncGET(String resource, final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().get(baseUrl + resource, additionalHeaders, parameters, listener);
    }

    public ResultedCompletion<StringResponse> syncGET(String resource) {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();
        httpConnection.get().get(baseUrl + resource, additionalHeaders, parameters, listener);

        return listener.watchCompletion();
    }

    public void asyncPUT(String resource, String contentType, Charset contentCharset,
                              byte[] content, final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().put(baseUrl + resource, additionalHeaders, parameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<Response> syncPUT(String resource, String contentType, Charset contentCharset,
                                                     byte[] content) {
        prepareConnection();

        SyncByteResponseListener listener = new SyncByteResponseListener();

        httpConnection.get().put(baseUrl + resource, additionalHeaders, parameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncRawPOST(String resource, String contentType, Charset contentCharset,
                          byte[] content, final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postContent(baseUrl + resource, additionalHeaders, parameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<Response> syncRawPOST(String resource, String contentType, Charset contentCharset,
                                                 byte[] content) {
        prepareConnection();

        SyncByteResponseListener listener = new SyncByteResponseListener();

        httpConnection.get().postContent(baseUrl + resource, additionalHeaders, parameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncPOST(String resource, String contentType, Charset contentCharset,
                                       byte[] content, final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postContent(baseUrl + resource, additionalHeaders, parameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<StringResponse> syncPOST(String resource, String contentType,
                                                                    Charset contentCharset, byte[] content) {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().postContent(baseUrl + resource, additionalHeaders, parameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncEncodedParametersRawPOST(String resource,
                                                      final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, additionalHeaders, parameters, listener);
    }

    public ResultedCompletion<Response> syncEncodedParametersRawPOST(String resource) {
        prepareConnection();

        SyncByteResponseListener listener = new SyncByteResponseListener();

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, additionalHeaders, parameters, listener);

        return listener.watchCompletion();
    }

    public void asyncEncodedParametersPOST(String resource, final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, additionalHeaders, parameters, listener);
    }

    public ResultedCompletion<StringResponse> syncEncodedParametersPOST(String resource) {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, additionalHeaders, parameters, listener);

        return listener.watchCompletion();
    }

    public void asyncFormDataPOST(String resource, final Consumer<Either<String, StringResponse>> consumer,
                                  FormRequestData formData) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postFormData(baseUrl + resource, additionalHeaders, formData, listener);
    }

    public ResultedCompletion<StringResponse> syncFormDataPOST(String resource, FormRequestData formData) {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().postFormData(baseUrl + resource, additionalHeaders, formData, listener);

        return listener.watchCompletion();
    }

    public void asyncOPTIONS(String resource, final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().optionsWithUrl(baseUrl + resource, listener);
    }

    public ResultedCompletion<StringResponse> syncOPTIONS(String resource) {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().optionsWithUrl(baseUrl + resource, listener);

        return listener.watchCompletion();
    }

    public void asyncServerOPTIONS(final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();

        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().options(listener);
    }

    public ResultedCompletion<StringResponse> syncServerOPTIONS() {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().options(listener);

        return listener.watchCompletion();
    }

    private void prepareConnection() {
        httpConnection.compareAndSet(null, new SingleConnection(server, port, httpClient.socketClient()));
    }

    private void checkBaseUrlSpecified() {
        if (httpClient == null) {
            throw new IllegalStateException("Specify base url first");
        }
    }

    public enum ConnectionType {
        Single, Persistent, Pipelining
    }

    private enum HttpClient {
        Http {
            @Override
            int port() {
                return 80;
            }

            @Override
            SocketClient socketClient() {
                return ClientSystem.client();
            }
        }, Https {
            @Override
            int port() {
                return 443;
            }

            @Override
            SocketClient socketClient() {
                return ClientSystem.sslClient();
            }
        };

        abstract int port();

        abstract SocketClient socketClient();

        private static final String HTTP = "http";

        private static final String HTTPS = "https";

        static HttpClient fromProtocol(String protocol) {
            switch (protocol) {
                case HTTP:
                    return Http;
                case HTTPS:
                    return Https;
                default:
                    throw new IllegalArgumentException("Only HTTP and HTTPS protocols should be specified");
            }
        }
    }
}
