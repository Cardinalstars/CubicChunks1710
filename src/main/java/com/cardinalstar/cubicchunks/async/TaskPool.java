package com.cardinalstar.cubicchunks.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.google.common.util.concurrent.AbstractFuture;

@SuppressWarnings("unused")
public class TaskPool {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    public static final ThreadFactory THREAD_FACTORY = runnable -> {
        int number = THREAD_COUNTER.incrementAndGet();
        String name = String.format("CC BG Thread %d", number);
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    };

    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
        1,
        CubicChunksConfig.optimizations.backgroundThreads,
        60,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1024),
        THREAD_FACTORY,
        new DiscardPolicy());

    private static final ScheduledExecutorService SCHEDULED = Executors
        .newSingleThreadScheduledExecutor(THREAD_FACTORY);

    // static {
    // SCHEDULED.scheduleAtFixedRate(TaskPool::flushLastTask, 1, 1, TimeUnit.MILLISECONDS);
    // }

    // private static final Object lock = new Object();
    // private static Task<?> lastTask;

    // private static final long TASK_BATCHING_PERIOD = Duration.ofMillis(1).toNanos();

    public static <T> Future<T> submit(ITask<T> task) {
        return submit(task, null);
    }

    public static <T> Future<T> submit(ITask<T> task, @Nullable Consumer<T> callback) {
        long now = System.nanoTime();

        synchronized (lock) {
            // if (lastTask != null) {
            // Task<T> merged;
            //
            // if (lastTask.submitTime + TASK_BATCHING_PERIOD < now) {
            // POOL.submit(lastTask);
            // lastTask = null;
            // } else if ((merged = lastTask.tryMerge(task, now, callback)) != null) {
            // return merged;
            // }
            // }

            Task<T> future = new Task<>(task, now, callback);
            // lastTask = future;
            POOL.submit(future);
            return future;
        }
    }

    // private static void flushLastTask() {
    // synchronized (lock) {
    // if (lastTask != null) {
    // if (lastTask.submitTime + TASK_BATCHING_PERIOD < System.nanoTime()) {
    // POOL.submit(lastTask);
    // lastTask = null;
    // }
    // }
    // }
    // }

    public interface ITask<T> extends Callable<T> {

        /// Merges another task into this one to batch their work, if possible
        default boolean canMerge(ITask<?> other) {
            return false;
        }

        /// Executes all merged tasks
        default T callMerged(List<ITaskFuture<?>> mergedTasks) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    public interface ITaskFuture<T> {

        ITask<T> getTask();

        void finish(T value);

        void fail(Exception ex);
    }

    private static class Task<T> extends AbstractFuture<T> implements Runnable, ITaskFuture<T> {

        private final ITask<T> task;
        public final long submitTime;
        @Nullable
        private final Consumer<T> callback;

        private List<Task<?>> merged = null;

        public Task(ITask<T> task, long submitTime, @Nullable Consumer<T> callback) {
            this.task = task;
            this.submitTime = submitTime;
            this.callback = callback;
        }

        public synchronized <T2> Task<T2> tryMerge(ITask<T2> other, long now, @Nullable Consumer<T2> callback) {
            if (this.isDone()) return null;

            if (task.canMerge(other)) {
                if (merged == null) merged = new ArrayList<>(1);

                Task<T2> otherTask = new Task<>(other, now, callback);

                merged.add(otherTask);

                return otherTask;
            }

            return null;
        }

        @Override
        public synchronized void run() {
            try {
                if (merged != null) {
                    // noinspection unchecked
                    finish(task.callMerged((List<ITaskFuture<?>>) (List<?>) merged));
                } else {
                    finish(task.call());
                }
            } catch (Exception e) {
                CubicChunks.LOGGER.error("Could not run background task", e);
            }
        }

        @Override
        public ITask<T> getTask() {
            return task;
        }

        @Override
        public void finish(T value) {
            set(value);
            if (callback != null) callback.accept(value);
        }

        @Override
        public void fail(Exception ex) {
            setException(ex);
        }
    }
}
