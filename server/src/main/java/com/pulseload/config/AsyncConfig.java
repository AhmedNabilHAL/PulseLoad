package com.pulseload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Enables Spring's proxy-based {@code @Async} support and defines the executor
 * that background run
 * launches run on. Each run is a long-lived, mostly-blocking task (it owns a
 * pacing loop and waits
 * on virtual users), so a virtual-thread-per-task executor fits far better than
 * a bounded pool of
 * platform threads: a blocked carrier costs almost nothing, and runs never
 * queue behind each other.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  public static final String RUN_LAUNCHER_EXECUTOR = "runLauncherExecutor";

  @Bean(RUN_LAUNCHER_EXECUTOR)
  public AsyncTaskExecutor runLauncherExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
