package com.netflix.msl.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Provides Input/Output stream from a URL. Resulting InputStream will delay reading according to DelayedInputStream.
 *
 * Created by jryan on 5/27/16.
 */
public class UrlConnectionProvider implements ConnectionProvider {
    private final URL url;
    private final int timeout;

    public UrlConnectionProvider(URL url, int timeout) {
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public InputOutputPair provide(boolean shouldClose) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setDoOutput(true);
        connection.setDoInput(true);

        connection.connect();

        DelayedInputStream delayedInputStream = new DelayedInputStream(connection);
        OutputStream outputStream = connection.getOutputStream();

        return new InputOutputPair(delayedInputStream, outputStream, shouldClose);
    }
}
