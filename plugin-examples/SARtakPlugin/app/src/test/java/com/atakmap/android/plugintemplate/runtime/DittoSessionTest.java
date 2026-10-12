package com.atakmap.android.plugintemplate.runtime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Opening and closing a store on its writer thread (finding T2-1): the
 * caller never waits for it, writes still never run against a closed store,
 * and a new store only opens once the old one has closed.
 */
public class DittoSessionTest {

    private static final long WAIT_MS = 5000L;

    private final List<String> events =
            Collections.synchronizedList(new ArrayList<String>());

    private DittoSession<String> session() {
        return new DittoSession<>(null);
    }

    /** Opens the session; the returned latch is released once opening ends. */
    private CountDownLatch open(DittoSession<String> session,
            DittoSession<?> previous, final String name,
            final CountDownLatch gate) {
        final CountDownLatch done = new CountDownLatch(1);
        session.open(previous, new DittoSession.Opener<String>() {
            @Override
            public String open() throws Throwable {
                if (gate != null)
                    gate.await();
                events.add("open " + name);
                return name;
            }
        }, new DittoSession.OpenListener<String>() {
            @Override
            public void onOpened(String handle) {
                events.add("opened " + handle);
                done.countDown();
            }

            @Override
            public void onOpenFailed(Throwable throwable) {
                events.add("failed " + throwable.getMessage());
                done.countDown();
            }
        });
        return done;
    }

    private void write(DittoSession<String> session, final String label) {
        session.submit(null, new DittoSession.Write<String>() {
            @Override
            public void run(String handle) {
                events.add("write " + label + " to " + handle);
            }
        });
    }

    private DittoSession.Closer<String> closer(final CountDownLatch gate) {
        return new DittoSession.Closer<String>() {
            @Override
            public void close(String handle) {
                try {
                    if (gate != null)
                        gate.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                events.add("close " + handle);
            }
        };
    }

    @Test
    public void openAndCloseDoNotBlockTheCaller() throws Exception {
        CountDownLatch slowOpen = new CountDownLatch(1);
        CountDownLatch slowClose = new CountDownLatch(1);
        DittoSession<String> session = session();

        CountDownLatch opened = open(session, null, "A", slowOpen);
        // Held by its gate, yet open() has returned.
        assertTrue(events.isEmpty());
        slowOpen.countDown();
        assertTrue(opened.await(WAIT_MS, TimeUnit.MILLISECONDS));

        session.close(closer(slowClose));
        // Likewise for close().
        assertFalse(events.contains("close A"));
        slowClose.countDown();
        assertTrue(session.awaitClosed(WAIT_MS));
        assertTrue(events.contains("close A"));
    }

    @Test
    public void writesQueuedWhileOpeningWaitAndRunBeforeTheClose()
            throws Exception {
        CountDownLatch slowOpen = new CountDownLatch(1);
        DittoSession<String> session = session();

        CountDownLatch opened = open(session, null, "A", slowOpen);
        write(session, "1");
        write(session, "2");
        slowOpen.countDown();
        assertTrue(opened.await(WAIT_MS, TimeUnit.MILLISECONDS));
        session.close(closer(null));

        assertTrue(session.awaitClosed(WAIT_MS));
        assertEquals(java.util.Arrays.asList("open A", "opened A",
                "write 1 to A", "write 2 to A", "close A"), events);
    }

    @Test
    public void writesAfterCloseAreRefused() throws Exception {
        DittoSession<String> session = session();
        assertTrue(open(session, null, "A", null)
                .await(WAIT_MS, TimeUnit.MILLISECONDS));
        session.close(closer(null));

        assertFalse(session.submit(null, new DittoSession.Write<String>() {
            @Override
            public void run(String handle) {
                events.add("late write");
            }
        }));
        assertTrue(session.awaitClosed(WAIT_MS));
        assertFalse(events.contains("late write"));
        assertNull(session.getHandle());
    }

    @Test
    public void theNextStoreOpensOnlyAfterThePreviousHasClosed()
            throws Exception {
        CountDownLatch slowClose = new CountDownLatch(1);
        DittoSession<String> first = session();
        assertTrue(open(first, null, "A", null)
                .await(WAIT_MS, TimeUnit.MILLISECONDS));
        first.close(closer(slowClose));

        DittoSession<String> second = session();
        CountDownLatch openedB = open(second, first, "B", null);
        write(second, "1");

        // B is waiting on A's close.
        assertFalse(second.awaitClosed(200L));
        assertFalse(events.contains("open B"));
        slowClose.countDown();
        assertTrue(openedB.await(WAIT_MS, TimeUnit.MILLISECONDS));
        second.close(closer(null));
        assertTrue(second.awaitClosed(WAIT_MS));

        assertTrue(events.indexOf("close A") < events.indexOf("open B"));
        assertTrue(events.contains("write 1 to B"));
    }

    @Test
    public void closingBeforeTheStoreOpensSkipsOpeningIt()
            throws Exception {
        CountDownLatch slowClose = new CountDownLatch(1);
        DittoSession<String> first = session();
        assertTrue(open(first, null, "A", null)
                .await(WAIT_MS, TimeUnit.MILLISECONDS));
        first.close(closer(slowClose));

        DittoSession<String> second = session();
        open(second, first, "B", null);
        second.close(closer(null));
        slowClose.countDown();

        assertTrue(second.awaitClosed(WAIT_MS));
        assertFalse(events.contains("open B"));
        assertFalse(events.contains("close B"));
    }

    @Test
    public void aFailedOpenIsReportedAndItsWritesAreSkipped()
            throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch gate = new CountDownLatch(1);
        DittoSession<String> session = session();
        session.open(null, new DittoSession.Opener<String>() {
            @Override
            public String open() throws Throwable {
                entered.countDown();
                gate.await();
                throw new IllegalStateException("no store");
            }
        }, new DittoSession.OpenListener<String>() {
            @Override
            public void onOpened(String handle) {
                events.add("opened");
            }

            @Override
            public void onOpenFailed(Throwable throwable) {
                events.add("failed " + throwable.getMessage());
            }
        });
        write(session, "1");
        assertTrue(entered.await(WAIT_MS, TimeUnit.MILLISECONDS));
        session.close(closer(null));
        gate.countDown();

        assertTrue(session.awaitClosed(WAIT_MS));
        assertEquals(Collections.singletonList("failed no store"), events);
    }

    @Test
    public void everythingRunsOnTheWriterThreadNotTheCaller() {
        final AtomicReference<Thread> opener = new AtomicReference<>();
        final AtomicReference<Thread> closerThread = new AtomicReference<>();
        DittoSession<String> session = session();
        session.open(null, new DittoSession.Opener<String>() {
            @Override
            public String open() {
                opener.set(Thread.currentThread());
                return "A";
            }
        }, new DittoSession.OpenListener<String>() {
            @Override
            public void onOpened(String handle) {
            }

            @Override
            public void onOpenFailed(Throwable throwable) {
            }
        });
        session.close(new DittoSession.Closer<String>() {
            @Override
            public void close(String handle) {
                closerThread.set(Thread.currentThread());
            }
        });

        assertTrue(session.awaitClosed(WAIT_MS));
        assertNotSame(Thread.currentThread(), opener.get());
        assertEquals(DittoWriteQueue.THREAD_NAME, opener.get().getName());
        assertEquals(opener.get(), closerThread.get());
    }
}
