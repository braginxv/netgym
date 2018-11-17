package com.techlook;

import org.jetbrains.annotations.NotNull;

public class ResultedCompletion<T> extends Completion {
    private Either<String, T> completionResult;

    public ResultedCompletion() {
        super();
    }

    public ResultedCompletion(int timeout) {
        super(timeout);
    }

    public void finish(@NotNull T result) {
        completionResult = Either.toRight(result);

        finish();
    }

    public void failure(@NotNull String message) {
        completionResult = Either.toLeft(message);

        finish();
    }

    public @NotNull Either<String, T> awaitResult() throws InterruptedException {
        super.await();

        return completionResult;
    }
}
