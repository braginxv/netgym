/*
 * The MIT License
 *
 * Copyright (c) 2022 Vladimir Bragin
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.techlook.ChannelListener;
import org.techlook.SocketClient;
import org.techlook.http.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class TestHttpAsyncClient {
    private static final String SERVER = "server";
    private static final String PATH = "/test/path";
    private static final String CONTENT_TYPE = "text/plain";
    private static final int PORT = 1234;
    private static final boolean IS_KEEPALIVE_CONNECTION = true;
    private static final String KEEPALIVE = IS_KEEPALIVE_CONNECTION ? "keep-alive" : "close";
    private static final int CHANNEL_ID = 12345;

    private static final List<Pair<String, String>> HEADERS = Arrays.asList(
            new Pair<>("User-Agent", "test agent"),
            new Pair<>("Header1", "value1"),
            new Pair<>("Header2", "value2"),
            new Pair<>("Header3", "value3")
    );

    private static final List<Pair<String, String>> PARAMETERS = Arrays.asList(
            new Pair<>("param1", "value1"),
            new Pair<>("param2", "value2"),
            new Pair<>("param3", "value3")
    );

    private static final String PARAMETER_PART = "?param1=value1&param2=value2&param3=value3";

    private static byte[] requestHeader(String method) {
        String headerPart = "Connection: " + KEEPALIVE + "\n" +
            "User-Agent: test agent\n" +
            "Header1: value1\n" +
            "Header2: value2\n" +
            "Header3: value3\n" +
            "Accept-Encoding: gzip, deflate";

        return (method + " " + PATH + PARAMETER_PART + " HTTP/1.1\n" +
                "Host: " + SERVER + "\n" + headerPart + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] requestHeader(String method, byte[] content) {
        String headerPart = "Connection: " + KEEPALIVE + "\n" +
                "User-Agent: test agent\n" +
                "Header1: value1\n" +
                "Header2: value2\n" +
                "Header3: value3\n" +
                "Content-type: " + CONTENT_TYPE + "; charset=UTF-8\n" +
                "Content-length: " + content.length + "\n" +
                "Accept-Encoding: gzip, deflate";

        return (method + " " + PATH + PARAMETER_PART + " HTTP/1.1\n" +
                "Host: " + SERVER + "\n" + headerPart + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    private HttpAsyncClient http;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private SocketClientTestImpl socketClient;

    @Mock
    HttpListener httpListener;

    @Before
    public void setUp() throws IOException {
        when(socketClient.connect(eq(new InetSocketAddress(SERVER, PORT)), any(ChannelListener.class)))
                .thenReturn(CHANNEL_ID);
        when(socketClient.send(any(byte[].class), anyInt(), anyInt(), eq(CHANNEL_ID))).thenCallRealMethod();
        when(socketClient.checkBuffer(any(byte[].class))).thenCallRealMethod();

        http = new HttpAsyncClient(SERVER, PORT, IS_KEEPALIVE_CONNECTION, socketClient);
    }

    @After
    public void tearDown() {
        http.close();
        http = null;
    }

    @Test
    public void testGetRequest() {
        http.get(PATH, HEADERS, PARAMETERS, httpListener);

        byte[] data = requestHeader(HttpAsyncClient.Method.GET);
        assertTrue(socketClient.checkBuffer(data));
    }

    @Test
    public void testPutRequest() {
        byte[] content = "test content\nto be sent".getBytes(StandardCharsets.UTF_8);
        http.put(PATH, HEADERS, PARAMETERS, CONTENT_TYPE, StandardCharsets.UTF_8, content, httpListener);

        byte[] data = concat(requestHeader(HttpAsyncClient.Method.PUT, content), content);
        assertTrue(socketClient.checkBuffer(data));
    }

    private static byte[] concat(byte[] array1, byte[] array2) {
        byte[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    private static abstract class SocketClientTestImpl implements SocketClient {
        private byte[] buffer = new byte[0];

        boolean checkBuffer(byte[] checkedBuffer) {
            return Arrays.equals(buffer, checkedBuffer);
        }

        @Override
        public boolean send(byte[] data, int offset, int length, Integer channelId) {
            if (buffer == null) {
                buffer = new byte[0];
            }
            int oldLength = buffer.length;
            buffer = Arrays.copyOf(buffer, oldLength + length);
            System.arraycopy(data, offset, buffer, oldLength, length);

            return true;
        }
    }
}
