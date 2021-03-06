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

package org.techlook.http.client;

import org.techlook.ChannelListener;
import org.techlook.Fault;
import org.techlook.SocketClient;
import org.techlook.http.FormField;
import org.techlook.http.FormRequestData;
import org.techlook.http.HttpConnection;
import org.techlook.http.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HttpAsyncClient implements HttpConnection, ChannelListener {
    private final static Logger log = Logger.getLogger(HttpAsyncClient.class.getName());

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
    public void get(String url,
                    List<Pair<String, String>> additionalHeaders,
                    List<Pair<String, String>> parameters,
                    HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(url);

        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(parameters));
        }

        final String requestHeader = String.format(
                "GET %s HTTP/1.1\nHost: %s\nConnection: %s%sAccept-Encoding: gzip, deflate\n\n",
                request,
                server,
                isPersistent ? "keep-alive" : "close",
                encodeHeaders(additionalHeaders));

        sendViaTransport(requestHeader);
    }

    @Override
    public void put(String url,
                    List<Pair<String, String>> additionalHeaders,
                    List<Pair<String, String>> urlParameters,
                    String contentType,
                    Charset contentCharset,
                    byte[] content,
                    HttpListener listener) {
        putListener(listener);
        sendContent("PUT",
                url, additionalHeaders, urlParameters, contentType, contentCharset, content);
    }

    @Override
    public void postContent(String url,
                            List<Pair<String, String>> additionalHeaders,
                            List<Pair<String, String>> urlParameters,
                            String contentType,
                            Charset contentCharset,
                            byte[] content,
                            HttpListener listener) {
        putListener(listener);
        sendContent("POST",
                url, additionalHeaders, urlParameters, contentType, contentCharset, content);
    }

    @Override
    public void postWithEncodedParameters(String url,
                                          List<Pair<String, String>> additionalHeaders,
                                          List<Pair<String, String>> parameters,
                                          HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(url);

        String parametersHeader = "";
        String body = "";
        if (parameters != null && !parameters.isEmpty()) {
            body = encodeParameters(parameters);
            parametersHeader = "Content-Type: application/x-www-form-urlencoded\n" +
                    "Content-Length: " + body.length();
        }
        final String requestHeader = String.format(
                "POST %s HTTP/1.1\nHost: %s\nConnection: %s\n%s%s\nAccept-Encoding: gzip, deflate\n\n",
                request,
                server,
                isPersistent ? "keep-alive" : "close",
                encodeHeaders(additionalHeaders),
                parametersHeader);

        sendViaTransport(requestHeader);
        if (!body.isEmpty()) {
            sendViaTransport(body);
        }
    }

    @Override
    public void postFormData(String url,
                             List<Pair<String, String>> additionalHeaders,
                             FormRequestData requestData,
                             HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(url);

        String formFieldsHeader = "";

        if (!requestData.isEmpty()) {
            formFieldsHeader = "Content-Type: multipart/form-data;boundary=\"" + Boundary.value + "\"\n";
        }
        final String requestHeader = String.format(
                "POST %s HTTP/1.1\nHost: %s\nConnection: %s\n%s%sAccept-Encoding: gzip, deflate\n\n",
                request,
                server,
                isPersistent ? "keep-alive" : "close",
                encodeHeaders(additionalHeaders),
                formFieldsHeader);

        sendViaTransport(requestHeader);
        sendFormData(requestData);
    }

    @Override
    public void optionsWithUrl(String url, HttpListener listener) {
        putListener(listener);
        StringBuilder request = initRequestBuilder(url);

        final String requestHeader = String.format(
                "OPTIONS %s HTTP/1.1\nHost: %s\n\n",
                request,
                server);

        sendViaTransport(requestHeader);
    }

    @Override
    public void options(HttpListener listener) {
        putListener(listener);
        final String requestHeader = String.format("OPTIONS * HTTP/1.1\nHost: %s\n\n", server);
        sendViaTransport(requestHeader);
    }

    private void sendContent(String method,
                             String url,
                             List<Pair<String, String>> additionalHeaders,
                             List<Pair<String, String>> urlParameters,
                             String contentType,
                             Charset contentCharset,
                             byte[] content) {
        StringBuilder request = initRequestBuilder(url);
        String contentHeaders = "";
        if (contentType != null && content != null) {
            String charset = contentCharset == null ? "" : "; charset=" + contentCharset.name();
            contentHeaders = String.format("Content-type: %s%s\nContent-length: %d",
                    contentType, charset, content.length);
        }

        if (urlParameters != null && !urlParameters.isEmpty()) {
            request.append("?");
            request.append(encodeParameters(urlParameters));
        }

        final String requestHeader = String.format(
                "%s %s HTTP/1.1\nHost: %s\nConnection: %s\n%s%s\nAccept-Encoding: gzip, deflate\n\n",
                method,
                request,
                server,
                isPersistent ? "keep-alive" : "close",
                encodeHeaders(additionalHeaders),
                contentHeaders);

        sendViaTransport(requestHeader);
        sendViaTransport(content);
    }

    private HttpListener createSessionListener(final HttpListener listener) {
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

    private synchronized void putListener(final HttpListener listener) {
        HttpListener sessionListener = createSessionListener(listener);
        if (!httpSession.compareAndSet(null,
                new HttpSession(sessionListener, client.getThreadPool()))) {
            listeners.add(sessionListener);
        }
    }

    private void sendFormData(FormRequestData requestData) {
        if (!requestData.isEmpty()) {
            for (FormField field : requestData.getFields()) {
                sendViaTransport(String.format("--%s\n%s\n", Boundary.value, field.header()));
                sendViaTransport(field.body());
                sendViaTransport(String.format("\n--%s\n", Boundary.value));
            }
        }
    }

    private void sendViaTransport(String string) {
        sendViaTransport(string.getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void sendViaTransport(byte[] buffer) {
        if ((connectId.get() < 0 || !client.send(buffer, 0, buffer.length, connectId.get()))
                && reconnect()) {
            client.send(buffer, 0, buffer.length, connectId.get());
        }
    }

    private boolean reconnect() {
        try {
            connectId.set(client.connect(new InetSocketAddress(server, port), this));
        } catch (IOException e) {
            String errorMessage = Fault.AsyncClientError.format(e.getMessage());
            log.log(Level.SEVERE, errorMessage);
            return false;
        }

        return true;
    }

    private StringBuilder initRequestBuilder(String url) {
        StringBuilder request = new StringBuilder();
        if (!url.startsWith("/")) {
            request.append("/");
        }
        request.append(url);
        return request;
    }

    private String encodeParameters(List<Pair<String, String>> parameters) {
        StringBuilder request = new StringBuilder();

        for (Pair<String, String> parameter : parameters) {
            request.append(parameter.getKey()).append("=").append(parameter.getValue());
        }
        return request.toString();
    }

    private String encodeHeaders(List<Pair<String, String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "\n";
        }

        StringBuilder result = new StringBuilder();
        for (Pair<String, String> header : headers) {
            result.append(String.format("%s: %s\n", header.getKey(), header.getValue()));
        }

        return result.toString();
    }

    // ChannelListener methods

    @Override
    public void channelError(String message) {
        httpSession.get().getListener().failure(message);
        connectId.set(-1);
        log.log(Level.SEVERE, message);
    }

    @Override
    public void chunkIsReceived(byte[] chunk) {
        httpSession.get().read(chunk);
    }

    @Override
    public void close() {
        connectId.set(-1);
    }

    private static final class Boundary {
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
