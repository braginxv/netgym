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

package org.techlook.http.content;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.techlook.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestWholeContentReader {
    private static final int PARALLELISM = 4;

    private static ForkJoinPool threadPool;
    private ContentReader reader;
    private ResultedCompletion<String> completion;

    @BeforeClass
    public static void prepareEnvironment() {
        threadPool = new ForkJoinPool(PARALLELISM);
        Substance.setThreadPool(threadPool);
    }

    @AfterClass
    public static void shutdownEnvironment() throws InterruptedException {
        threadPool.shutdown();
        threadPool.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testTransmitWithoutDecoders() throws Exception {
        Substance substance = initWithSubstance(Substance.simple(), null);

        Substance.readContent(substance.getContent(), reader);

        checkResult(completion.awaitResult(), substance);
    }

    @Test
    public void testTransmitWithDecoder() throws Exception {
        Substance substance = initWithSubstance(Substance.gzipped(), Collections.singletonList(Decoder.GZIP));

        Substance.readContent(substance.getContent(), reader);

        checkResult(completion.awaitResult(), Substance.simple());
    }

    private Substance initWithSubstance(Substance substance, List<Decoder> decoders) {
        completion = new ResultedCompletion<>();
        MockHttpListener listener = new MockHttpListener(completion);

        reader = new WholeContentReader(listener, substance.getSize(), null, decoders, threadPool);

        return substance;
    }

    private void checkResult(final Either<String, String> result, final Substance substance) {
        result.right().apply(new Consumer<String>() {
            @Override
            public void consume(String response) {
                assertEquals(
                        deleteMetaName(new String(substance.getContent(), substance.getCharset())),
                        deleteMetaName(response));
            }
        });

        result.left().apply(new Consumer<String>() {
            @Override
            public void consume(String message) {
                fail(message);
            }
        });
    }

    private String deleteMetaName(String html) {
        return html.replaceAll("<meta name.*?>", "");
    }
}
