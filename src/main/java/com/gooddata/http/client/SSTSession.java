package com.gooddata.http.client;

import java.io.IOException;

/**
 * Thread-safe {@link SSTRetrievalStrategy} capable to serve several SSTs (one per thread).
 *
 * Call {@link #setSst} to setup SST in your thread and {@link #clear} to remove that SST.
 */
public class SSTSession implements SSTRetrievalStrategy {

    private final ThreadLocal<String> sstThreadLocal = new ThreadLocal<String>();

    /**
     * Set SST for current thread. of there was already some SST set, it is overwritten.
     * @param sst sst to be set
     */
    public void setSst(final String sst) {
        sstThreadLocal.set(sst);
    }

    /**
     * Clear SST for current thread.
     */
    public void clear() {
        sstThreadLocal.remove();
    }

    @Override
    public String obtainSst() throws IOException {
        final String sst = sstThreadLocal.get();
        if (sst == null) {
            throw new IllegalStateException("Please set SST first in current thread!");
        }
        return sst;
    }
}
