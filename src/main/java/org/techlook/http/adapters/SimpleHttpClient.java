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

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleHttpClient {
    private final AtomicReference<HttpConnection> httpConnection = new AtomicReference<>();
    private final HttpClient httpClient;
    private final String server;
    private final int port;
    private final String baseUrl;
    private final Set<Pair<String, String>> commonHeaders = new HashSet<>();

    public enum ConnectionType {
        Single, Persistent, Pipelining
    }

    public SimpleHttpClient(String baseUrl) throws MalformedURLException {
        URL url = new URL(baseUrl);

        this.httpClient = HttpClient.fromProtocol(url.getProtocol());
        this.server = url.getHost();
        int tcpPort = url.getPort();
        this.port = tcpPort > 0 ? tcpPort : httpClient.port();
        String path = url.getPath();
        this.baseUrl = path.endsWith("/") ? path + "/" : path;
    }

    public SimpleHttpClient configureConnection(ConnectionType connectionType) {
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

    public SimpleHttpClient configurePipeliningConnection(long sendingInterval) {
        httpConnection.set(new PipeliningConnection(server, port, httpClient.socketClient(), sendingInterval));
        return this;
    }

    public SimpleHttpClient addHeader(String header, String value) {
        commonHeaders.add(new Pair<>(header, value));
        return this;
    }

    public SimpleHttpClient addHeaders(Set<Pair<String, String>> headers) {
        commonHeaders.addAll(headers);
        return this;
    }

    public SimpleHttpClient setTLSKeyManagers(KeyManager[] keyManagers) {
        HttpClient.setKeyManagers(keyManagers);
        return this;
    }

    public SimpleHttpClient setTLSTrustManagers(TrustManager[] trustManagers) {
        HttpClient.setTrustManagers(trustManagers);
        return this;
    }

    public void asyncRawGET(String resource, final Consumer<Either<String, Response>> consumer) {
        asyncRawGET(resource, null, null, consumer);
    }

    public void asyncRawGET(String resource,
                            Set<Pair<String, String>> requestParameters,
                            Set<Pair<String, String>> additionalHeaders,
                            final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().get(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);
    }

    public ResultedCompletion<Response> syncRawGET(String resource) {
        return syncRawGET(resource, null, null);
    }

    public ResultedCompletion<Response> syncRawGET(String resource,
                                                   Set<Pair<String, String>> requestParameters,
                                                   Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncByteResponseListener listener = new SyncByteResponseListener();
        httpConnection.get().get(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);

        return listener.watchCompletion();
    }

    public void asyncGET(String resource, final Consumer<Either<String, StringResponse>> consumer) {
        asyncGET(resource, null, null, consumer);
    }

    public void asyncGET(String resource,
                         Set<Pair<String, String>> requestParameters,
                         Set<Pair<String, String>> additionalHeaders,
                         final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().get(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);
    }

    public ResultedCompletion<StringResponse> syncGET(String resource) {
        return syncGET(resource, null, null);
    }

    public ResultedCompletion<StringResponse> syncGET(String resource,
                                                      Set<Pair<String, String>> requestParameters,
                                                      Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();

        SyncStringResponseListener listener = new SyncStringResponseListener();
        httpConnection.get().get(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);

        return listener.watchCompletion();
    }

    public void asyncPUT(String resource, String contentType, Charset contentCharset,
                         byte[] content, final Consumer<Either<String, StringResponse>> consumer) {
        asyncPUT(resource, contentType, contentCharset, content, null, null, consumer);
    }

    public void asyncPUT(String resource, String contentType, Charset contentCharset,
                         byte[] content,
                         Set<Pair<String, String>> requestParameters,
                         Set<Pair<String, String>> additionalHeaders,
                         final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };
        httpConnection.get().put(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<StringResponse> syncPUT(String resource, String contentType, Charset contentCharset,
                                                byte[] content) {
        return syncPUT(resource, contentType, contentCharset, content, null, null);

    }

    public ResultedCompletion<StringResponse> syncPUT(String resource, String contentType, Charset contentCharset,
                                                byte[] content,
                                                Set<Pair<String, String>> requestParameters,
                                                Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().put(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncDELETE(String resource, String contentType, Charset contentCharset,
                         byte[] content, final Consumer<Either<String, StringResponse>> consumer) {
        asyncDELETE(resource, contentType, contentCharset, content, null, null, consumer);
    }

    public void asyncDELETE(String resource, String contentType, Charset contentCharset,
                         byte[] content,
                         Set<Pair<String, String>> requestParameters,
                         Set<Pair<String, String>> additionalHeaders,
                         final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };
        httpConnection.get().delete(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<StringResponse> syncDELETE(String resource, String contentType, Charset contentCharset,
                                                byte[] content) {
        return syncDELETE(resource, contentType, contentCharset, content, null, null);

    }

    public ResultedCompletion<StringResponse> syncDELETE(String resource, String contentType, Charset contentCharset,
                                                byte[] content,
                                                Set<Pair<String, String>> requestParameters,
                                                Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().delete(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncRawPOST(String resource, String contentType, Charset contentCharset,
                             byte[] content, final Consumer<Either<String, Response>> consumer) {
        asyncRawPOST(resource, contentType, contentCharset, content, null, null, consumer);
    }

    public void asyncRawPOST(String resource, String contentType, Charset contentCharset,
                             byte[] content,
                             Set<Pair<String, String>> requestParameters,
                             Set<Pair<String, String>> additionalHeaders,
                             final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postContent(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<Response> syncRawPOST(String resource, String contentType, Charset contentCharset,
                                                    byte[] content) {
        return syncRawPOST(resource, contentType, contentCharset, content, null, null);
    }

    public ResultedCompletion<Response> syncRawPOST(String resource, String contentType, Charset contentCharset,
                                                    byte[] content,
                                                    Set<Pair<String, String>> requestParameters,
                                                    Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();

        SyncByteResponseListener listener = new SyncByteResponseListener();

        httpConnection.get().postContent(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncPOST(String resource, String contentType, Charset contentCharset,
                          byte[] content, final Consumer<Either<String, StringResponse>> consumer) {
        asyncPOST(resource, contentType, contentCharset, content, null, null, consumer);
    }

    public void asyncPOST(String resource, String contentType, Charset contentCharset,
                          byte[] content,
                          Set<Pair<String, String>> requestParameters,
                          Set<Pair<String, String>> additionalHeaders,
                          final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postContent(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);
    }

    public ResultedCompletion<StringResponse> syncPOST(String resource, String contentType,
                                                       Charset contentCharset, byte[] content) {
        return syncPOST(resource, contentType, contentCharset, content, null, null);
    }

    public ResultedCompletion<StringResponse> syncPOST(String resource, String contentType,
                                                       Charset contentCharset, byte[] content,
                                                       Set<Pair<String, String>> requestParameters,
                                                       Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().postContent(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters,
                contentType, contentCharset, content, listener);

        return listener.watchCompletion();
    }

    public void asyncEncodedParametersRawPOST(String resource,
                                              final Consumer<Either<String, Response>> consumer) {
        asyncEncodedParametersRawPOST(resource, null, null, consumer);
    }

    public void asyncEncodedParametersRawPOST(String resource,
                                              Set<Pair<String, String>> requestParameters,
                                              Set<Pair<String, String>> additionalHeaders,
                                              final Consumer<Either<String, Response>> consumer) {
        prepareConnection();
        ByteResponseListener listener = new ByteResponseListener() {
            @Override
            public void respond(Either<String, Response> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);
    }

    public ResultedCompletion<Response> syncEncodedParametersRawPOST(String resource) {
        return syncEncodedParametersRawPOST(resource, null, null);
    }

    public ResultedCompletion<Response> syncEncodedParametersRawPOST(String resource,
                                                                     Set<Pair<String, String>> requestParameters,
                                                                     Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncByteResponseListener listener = new SyncByteResponseListener();

        httpConnection.get().postWithEncodedParameters(baseUrl + resource,
                getHeadersWith(additionalHeaders), requestParameters, listener);

        return listener.watchCompletion();
    }

    public void asyncEncodedParametersPOST(String resource, final Consumer<Either<String, StringResponse>> consumer) {
        asyncEncodedParametersPOST(resource, null, null, consumer);
    }

    public void asyncEncodedParametersPOST(String resource,
                                           Set<Pair<String, String>> requestParameters,
                                           Set<Pair<String, String>> additionalHeaders,
                                           final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);
    }

    public ResultedCompletion<StringResponse> syncEncodedParametersPOST(String resource) {
        return syncEncodedParametersPOST(resource, null, null);
    }

    public ResultedCompletion<StringResponse> syncEncodedParametersPOST(String resource,
                                                                        Set<Pair<String, String>> requestParameters,
                                                                        Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().postWithEncodedParameters(baseUrl + resource, getHeadersWith(additionalHeaders), requestParameters, listener);

        return listener.watchCompletion();
    }

    public void asyncFormDataPOST(String resource,
                                  FormRequestData formData, final Consumer<Either<String, StringResponse>> consumer) {
        asyncFormDataPOST(resource, formData,  null, consumer);
    }

    public void asyncFormDataPOST(String resource,
                                  FormRequestData formData,
                                  Set<Pair<String, String>> additionalHeaders,
                                  final Consumer<Either<String, StringResponse>> consumer) {
        prepareConnection();
        StringResponseListener listener = new StringResponseListener() {
            @Override
            public void respondString(Either<String, StringResponse> response) {
                consumer.consume(response);
            }
        };

        httpConnection.get().postFormData(baseUrl + resource, getHeadersWith(additionalHeaders), formData, listener);
    }

    public ResultedCompletion<StringResponse> syncFormDataPOST(String resource, FormRequestData formData) {
        return syncFormDataPOST(resource, formData, null);
    }

    public ResultedCompletion<StringResponse> syncFormDataPOST(String resource, FormRequestData formData,
                                                               Set<Pair<String, String>> additionalHeaders) {
        prepareConnection();
        SyncStringResponseListener listener = new SyncStringResponseListener();

        httpConnection.get().postFormData(baseUrl + resource, getHeadersWith(additionalHeaders), formData, listener);
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

    private Set<Pair<String, String>> getHeadersWith(Set<Pair<String, String>> additionalHeaders) {
        Set<Pair<String, String>> headers;
        if (additionalHeaders != null) {
            headers = new HashSet<>(commonHeaders);
            headers.addAll(additionalHeaders);
        } else {
            headers = commonHeaders;
        }

        return headers;
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
                return ClientSystem.sslClient(keyManagers, trustManagers);
            }
        };

        private static final String HTTP = "http";
        private static final String HTTPS = "https";
        private static KeyManager[] keyManagers;
        private static TrustManager[] trustManagers;

        public static void setKeyManagers(KeyManager[] keyManagers) {
            HttpClient.keyManagers = keyManagers;
        }

        public static void setTrustManagers(TrustManager[] trustManagers) {
            HttpClient.trustManagers = trustManagers;
        }

        abstract int port();

        abstract SocketClient socketClient();



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
