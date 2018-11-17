package com.techlook.http.content;

import com.techlook.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class TestSimpleContentReader {
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
        Substance substance = initWithSubstance(Substance.simple());

        Substance.readContent(substance.getContent(), reader);

        checkResult(completion.awaitResult(), substance);
    }

    @Test
    public void testTransmitWithDecoder() throws Exception {
        Substance substance = initWithSubstance(Substance.gzipped());
        reader.setDecoders(Collections.singletonList(Decoder.GZIP));

        Substance.readContent(substance.getContent(), reader);

        checkResult(completion.awaitResult(), Substance.simple());
    }

    private Substance initWithSubstance(Substance substance) {
        completion = new ResultedCompletion<>(20000);
        MockeryHttpListener listener = new MockeryHttpListener(completion);

        reader = new WholeContentReader(listener, substance.getCharset(), substance.getSize(), substance.getContent());

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
