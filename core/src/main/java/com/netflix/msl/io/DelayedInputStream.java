package com.netflix.msl.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URLConnection;

/**
 * A delayed input stream does not open the real input stream until
 * one of its methods is called.
 */
public class DelayedInputStream extends FilterInputStream {
    /**
     * Create a new delayed input stream that will not attempt to
     * construct the input stream from the URL connection until it is
     * actually needed (i.e. read from).
     *
     * @param conn backing URL connection.
     */
    public DelayedInputStream(final URLConnection conn) {
        super(null);
        this.conn = conn;
    }

    @Override
    public int available() throws IOException {
        if (in == null)
            in = conn.getInputStream();
        return super.available();
    }

    @Override
    public void close() throws IOException {
        if (in == null)
            in = conn.getInputStream();
        super.close();
    }

    @Override
    public synchronized void mark(final int readlimit) {
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        if (in == null)
            in = conn.getInputStream();
        return in.read();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (in == null)
            in = conn.getInputStream();
        return super.read(b, off, len);
    }

    @Override
    public int read(final byte[] b) throws IOException {
        if (in == null)
            in = conn.getInputStream();
        return super.read(b);
    }

    @Override
    public synchronized void reset() throws IOException {
        if (in == null)
            in = conn.getInputStream();
        super.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        if (in == null)
            in = conn.getInputStream();
        return super.skip(n);
    }

    /** URL connection providing the input stream. */
    private final URLConnection conn;
}
