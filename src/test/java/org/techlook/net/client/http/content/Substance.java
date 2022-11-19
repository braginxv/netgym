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

package org.techlook.net.client.http.content;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Substance {
    private static String SIMPLE = "response.dat";
    private static String CHUNKED = "response-chunked.dat";
    private static String GZIPPED = "response-gzip.dat";
    private static String CHUNCKED_GZIPPED = "response-chunked-gzip.dat";

    private static Substance simple;
    private static Substance chunked;
    private static Substance gzipped;
    private static Substance chunckedGzipped;
    private static ForkJoinPool threadPool;

    private final String response;
    private final byte[] content;
    private final String header;
    private final int size;
    private Charset charset;

    private Substance(String fileName) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(
                    new File(getClass().getClassLoader().getResource(fileName).getPath()).toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        response = new String(bytes);

        int index = response.indexOf("\r\n\r\n");
        int contentBias = 4;
        header = response.substring(0, index);
        int headerSize = index + contentBias;
        content = Arrays.copyOfRange(bytes, headerSize, bytes.length);

        Matcher matcher = Pattern.compile("Content-Length:\\s*(\\S+)").matcher(header);
        size = matcher.matches() ? Integer.valueOf(matcher.group(1)) : bytes.length - headerSize;

        matcher = Pattern.compile("Content-Type:.*charset=(\\S+)").matcher(header);
        charset = matcher.matches() ? Charset.forName(matcher.group(1)) : Charset.forName("utf-8");
    }

    public static Substance simple() {
        if (simple == null) {
            simple = new Substance(SIMPLE);
        }

        return simple;
    }

    public static Substance chunked() {
        if (chunked == null) {
            chunked = new Substance(CHUNKED);
        }

        return chunked;
    }

    public static Substance gzipped() {
        if (gzipped == null) {
            gzipped = new Substance(GZIPPED);
        }

        return gzipped;
    }

    public static Substance chunkedGzipped() {
        if (chunckedGzipped == null) {
            chunckedGzipped = new Substance(CHUNCKED_GZIPPED);
        }

        return chunckedGzipped;
    }

    public static void readContent(final byte[] content, final ContentReader reader) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                final int READ_BLOCK_SIZE = 0x100;

                int index = 0;
                int size = content.length;

                while (index < size - 1) {
                    reader.read(Arrays.copyOfRange(content, index, index += Math.min(READ_BLOCK_SIZE, size - index)));
                }
            }
        });
    }

    public static void setThreadPool(ForkJoinPool threadPool) {
        Substance.threadPool = threadPool;
    }

    public String getResponse() {
        return response;
    }

    public byte[] getContent() {
        return content;
    }

    public String getHeader() {
        return header;
    }

    public int getSize() {
        return size;
    }

    public Charset getCharset() {
        return charset;
    }
}
