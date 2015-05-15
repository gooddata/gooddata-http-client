package com.gooddata.http.client;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;


public class SSTSessionTest {

    @Test
    public void test() throws Exception {
        final SSTSession sstSession = new SSTSession();
        sstSession.setSst("SST0");
        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final List<Future<String>> futures = executorService.invokeAll(asList(
                new SSTRunnable(sstSession, "SST1"),
                new SSTRunnable(sstSession, "SST2")));

        assertEquals(2, futures.size());
        assertEquals("SST1", futures.get(0).get());
        assertEquals("SST2", futures.get(1).get());

        executorService.shutdown();

        assertEquals("SST0", sstSession.obtainSst());
    }

    private static class SSTRunnable implements Callable<String> {

        private final SSTSession session;
        private final String sst;

        private SSTRunnable(SSTSession session, String sst) {
            this.session = session;
            this.sst = sst;
        }

        @Override
        public String call() {
            session.setSst(sst);
            try {
                return session.obtainSst();
            } catch (IOException e) {
                // ignore
            } finally {
                session.clear();
            }
            return null;
        }
    }
}