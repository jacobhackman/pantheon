/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.manager.task;

import static tech.pegasys.pantheon.util.FutureUtils.completedExceptionally;

import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.google.common.base.Stopwatch;

public abstract class AbstractEthTask<T> implements EthTask<T> {

  private double taskTimeInSec = -1.0D;
  private final OperationTimer taskTimer;
  protected final AtomicReference<CompletableFuture<T>> result = new AtomicReference<>();
  private final Collection<CompletableFuture<?>> subTaskFutures = new ConcurrentLinkedDeque<>();

  protected AbstractEthTask(final MetricsSystem metricsSystem) {
    this(buildOperationTimer(metricsSystem));
  }

  protected AbstractEthTask(final OperationTimer taskTimer) {
    this.taskTimer = taskTimer;
  }

  private static OperationTimer buildOperationTimer(final MetricsSystem metricsSystem) {
    final LabelledMetric<OperationTimer> ethTasksTimer =
        metricsSystem.createLabelledTimer(
            MetricCategory.SYNCHRONIZER, "task", "Internal processing tasks", "taskName");
    if (ethTasksTimer == NoOpMetricsSystem.NO_OP_LABELLED_1_OPERATION_TIMER) {
      return () ->
          new OperationTimer.TimingContext() {
            final Stopwatch stopwatch = Stopwatch.createStarted();

            @Override
            public double stopTimer() {
              return stopwatch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
            }
          };
    } else {
      return ethTasksTimer.labels(AbstractEthTask.class.getSimpleName());
    }
  }

  @Override
  public final CompletableFuture<T> run() {
    if (result.compareAndSet(null, new CompletableFuture<>())) {
      executeTaskTimed();
      result.get().whenComplete((r, t) -> cleanup());
    }
    return result.get();
  }

  @Override
  public final CompletableFuture<T> runAsync(final ExecutorService executor) {
    if (result.compareAndSet(null, new CompletableFuture<>())) {
      executor.submit(this::executeTaskTimed);
      result.get().whenComplete((r, t) -> cleanup());
    }
    return result.get();
  }

  @Override
  public final void cancel() {
    synchronized (result) {
      result.compareAndSet(null, new CompletableFuture<>());
      result.get().cancel(false);
    }
  }

  public final boolean isDone() {
    return result.get() != null && result.get().isDone();
  }

  public final boolean isSucceeded() {
    return isDone() && !result.get().isCompletedExceptionally();
  }

  public final boolean isFailed() {
    return isDone() && result.get().isCompletedExceptionally();
  }

  public final boolean isCancelled() {
    return isDone() && result.get().isCancelled();
  }

  /**
   * Utility for executing completable futures that handles cleanup if this EthTask is cancelled.
   *
   * @param subTask a subTask to execute
   * @param <S> the type of data returned from the CompletableFuture
   * @return The completableFuture that was executed
   */
  protected final <S> CompletableFuture<S> executeSubTask(
      final Supplier<CompletableFuture<S>> subTask) {
    synchronized (result) {
      if (!isCancelled()) {
        final CompletableFuture<S> subTaskFuture = subTask.get();
        subTaskFutures.add(subTaskFuture);
        subTaskFuture.whenComplete((r, t) -> subTaskFutures.remove(subTaskFuture));
        return subTaskFuture;
      } else {
        return completedExceptionally(new CancellationException());
      }
    }
  }

  /**
   * Utility for registering completable futures for cleanup if this EthTask is cancelled.
   *
   * @param <S> the type of data returned from the CompletableFuture
   * @param subTaskFuture the future to be registered.
   */
  protected final <S> void registerSubTask(final CompletableFuture<S> subTaskFuture) {
    synchronized (result) {
      if (!isCancelled()) {
        subTaskFutures.add(subTaskFuture);
        subTaskFuture.whenComplete((r, t) -> subTaskFutures.remove(subTaskFuture));
      }
    }
  }

  /**
   * Helper method for sending subTask to worker that will clean up if this EthTask is cancelled.
   *
   * @param scheduler the scheduler that will run worker task
   * @param subTask a subTask to execute
   * @param <S> the type of data returned from the CompletableFuture
   * @return The completableFuture that was executed
   */
  protected final <S> CompletableFuture<S> executeWorkerSubTask(
      final EthScheduler scheduler, final Supplier<CompletableFuture<S>> subTask) {
    return executeSubTask(() -> scheduler.scheduleSyncWorkerTask(subTask));
  }

  public final T result() {
    if (!isSucceeded()) {
      return null;
    }
    try {
      return result.get().get();
    } catch (final InterruptedException | ExecutionException e) {
      return null;
    }
  }

  /** Execute core task logic. */
  protected abstract void executeTask();

  /** Executes the task while timed by a timer. */
  public void executeTaskTimed() {
    final OperationTimer.TimingContext timingContext = taskTimer.startTimer();
    try {
      executeTask();
    } finally {
      taskTimeInSec = timingContext.stopTimer();
    }
  }

  public double getTaskTimeInSec() {
    return taskTimeInSec;
  }

  /** Cleanup any resources when task completes. */
  protected void cleanup() {
    for (final CompletableFuture<?> subTaskFuture : subTaskFutures) {
      subTaskFuture.cancel(false);
    }
  }
}
