package com.atakmap.android.plugintemplate.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DittoWriteQueueTest {

    private static final long WAIT_MS = 5000L;

    private final List<String> ran =
            Collections.synchronizedList(new ArrayList<String>());
    private final List<Throwable> failures =
            Collections.synchronizedList(new ArrayList<Throwable>());
    private final DittoWriteQueue queue = new DittoWriteQueue(
            new DittoWriteQueue.FailureListener() {
                @Override
                public void onWriteFailed(Throwable throwable) {
                    failures.add(throwable);
                }
            });

    @After
    public void tearDown() {
        queue.shutdown(WAIT_MS);
    }

    @Test
    public void writesRunOnTheNamedWriterThreadNotTheCaller() {
        final AtomicReference<Thread> writer = new AtomicReference<>();
        queue.submit("device", new Runnable() {
            @Override
            public void run() {
                writer.set(Thread.currentThread());
            }
        });

        assertTrue(queue.drain(WAIT_MS));
        assertNotSame(Thread.currentThread(), writer.get());
        assertEquals(DittoWriteQueue.THREAD_NAME, writer.get().getName());
        assertTrue(writer.get().isDaemon());
        assertFalse(queue.isWriterThread());
    }

    @Test
    public void writesRunInTheOrderTheyWereQueued() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String id = "doc-" + i;
            expected.add(id);
            queue.submit(id, record(id));
        }

        assertTrue(queue.drain(WAIT_MS));
        assertEquals(expected, ran);
        assertEquals(0, queue.getPendingCount());
    }

    @Test
    public void waitingWriteToTheSameDocumentIsReplacedInPlace()
            throws Exception {
        CountDownLatch release = blockWriter();
        queue.submit("device", record("device v1"));
        queue.submit("line", record("line"));
        queue.submit("device", record("device v2"));
        queue.submit("device", record("device v3"));
        assertEquals(3, queue.getPendingCount());

        release.countDown();

        assertTrue(queue.drain(WAIT_MS));
        assertEquals(java.util.Arrays.asList("blocker", "device v3", "line"),
                ran);
        assertTrue(queue.getSummary(), queue.getSummary()
                .contains("merged=2"));
    }

    @Test
    public void writesWithoutAKeyAreNeverMerged() throws Exception {
        CountDownLatch release = blockWriter();
        queue.submit(null, record("strict mode"));
        queue.submit(null, record("strict mode"));
        release.countDown();

        assertTrue(queue.drain(WAIT_MS));
        assertEquals(java.util.Arrays.asList("blocker", "strict mode",
                "strict mode"), ran);
    }

    @Test
    public void writeQueuedWhileTheSameDocumentIsBeingWrittenStillRuns()
            throws Exception {
        final CountDownLatch running = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        queue.submit("device", new Runnable() {
            @Override
            public void run() {
                running.countDown();
                awaitQuietly(release);
                ran.add("device v1");
            }
        });
        assertTrue(running.await(WAIT_MS, TimeUnit.MILLISECONDS));

        // v1 has already read its data; v2 must not be folded into it.
        queue.submit("device", record("device v2"));
        release.countDown();

        assertTrue(queue.drain(WAIT_MS));
        assertEquals(java.util.Arrays.asList("device v1", "device v2"), ran);
    }

    @Test
    public void drainTimesOutWhileTheWriterIsStuck() throws Exception {
        CountDownLatch release = blockWriter();
        queue.submit("device", record("device"));

        assertFalse(queue.drain(50L));

        release.countDown();
        assertTrue(queue.drain(WAIT_MS));
        assertEquals(java.util.Arrays.asList("blocker", "device"), ran);
    }

    @Test
    public void drainWithNothingQueuedReturnsAtOnce() {
        assertTrue(queue.drain(0L));
        assertFalse(queue.isRunning());
    }

    @Test
    public void shutdownFinishesQueuedWritesThenStopsTheThread()
            throws Exception {
        final AtomicReference<Thread> first = new AtomicReference<>();
        queue.submit("a", captureThread(first));
        queue.submit("b", record("b"));

        assertEquals(0, queue.shutdown(WAIT_MS));
        assertFalse(queue.isRunning());
        assertTrue(ran.contains("b"));
        first.get().join(WAIT_MS);
        assertFalse(first.get().isAlive());

        // The next operation gets a fresh writer.
        final AtomicReference<Thread> second = new AtomicReference<>();
        queue.submit("a", captureThread(second));
        assertTrue(queue.drain(WAIT_MS));
        assertTrue(queue.isRunning());
        assertNotSame(first.get(), second.get());
    }

    @Test
    public void shutdownDropsWritesTheStuckWriterNeverReached()
            throws Exception {
        blockWriter();
        queue.submit("device", record("device"));
        queue.submit("line", record("line"));

        int dropped = queue.shutdown(50L);

        assertEquals(2, dropped);
        assertFalse(queue.isRunning());
        assertFalse(ran.contains("device"));
        assertFalse(ran.contains("line"));
        assertEquals(0, queue.getPendingCount());
    }

    @Test
    public void failedWriteIsReportedAndLaterWritesStillRun() {
        final IllegalStateException boom = new IllegalStateException("boom");
        queue.submit("grid", new Runnable() {
            @Override
            public void run() {
                throw boom;
            }
        });
        queue.submit("line", record("line"));

        assertTrue(queue.drain(WAIT_MS));
        assertEquals(Collections.singletonList(boom), failures);
        assertEquals(Collections.singletonList("line"), ran);
        assertTrue(queue.getSummary(), queue.getSummary()
                .contains("failed=1"));
    }

    @Test
    public void drainFromTheWriterThreadDoesNotDeadlock() {
        final AtomicReference<Boolean> drained = new AtomicReference<>();
        queue.submit("device", new Runnable() {
            @Override
            public void run() {
                drained.set(queue.drain(WAIT_MS));
            }
        });

        assertTrue(queue.drain(WAIT_MS));
        assertEquals(Boolean.TRUE, drained.get());
    }

    /** Occupies the writer until the returned latch is released. */
    private CountDownLatch blockWriter() throws InterruptedException {
        final CountDownLatch running = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        queue.submit("blocker", new Runnable() {
            @Override
            public void run() {
                running.countDown();
                awaitQuietly(release);
                ran.add("blocker");
            }
        });
        assertTrue(running.await(WAIT_MS, TimeUnit.MILLISECONDS));
        return release;
    }

    private Runnable record(final String label) {
        return new Runnable() {
            @Override
            public void run() {
                ran.add(label);
            }
        };
    }

    private Runnable captureThread(final AtomicReference<Thread> holder) {
        return new Runnable() {
            @Override
            public void run() {
                holder.set(Thread.currentThread());
            }
        };
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
