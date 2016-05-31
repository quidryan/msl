package com.netflix.msl.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handle to two stream, one for input stream and output stream.
 *
 * Created by jryan on 5/27/16.
 */
public class InputOutputPair {
    private final InputStream inputStream;
    private final OutputStream outputStream;

    /** Should the caller close the streams. */
    private final boolean shouldClose;

    public InputOutputPair(InputStream inputStream, OutputStream outputStream, boolean shouldClose) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.shouldClose = shouldClose;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public boolean shouldClose() {
        return shouldClose;
    }
    /**
     * Close any open streams, if the originator wanted the streams closed.
     */
    public void close() {
        if (shouldClose) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "InputOutputPair{" +
                "inputStream=" + inputStream.toString() +
                ", outputStream=" + outputStream.toString() +
                ", shouldClose=" + shouldClose +
                '}';
    }
}
