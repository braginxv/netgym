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

package org.techlook.net.client;

import org.techlook.net.client.nio.AsyncSocketClient;
import org.techlook.net.client.ssl.SSLSocketClient;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A singleton instance of network client used by all socket connection within this library.
 */
public class ClientSystem {
    private static final AtomicReference<SocketClient> client = new AtomicReference<>();
    private static final AtomicReference<SocketClient> sslClient = new AtomicReference<>();

    /**
     * Instance (Singleton)
     * @return instance
     */
    public static SocketClient client() {
        try {
            client.compareAndSet(null, AsyncSocketClient.run());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return client.get();
    }

    /**
     * SSL-version
     * @return SSL client instance
     */
    public static SocketClient sslClient(KeyManager[] keyManagers, TrustManager[] trustManagers) {
        sslClient.compareAndSet(null, new SSLSocketClient(client(), keyManagers, trustManagers));

        return sslClient.get();
    }

}
