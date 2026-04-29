package com.xcw.aiagentbackend.tools;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class RetryExecutor {

    private RetryExecutor() {
    }

    public static <T> T execute(Callable<T> callable) throws ExecutionException, RetryException {
        Retryer<T> retryer = RetryerBuilder.<T>newBuilder()
                .retryIfException()
                .retryIfResult(result -> result == null)
                .withWaitStrategy(WaitStrategies.fixedWait(500, TimeUnit.MILLISECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        return retryer.call(callable);
    }
}
