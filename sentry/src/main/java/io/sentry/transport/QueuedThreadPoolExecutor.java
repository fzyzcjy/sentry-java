package io.sentry.transport;

import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a thread pool executor enriched for the possibility of queueing (with max queue size) the
 * supplied tasks.
 *
 * <p>The {@link Runnable} instances.
 *
 * <p>This class is not public because it is used solely in {@link AsyncHttpTransport}.
 */
final class QueuedThreadPoolExecutor extends ThreadPoolExecutor {
  private final int maxQueueSize;
  private final @NotNull ILogger logger;
  private final @NotNull ReusableCountLatch unfinishedTasksCount = new ReusableCountLatch();

  /**
   * Creates a new instance of the thread pool.
   *
   * @param corePoolSize the minimum number of threads started
   * @param threadFactory the thread factory to construct new threads
   * @param rejectedExecutionHandler specifies what to do with the tasks that cannot be run (e.g.
   *     during the shutdown)
   */
  public QueuedThreadPoolExecutor(
      final int corePoolSize,
      final int maxQueueSize,
      final @NotNull ThreadFactory threadFactory,
      final @NotNull RejectedExecutionHandler rejectedExecutionHandler,
      final @NotNull ILogger logger) {
    // similar to Executors.newSingleThreadExecutor, but with a max queue size control
    super(
        corePoolSize,
        corePoolSize,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        threadFactory,
        rejectedExecutionHandler);
    this.maxQueueSize = maxQueueSize;
    this.logger = logger;
  }

  @Override
  public Future<?> submit(final @NotNull Runnable task) {
    if (isSchedulingAllowed()) {
      unfinishedTasksCount.increment();
      return super.submit(task);
    } else {
      // if the thread pool is full, we don't cache it
      logger.log(SentryLevel.WARNING, "Submit cancelled");
      return new CancelledFuture<>();
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  protected void afterExecute(final @NotNull Runnable r, final @Nullable Throwable t) {
    try {
      super.afterExecute(r, t);
    } finally {
      unfinishedTasksCount.decrement();
    }
  }

  /** Blocks the thread until there are no running tasks. */
  void waitTillIdle(final long timeoutMillis) {
    try {
      unfinishedTasksCount.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      logger.log(SentryLevel.ERROR, "Failed to wait till idle", e);
      Thread.currentThread().interrupt();
    }
  }

  private boolean isSchedulingAllowed() {
    return unfinishedTasksCount.getCount() < maxQueueSize;
  }

  static final class CancelledFuture<T> implements Future<T> {
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      return true;
    }

    @Override
    public boolean isCancelled() {
      return true;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() {
      throw new CancellationException();
    }

    @Override
    public T get(final long timeout, final @NotNull TimeUnit unit) {
      throw new CancellationException();
    }
  }
}
