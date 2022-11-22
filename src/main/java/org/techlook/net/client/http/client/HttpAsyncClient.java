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

import org.techlook.net.client.ChannelListener;
import org.techlook.net.client.Fault;
import org.techlook.net.client.SocketClient;
import org.techlook.net.client.http.FormField;
import org.techlook.net.client.http.FormRequestData;
import org.techlook.net.client.http.HttpConnection;
import org.techlook.net.client.http.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class HttpAsyncClient implements HttpConnection, ChannelListener {
    public static final class Method {
        public static final String GET = "GET";
        public static final String PUT = "PUT";
        public static final String DELETE = "DELETE";
        public static final String OPTIONS = "OPTIONS";
        public static final String POST = "POST";
        public static final String HEAD = "HEAD";
        public static final String PATCH = "PATCH";
        public static final String CONNECT = "CONNECT";
        public static final String TRACE = "TRACE";
    }

    private final String server;
    private final int port;
    private final SocketClient client;
    private final boolean isPersistent;
    private final ConcurrentLinkedQueue<HttpListener> listeners = new ConcurrentLinkedQueue<>();
    private final AtomicInteger connectId = new AtomicInteger(-1);
    private final AtomicReference<HttpSession> httpSession = new AtomicReference<>();

    public HttpAsyncClient(String server, int port, boolean isPersistent, SocketClient asyncClient) {
        this.client = asyncClient;
        this.server = server;
        this.port = port;
        this.isPersistent = isPersistent;
    }

    @Override
    public void head(String path,
                     Set<Pair<String, String>> headers,
                     Set<Pair<String, String>> parameters,
                     HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(parameters));
        }

        final String requestHeader = requestHeader(Method.HEAD, request.toString(), headers, null, false);

        sendViaTransport(requestHeader);
    }

    @Override
    public void get(String path,
                    Set<Pair<String, String>> headers,
                    Set<Pair<String, String>> parameters,
                    HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(parameters));
        }

        final String requestHeader = requestHeader(Method.GET, request.toString(), headers, null, true);

        sendViaTransport(requestHeader);
    }

    @Override
    public void put(String path,
                    Set<Pair<String, String>> headers,
                    Set<Pair<String, String>> urlParameters,
                    String contentType,
                    Charset contentCharset,
                    byte[] content,
                    HttpListener listener) {
        putListener(listener);
        sendContent(Method.PUT,
                path, headers, urlParameters, contentType, contentCharset, content);
    }

    @Override
    public void delete(String path,
                       Set<Pair<String, String>> headers,
                       Set<Pair<String, String>> urlParameters,
                       String contentType,
                       Charset contentCharset,
                       byte[] content,
                       HttpListener listener) {
        putListener(listener);
        sendContent(Method.DELETE,
                path, headers, urlParameters, contentType, contentCharset, content);
    }

    @Override
    public void patch(String path,
                      Set<Pair<String, String>> headers,
                      Set<Pair<String, String>> urlParameters,
                      String contentType,
                      Charset contentCharset,
                      byte[] content,
                      HttpListener listener) {
        putListener(listener);
        sendContent(Method.PATCH,
                path, headers, urlParameters, contentType, contentCharset, content);
    }

    @Override
    public void connect(String path,
                        Set<Pair<String, String>> headers,
                        Set<Pair<String, String>> parameters,
                        HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(parameters));
        }

        final String requestHeader = requestHeader(Method.CONNECT, request.toString(), headers, null, true);

        sendViaTransport(requestHeader);
    }

    @Override
    public void trace(String path,
                      Set<Pair<String, String>> headers,
                      Set<Pair<String, String>> parameters,
                      HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(parameters));
        }

        final String requestHeader = requestHeader(Method.TRACE, request.toString(), headers, null, false);

        sendViaTransport(requestHeader);
    }

    @Override
    public void postContent(String path,
                            Set<Pair<String, String>> headers,
                            Set<Pair<String, String>> urlParameters,
                            String contentType,
                            Charset contentCharset,
                            byte[] content,
                            HttpListener listener) {
        putListener(listener);
        sendContent(Method.POST,
                path, headers, urlParameters, contentType, contentCharset, content);
    }

    @Override
    public void postWithEncodedParameters(String path,
                                          Set<Pair<String, String>> headers,
                                          Set<Pair<String, String>> parameters,
                                          HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        String parametersHeader = "";
        String body = "";
        if (parameters != null && !parameters.isEmpty()) {
            body = encodeParameters(parameters);
            parametersHeader = "Content-Type: application/x-www-form-urlencoded\n" +
                    "Content-Length: " + body.length() + "\n";
        }
        final String requestHeader = requestHeader(Method.POST, request.toString(), headers, parametersHeader, true);

        sendViaTransport(requestHeader);
        if (!body.isEmpty()) {
            sendViaTransport(body);
        }
    }

    @Override
    public void postFormData(String path,
                             Set<Pair<String, String>> headers,
                             FormRequestData requestData,
                             HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        String formFieldsHeader = "";

        if (!requestData.isEmpty()) {
            formFieldsHeader = "Content-Type: multipart/form-data;boundary=\"" + Boundary.value + "\"\n";
        }
        final String requestHeader = requestHeader(Method.POST, request.toString(), headers, formFieldsHeader, true);

        sendViaTransport(requestHeader.substring(0, requestHeader.length() - 1));
        sendFormData(requestData);
    }

    @Override
    public void options(Set<Pair<String, String>> headers, HttpListener listener) {
        putListener(listener);
        sendViaTransport(requestHeader(Method.OPTIONS, "*", headers, null, true));
    }

    @Override
    public void optionsWithUrl(String path,
                               Set<Pair<String, String>> headers,
                               Set<Pair<String, String>> parameters,
                               HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(path);

        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(parameters));
        }

        final String requestHeader = requestHeader(Method.OPTIONS, request.toString(), headers, null, true);

        sendViaTransport(requestHeader);
    }

    @Override
    public void channelError(String message) {
        httpSession.get().getListener().failure(message);
        connectId.set(-1);
    }

    @Override
    public void chunkIsReceived(byte[] chunk) {
        httpSession.get().read(chunk);
    }

    @Override
    public void close() {
        connectId.set(-1);
    }

    void sendContent(String method,
                     String path,
                     Set<Pair<String, String>> headers,
                     Set<Pair<String, String>> urlParameters,
                     String contentType,
                     Charset contentCharset,
                     byte[] content) {
        StringBuilder request = initRequestBuilder(path);
        String contentHeaders = "";
        if (contentType != null && content != null) {
            String charset = contentCharset == null ? "" : "; charset=" + contentCharset.name();
            contentHeaders = String.format("Content-type: %s%s\nContent-Length: %d\n",
                    contentType, charset, content.length);
        }

        if (urlParameters != null && !urlParameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(urlParameters));
        }

        final String requestHeader = requestHeader(method, request.toString(), headers, contentHeaders, true);

        sendViaTransport(requestHeader);
        if (content != null) {
            sendViaTransport(content);
        }
    }

    private String requestHeader(String method,
                                 String urlPart,
                                 Set<Pair<String, String>> headers,
                                 String contentHeaders,
                                 boolean hasResponseBody) {
        String responseEncoding = hasResponseBody ? "Accept-Encoding: gzip, deflate\n" : "";

        return String.format(
                "%s %s HTTP/1.1\nHost: %s\nConnection: %s\n%s%s%s\n",
                method,
                urlPart,
                server,
                isPersistent ? "keep-alive" : "close",
                encodeHeaders(headers),
                contentHeaders != null ? contentHeaders : "",
                responseEncoding);
    }

    HttpListener createSessionListener(final HttpListener listener) {
        return new HttpListener() {
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
                listener.complete();
                HttpListener nextListener = listeners.poll();
                httpSession.set(nextListener == null ? null : new HttpSession(nextListener, client.getThreadPool()));
            }

            @Override
            public void connectionClosed() {
                listener.connectionClosed();
            }

            @Override
            public void failure(String message) {
                listener.failure(message);
            }

            @Override
            public Charset getCharset() {
                return listener.getCharset();
            }

            @Override
            public void respondHttpHeaders(Map<String, String> headers) {
                listener.respondHeaders(headers);
            }
        };
    }

    synchronized void putListener(final HttpListener listener) {
        HttpListener sessionListener = createSessionListener(listener);
        if (!httpSession.compareAndSet(null,
                new HttpSession(sessionListener, client.getThreadPool()))) {
            listeners.add(sessionListener);
        }
    }

    void sendFormData(FormRequestData requestData) {
        if (!requestData.isEmpty()) {
            for (FormField field : requestData.getFields()) {
                sendViaTransport(String.format("\n--%s\n%s\n", Boundary.value, field.header()));
                sendViaTransport(field.body());
            }
            sendViaTransport(String.format("\n--%s\n", Boundary.value));
        }
    }

    private void sendViaTransport(String string) {
        sendViaTransport(string.getBytes(StandardCharsets.UTF_8));
    }

    private final Object connecting = new Object();
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private void sendViaTransport(byte[] buffer) {
        if (isConnecting.get()) {
            try {
                synchronized (connecting) {
                    connecting.wait();
                }
            } catch (InterruptedException ignored) {
                sendViaTransport(buffer);
            }
        }

        if ((connectId.get() < 0 || !client.send(buffer, 0, buffer.length, connectId.get()))) {
            if (isConnecting.compareAndSet(false, true)) {
                reconnectAndSend(buffer);
            } else {
                client.send(buffer, 0, buffer.length, connectId.get());
            }
        }
    }

    private void reconnectAndSend(final byte[] buffer) {
        client.getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    connectId.set(client.connect(new InetSocketAddress(HttpAsyncClient.this.server, port), HttpAsyncClient.this));
                    client.send(buffer, 0, buffer.length, connectId.get());
                } catch (IOException e) {
                    String errorMessage = Fault.AsyncClientError.format(e.getMessage());
                } finally {
                    synchronized (connecting) {
                        connecting.notify();
                    }
                    isConnecting.set(false);
                }
            }
        });
    }

    private StringBuilder initRequestBuilder(String url) {
        StringBuilder request = new StringBuilder();
        if (!url.startsWith("/")) {
            request.append("/");
        }
        request.append(url);
        return request;
    }

    private String encodeParameters(Set<Pair<String, String>> parameters) {
        StringBuilder request = new StringBuilder();

        Iterator<Pair<String, String>> iterator = parameters.iterator();
        while (iterator.hasNext()) {
            Pair<String, String> parameter = iterator.next();
            request.append(parameter.getKey()).append("=").append(parameter.getValue());

            if (iterator.hasNext()) {
                request.append("&");
            }
        }

        return request.toString();
    }

    private String encodeHeaders(Set<Pair<String, String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "\n";
        }

        StringBuilder result = new StringBuilder();
        for (Pair<String, String> header : headers) {
            result.append(String.format("%s: %s\n", header.getKey(), header.getValue()));
        }

        return result.toString();
    }

    static final class Boundary {
        static final String value;

        static {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(Double.SIZE / 8);
                buffer.putDouble(Math.random());
                buffer.flip();
                byte[] seq = new byte[buffer.remaining()];
                buffer.get(seq);
                seq = MessageDigest.getInstance("MD5").digest(seq);

                value = hex(seq);

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        private static String hex(byte[] bytes) {
            StringBuilder result = new StringBuilder();
            for (byte aByte : bytes) {
                result.append(String.format("%02X", aByte));
            }
            return result.toString();
        }
    }
}
