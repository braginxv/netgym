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

import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.techlook.ChannelListener;
import org.techlook.SocketClient;
import org.techlook.http.FormRequestData;
import org.techlook.http.Pair;
import org.techlook.nio.AsyncSocketClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestHttpAsyncClient {
    private static final String SERVER = "server";
    private static final String PATH = "/test/path";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";
    private static final int PORT = 1234;
    private static final boolean IS_KEEPALIVE_CONNECTION = true;
    private static final String KEEPALIVE = IS_KEEPALIVE_CONNECTION ? "keep-alive" : "close";
    private static final int CHANNEL_ID = 12345;
    private static final ForkJoinPool pool = new ForkJoinPool(AsyncSocketClient.PARALLELISM_LEVEL);

    private static final Set<Pair<String, String>> HEADERS = new LinkedHashSet<>(Arrays.asList(
            new Pair<>("User-Agent", "test agent"),
            new Pair<>("Header1", "value1"),
            new Pair<>("Header2", "value2"),
            new Pair<>("Header3", "value3")
    ));

    private static final Set<Pair<String, String>> PARAMETERS = new LinkedHashSet<>(Arrays.asList(
            new Pair<>("param1", "value1"),
            new Pair<>("param2", "value2"),
            new Pair<>("param3", "value3")
    ));

    private static final String ENCODED_PARAMETERS = "param1=value1&param2=value2&param3=value3";

    private static byte[] requestHeader(String method, boolean hasContent) {
        String headerPart = "Connection: " + KEEPALIVE + "\n" +
                "User-Agent: test agent\n" +
                "Header1: value1\n" +
                "Header2: value2\n" +
                "Header3: value3" +
                (hasContent ? "\nAccept-Encoding: gzip, deflate" : "");

        return (method + " " + PATH + "?" + ENCODED_PARAMETERS + " HTTP/1.1\n" +
                "Host: " + SERVER + "\n" + headerPart + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] requestHeader(String method) {
        return requestHeader(method, true);
    }

    private static byte[] requestHeader(String method, byte[] content, Set<Pair<String, String>> headers,
                                        boolean encodeRequestParameters) {
        return requestHeader(method, content, headers, encodeRequestParameters, true);
    }

    private static byte[] requestHeader(String method, byte[] content, Set<Pair<String, String>> headers,
                                        boolean encodeRequestParameters, boolean insertContentLength) {
        String parametersPart = encodeRequestParameters ? "?" + ENCODED_PARAMETERS : "";

        StringBuilder specifiedHeaders = new StringBuilder();
        if (headers != null) {
            for (Pair<String, String> header : headers) {
                specifiedHeaders
                        .append(String.format("%s: %s", header.getKey(), header.getValue()))
                        .append("\n");
            }
        }

        String headerPart = "Connection: " + KEEPALIVE + "\n" +
                specifiedHeaders +
                (encodeRequestParameters ? "Content-type: " + CONTENT_TYPE + "; charset=UTF-8\n" : "") +
                (insertContentLength ? "Content-Length: " + content.length + "\n" : "") +
                "Accept-Encoding: gzip, deflate";

        return (method + " " + PATH + parametersPart + " HTTP/1.1\n" +
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
        doCallRealMethod().when(socketClient).checkBuffer(any(byte[].class));
        when(socketClient.getThreadPool()).thenReturn(pool);

        http = new HttpAsyncClient(SERVER, PORT, IS_KEEPALIVE_CONNECTION, socketClient);
    }

    @After
    public void tearDown() {
        http.close();
        http = null;
    }

    @AfterClass
    public static void ReleaseTestResources() {
        pool.shutdown();
    }

    @Test
    public void testGetRequest() {
        http.get(PATH, HEADERS, PARAMETERS, httpListener);

        byte[] data = requestHeader(HttpAsyncClient.Method.GET);
        socketClient.checkBuffer(data);
    }

    @Test
    public void testHeadRequest() {
        http.head(PATH, HEADERS, PARAMETERS, httpListener);

        byte[] data = requestHeader(HttpAsyncClient.Method.HEAD, false);
        socketClient.checkBuffer(data);
    }

    @Test
    public void testPutRequest() {
        byte[] content = "test content\nto be sent".getBytes(StandardCharsets.UTF_8);
        http.put(PATH, HEADERS, PARAMETERS, CONTENT_TYPE, StandardCharsets.UTF_8, content, httpListener);

        byte[] data = concat(requestHeader(HttpAsyncClient.Method.PUT, content, HEADERS, true), content);
        socketClient.checkBuffer(data);
    }

    @Test
    public void testDeleteWhenRequestHasNoContent() {
        http.delete(PATH, HEADERS, PARAMETERS, CONTENT_TYPE, StandardCharsets.UTF_8, null, httpListener);

        byte[] data = requestHeader(HttpAsyncClient.Method.DELETE);
        socketClient.checkBuffer(data);
    }

    @Test
    public void testPostRequest() {
        byte[] content = "test content\nto be sent".getBytes(StandardCharsets.UTF_8);
        http.postContent(PATH, HEADERS, PARAMETERS, CONTENT_TYPE, StandardCharsets.UTF_8, content, httpListener);

        byte[] data = concat(requestHeader(HttpAsyncClient.Method.POST, content, HEADERS, true), content);
        socketClient.checkBuffer(data);
    }

    @Test
    public void testPostRequestWithUrlEncodedParameters() {
        byte[] content = ENCODED_PARAMETERS.getBytes(StandardCharsets.UTF_8);
        http.postWithEncodedParameters(PATH, HEADERS, PARAMETERS, httpListener);

        Set<Pair<String, String>> headers = new LinkedHashSet<>(HEADERS);
        headers.add(new Pair<>("Content-Type", "application/x-www-form-urlencoded"));

        byte[] data = concat(requestHeader(HttpAsyncClient.Method.POST, content, headers, false), content);
        socketClient.checkBuffer(data);
    }

    @Test
    public void testPostRequestWithFormData() {
        final String stringField = "string field";
        final String binaryField = "binary field";
        final String fileField = "file field";
        final String fileName = "file_name.txt";
        final String boundary = HttpAsyncClient.Boundary.value;

        final String stringData = "string value";
        final byte[] binaryData = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        final byte[] fileData = new byte[]{0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d};

        FormRequestData formData = new FormRequestData();
        formData.addInputField(stringField, stringData, CONTENT_TYPE, StandardCharsets.UTF_8);
        formData.addInputField(binaryField, binaryData, BINARY_CONTENT_TYPE, null);
        formData.addFileField(fileField, fileName, fileData, BINARY_CONTENT_TYPE, null);

        http.postFormData(PATH, HEADERS, formData, httpListener);

        Set<Pair<String, String>> headers = new LinkedHashSet<>(HEADERS);
        headers.add(new Pair<>("Content-Type", "multipart/form-data;boundary=\""
                + boundary + "\""));

        byte[] stringPartContent = ("--" + boundary + "\n" +
                "Content-Disposition: form-data; name=\"" + stringField + "\"\n" +
                "Content-Type: " + CONTENT_TYPE + "; charset=UTF-8" + "\n\n" +
                stringData + "\n").getBytes(StandardCharsets.UTF_8);

        byte[] binaryPartContent = concat(("--" + boundary + "\n" +
                        "Content-Disposition: form-data; name=\"" + binaryField + "\"\n" +
                        "Content-Type: " + BINARY_CONTENT_TYPE + "\n\n").getBytes(StandardCharsets.UTF_8),
                concat(binaryData, "\n".getBytes(StandardCharsets.UTF_8)));

        byte[] filePartContent = concat(("--" + boundary + "\n" +
                        "Content-Disposition: form-data; name=\"" + fileField + "\"" +
                        "; filename=\"" + fileName + "\"\n" +
                        "Content-Type: " + BINARY_CONTENT_TYPE + "\n\n").getBytes(StandardCharsets.UTF_8),
                concat(fileData, ("\n--" + boundary + "\n").getBytes(StandardCharsets.UTF_8)));

        byte[] content = concat(concat(stringPartContent, binaryPartContent), filePartContent);

        byte[] data = concat(requestHeader(HttpAsyncClient.Method.POST, content, headers,
                false, false), content);
        socketClient.checkBuffer(data);
    }

    private static byte[] concat(byte[] array1, byte[] array2) {
        byte[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    private static abstract class SocketClientTestImpl implements SocketClient {
        private final static int WAIT_RESPONSE_TIMEOUT = 200;
        private volatile byte[] buffer = new byte[0];

        void checkBuffer(byte[] checkedBuffer) {
            try {
                Thread.sleep(WAIT_RESPONSE_TIMEOUT);
            } catch (InterruptedException e) {
                System.out.println("SocketClientTestImpl: waiting of the response was interrupted");
                ;
            }

            assertEquals(new String(buffer), new String(checkedBuffer));
        }

        @Override
        public synchronized boolean send(byte[] data, int offset, int length, Integer channelId) {
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
