package com.techlook.http.content;

import com.techlook.http.HttpAsyncClient;

import java.nio.channels.WritableByteChannel;
import java.util.logging.Logger;

public enum Decoder {
    GZIP {
        private static final String TOKEN = "gzip";

        @Override
        public InflaterChannel createChannel(WritableByteChannel output) {
            return new InflaterChannel(output, true);
        }

        @Override
        protected String token() {
            return TOKEN;
        }
    },
    Deflate {
        private static final String TOKEN = "deflate";

        @Override
        public InflaterChannel createChannel(WritableByteChannel output) {
            return new InflaterChannel(output, false);
        }

        @Override
        protected String token() {
            return TOKEN;
        }
    };

    private final static Logger log = Logger.getLogger(HttpAsyncClient.class.getName());

    public boolean matchToken(String token) {
        return token().equals(token.trim().toLowerCase());
    }

    public abstract InflaterChannel createChannel(WritableByteChannel output);

    protected abstract String token();
}

