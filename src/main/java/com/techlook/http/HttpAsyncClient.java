package com.techlook.http;

import com.techlook.AsyncSocketClient;
import com.techlook.ChannelListener;
import com.techlook.Fault;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HttpAsyncClient implements AsyncSocketClient.ClientListener {
    private final static Logger log = Logger.getLogger(HttpAsyncClient.class.getName());

    private final String server;
    private final int port;
    private final AsyncSocketClient client;
    private final boolean keepAlive;
    private final HttpListener listener;
    private Integer sessionId;
    private final ConcurrentMap<Integer, SessionBundle> sessions = new ConcurrentHashMap<>();


    public HttpAsyncClient(String server, int port, boolean keepAlive, AsyncSocketClient asyncClient, HttpListener listener) {
        this.client = asyncClient;
        this.server = server;
        this.port = port;
        this.keepAlive = keepAlive;
        this.listener = listener;
    }

    public void get(String uri, Map<String, String> parameters) {
        StringBuilder request = new StringBuilder();
        if (!uri.startsWith("/")) {
            request.append("/");
        }

        request.append(uri);
        if (parameters != null && !parameters.isEmpty()) {
            request.append("?");

            for (Map.Entry<String, String> parameter : parameters.entrySet()) {
                request.append(parameter.getKey()).append("=").append(parameter.getValue());
            }
        }

        final String requestBody = String.format(
                "GET %s HTTP/1.1\nHost: %s\nConnection: %s\nAccept-Encoding: gzip, deflate\n\n",
                request,
                server,
                keepAlive ? "keep-alive" : "close");

        byte[] body = requestBody.getBytes(Charset.forName("utf-8"));

        try {
            if (!keepAlive || sessionId == null || !sessions.containsKey(sessionId)) {
                SessionBundle sessionBundle = new SessionBundle(new HttpSession(listener));
                sessionId = client.connect(new InetSocketAddress(server, port), sessionBundle);
                sessionBundle.setId(sessionId);
                sessions.put(sessionId, sessionBundle);
            }

            client.send(body, 0, body.length, sessionId);
        } catch (IOException e) {
            log.log(Level.SEVERE, Fault.AsyncClientError.getDescription());
            listener.failure(Fault.AsyncClientError);
        }
    }

    @Override
    public void asyncClientError(Fault error, Object ...args) {
        listener.failure(error, args);
    }

    private class SessionBundle implements ChannelListener {
        private int id;
        private HttpSession session;

        public SessionBundle(HttpSession session) {
            this.session = session;
        }

        public void setId(int id) {
            this.id = id;
        }

        @Override
        public void channelError(Fault error) {
            listener.failure(error);
        }

        @Override
        public void response(byte[] chunk) {
            if (session == null) {
                Fault cause = Fault.RequiredSessionHasBeenClosed;

                log.log(Level.WARNING, cause.getDescription());
                listener.failure(cause);

                return;
            }

            session.read(chunk);
        }

        @Override
        public void close() {
            sessions.remove(id);
        }
    }
}
