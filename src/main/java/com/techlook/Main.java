package com.techlook;

import com.techlook.http.HttpAsyncClient;
import com.techlook.http.HttpListener;

import java.io.IOException;
import java.nio.charset.Charset;

public class Main {
    public static void main(String[] args) throws Exception {
        final AsyncSocketClient client = AsyncSocketClient.create(new AsyncSocketClient.ClientListener() {
            @Override
            public void asyncClientError(Fault error, Object ...args) {
                System.out.println(error.format(args));
            }
        });
        new HttpAsyncClient("zakon-kuzbass.ru", 80, true, client, new HttpListener() {
            @Override
            public void response(byte[] content, Charset charset) {
                System.out.print(new String(content, charset));
            }

            @Override
            public void complete() {
                System.out.println();
                try {
                    client.shutdown();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void failure(Fault fault, Object... args) {
                System.err.println(fault.format(args));
            }
        }).get("/", null);

        client.await();
    }
}
