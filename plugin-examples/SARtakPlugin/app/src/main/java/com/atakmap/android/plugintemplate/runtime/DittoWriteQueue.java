package com.atakmap.android.plugintemplate.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Runs Ditto store writes on one background thread so they never block
 * ATAK's main thread. DittoSdkBridge wraps each write in runBlocking, which
 * waits on Ditto's disk and sync locks; the periodic publishes used to do
 * that on the main looper every few seconds.
 *
 * One thread keeps writes in the order they were queued. A write queued
 * with a key that is still waiting to run replaces the waiting one in
 * place, so a slow store cannot pile up stale copies of the same document
 * (the device state is re-published every few seconds); writes with
 * different keys, or no key, are never merged.
 *
 * The thread starts on first use and is stopped by {@link #shutdown}, which
 * first lets queued writes finish; a later {@link #submit} starts a new one.
 */
public final class DittoWriteQueue {

    public static final String THREAD_NAME = "SARtak-Ditto-Writer";

    /** Told about a write that threw; called on the writer thread. */
    public interface FailureListener {
        void onWriteFailed(Throwable throwable);
    }

    private static final class PendingWrite {
        final String key;
        Runnable write;

        PendingWrite(String key, Runnable write) {
            this.key = key;
            this.write = write;
        }
    }

    private final Object lock = new Object();
    private final Map<String, PendingWrite> waitingByKey = new HashMap<>();
    private final FailureListener failureListener;
    private ExecutorService executor;
    private boolean finished;
    private volatile Thread writerThread;
    private int queued;
    private long completed;
    private long coalesced;
    private long failed;

    public DittoWriteQueue() {
        this(null);
    }

    public DittoWriteQueue(FailureListener failureListener) {
        this.failureListener = failureListener;
    }

    /**
     * Queues {@code write} to run on the writer thread.
     *
     * @param key identifies the document being written, or null. If a write
     *            with the same key is still waiting, it is replaced by this
     *            one and keeps its place in the queue.
     * @return false if nothing was queued: no write, or {@link #finish} was
     *         called
     */
    public boolean submit(String key, Runnable write) {
        if (write == null)
            return false;
        synchronized (lock) {
            if (finished)
                return false;
            if (key != null) {
                PendingWrite waiting = waitingByKey.get(key);
                if (waiting != null) {
                    waiting.write = write;
                    coalesced++;
                    return true;
                }
            }
            PendingWrite pending = new PendingWrite(key, write);
            if (key != null)
                waitingByKey.put(key, pending);
            queued++;
            executorLocked().execute(new WriteTask(pending));
            return true;
        }
    }

    private final class WriteTask implements Runnable {
        private final PendingWrite pending;

        WriteTask(PendingWrite pending) {
            this.pending = pending;
        }

        @Override
        public void run() {
            runPending(pending);
        }
    }

    /**
     * Waits until everything queued before this call has run.
     *
     * @return true if the queue drained within {@code timeoutMs}
     */
    public boolean drain(long timeoutMs) {
        if (isWriterThread())
            return true;
        final CountDownLatch latch = new CountDownLatch(1);
        synchronized (lock) {
            if (executor == null)
                return true;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                }
            });
        }
        try {
            return latch.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Lets queued writes finish for up to {@code drainTimeoutMs}, then stops
     * the writer thread and drops anything still waiting.
     *
     * @return the number of writes dropped because the drain timed out
     */
    public int shutdown(long drainTimeoutMs) {
        drain(drainTimeoutMs);
        synchronized (lock) {
            if (executor == null)
                return 0;
            List<Runnable> dropped = executor.shutdownNow();
            executor = null;
            writerThread = null;
            waitingByKey.clear();
            int droppedWrites = 0;
            for (Runnable runnable : dropped) {
                if (runnable instanceof WriteTask)
                    droppedWrites++;
            }
            queued = 0;
            return droppedWrites;
        }
    }

    /**
     * Lets everything already queued run, then lets the writer thread end,
     * without waiting for it. Later submits are ignored, so nothing can run
     * after a final task queued just before this (such as closing the store
     * the writes go to).
     */
    public void finish() {
        synchronized (lock) {
            finished = true;
            if (executor != null)
                executor.shutdown();
            executor = null;
        }
    }

    public boolean isWriterThread() {
        return Thread.currentThread() == writerThread;
    }

    public boolean isRunning() {
        synchronized (lock) {
            return executor != null;
        }
    }

    /** Writes queued but not finished, including one that is running. */
    public int getPendingCount() {
        synchronized (lock) {
            return queued;
        }
    }

    public String getSummary() {
        synchronized (lock) {
            return "writes pending=" + queued + " done=" + completed
                    + " merged=" + coalesced + " failed=" + failed;
        }
    }

    private void runPending(PendingWrite pending) {
        Runnable write;
        synchronized (lock) {
            if (pending.key != null && waitingByKey.get(pending.key) == pending)
                waitingByKey.remove(pending.key);
            write = pending.write;
        }
        Throwable failure = null;
        try {
            write.run();
        } catch (Throwable throwable) {
            failure = throwable;
        }
        synchronized (lock) {
            queued = Math.max(0, queued - 1);
            completed++;
            if (failure != null)
                failed++;
        }
        if (failure != null && failureListener != null) {
            try {
                failureListener.onWriteFailed(failure);
            } catch (Throwable ignored) {
                // A broken listener must not take the writer thread down.
            }
        }
    }

    private ExecutorService executorLocked() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, THREAD_NAME);
                    thread.setDaemon(true);
                    writerThread = thread;
                    return thread;
                }
            });
        }
        return executor;
    }
}
