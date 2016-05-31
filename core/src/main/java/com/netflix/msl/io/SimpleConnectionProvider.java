package com.netflix.msl.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Connection Provider that directly proxies an InputStream and OutputStream.
 *
 * Created by jryan on 5/27/16.
 */
public class SimpleConnectionProvider implements ConnectionProvider {
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public SimpleConnectionProvider(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    @Override
    public InputOutputPair provide() throws IOException {
        return new InputOutputPair(inputStream, outputStream);
    }
}
