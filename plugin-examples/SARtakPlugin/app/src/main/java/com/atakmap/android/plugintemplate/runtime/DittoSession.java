package com.atakmap.android.plugintemplate.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One Ditto instance for one operation, opened and closed on its own writer
 * thread (finding T2-1). Closing the old instance on an operation switch took
 * up to ~2.7 s of native work on ATAK's main thread; now the main thread only
 * queues it.
 *
 * <p>Everything runs on the session's {@link DittoWriteQueue}, in order:
 * open, then any writes, then close. That keeps the rule the old blocking
 * stop relied on: a store is never closed while a write to it is in flight,
 * which can SIGABRT in libdittoffi.so. Writes queued while the store is still
 * opening simply wait for it.
 *
 * <p>A new session opens only after the previous one has finished closing,
 * because both may use the same shared instance and persistence directory.
 * So after a switch, sync starts a moment later instead of the main thread
 * waiting for it.
 *
 * @param <H> the opened store and whatever must be closed with it
 */
public final class DittoSession<H> {

    /** Opens the store; runs on the writer thread. */
    public interface Opener<H> {
        H open() throws Throwable;
    }

    /** Told how opening went; runs on the writer thread. */
    public interface OpenListener<H> {
        void onOpened(H handle);

        void onOpenFailed(Throwable throwable);
    }

    /** Releases an opened store; runs on the writer thread. */
    public interface Closer<H> {
        void close(H handle);
    }

    /** A write against the opened store; runs on the writer thread. */
    public interface Write<H> {
        void run(H handle) throws Throwable;
    }

    private final DittoWriteQueue queue;
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile H handle;
    private volatile boolean closing;

    public DittoSession(DittoWriteQueue.FailureListener failureListener) {
        queue = new DittoWriteQueue(failureListener);
    }

    /**
     * Queues opening the store, after {@code previous} (may be null) has
     * finished closing. Returns straight away.
     */
    public void open(final DittoSession<?> previous, final Opener<H> opener,
            final OpenListener<H> listener) {
        queue.submit(null, new Runnable() {
            @Override
            public void run() {
                if (previous != null)
                    previous.awaitClosedUninterruptibly();
                if (closing)
                    return;
                H opened;
                try {
                    opened = opener.open();
                } catch (Throwable throwable) {
                    listener.onOpenFailed(throwable);
                    return;
                }
                handle = opened;
                listener.onOpened(opened);
            }
        });
    }

    /**
     * Queues a write to run once the store is open. It is skipped if opening
     * failed or the session is closing.
     *
     * @return false if the session is already closing
     */
    public boolean submit(String key, final Write<H> write) {
        if (closing)
            return false;
        return queue.submit(key, new Runnable() {
            @Override
            public void run() {
                // Null if opening failed. Close is always queued last, so a
                // write never runs against a released store.
                H target = handle;
                if (target == null)
                    return;
                try {
                    write.run(target);
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Throwable throwable) {
                    throw new WriteFailedException(throwable);
                }
            }
        });
    }

    /**
     * Queues closing the store after everything already queued, and returns
     * without waiting. Calling it again does nothing.
     */
    public void close(final Closer<H> closer) {
        if (closing)
            return;
        closing = true;
        queue.submit(null, new Runnable() {
            @Override
            public void run() {
                H target = handle;
                handle = null;
                try {
                    if (target != null)
                        closer.close(target);
                } finally {
                    closed.countDown();
                }
            }
        });
        queue.finish();
    }

    /** The open store, or null while opening, after a failure or once closed. */
    public H getHandle() {
        return handle;
    }

    public boolean isClosing() {
        return closing;
    }

    /** Waits up to {@code timeoutMs} for {@link #close} to finish. */
    public boolean awaitClosed(long timeoutMs) {
        try {
            return closed.await(Math.max(0L, timeoutMs),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String getSummary() {
        return queue.getSummary();
    }

    private void awaitClosedUninterruptibly() {
        boolean interrupted = false;
        while (true) {
            try {
                closed.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /** Carries a checked failure from a write to the queue's listener. */
    public static final class WriteFailedException extends RuntimeException {
        WriteFailedException(Throwable cause) {
            super(cause);
        }
    }
}
