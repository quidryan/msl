package com.netflix.msl.io;

import java.io.IOException;

/**
 * Created by jryan on 5/27/16.
 */
// @FunctionalInterface
public interface ConnectionProvider {

    /**
     * Provide streams pair to an open connection.
     * @return pair of input stream and output stream.
     * @throws IOException
     */
    InputOutputPair provide(boolean shouldClose) throws IOException;
}
