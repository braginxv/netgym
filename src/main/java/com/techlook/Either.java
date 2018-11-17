package com.techlook;

import org.jetbrains.annotations.NotNull;

public abstract class Either<L, R> {
    public abstract BoxedResult<R> right();

    public abstract BoxedResult<L> left();

    public interface BoxedResult<T> {
        void apply(Consumer<T> consumer);

        T get() throws NoResultException;
    }

    public static <L, R> Either<L, R> toRight(R result)  {
        return new Right<>(result);
    }

    public static <L, R> Either<L, R> toLeft(L leftResult)  {
        return new Left<>(leftResult);
    }

    private static class Right<L, R> extends Either<L, R> {
        private final ValueContainer<R> container;

        private Right(R result) {
            container = new ValueContainer<>(result);
        }

        @Override
        public BoxedResult<R> right() {
            return container;
        }

        @Override
        public BoxedResult<L> left() {
            return Empty.instance;
        }
    }

    private static class Left<L, R> extends Either<L, R> {
        private final ValueContainer<L> container;

        private Left(L leftResult) {
            container = new ValueContainer<>(leftResult);
        }

        @Override
        public BoxedResult right() {
            return Empty.instance;
        }

        @Override
        public BoxedResult left() {
            return container;
        }
    }

    private static class Empty implements BoxedResult<Object> {
        static final BoxedResult instance = new Empty();

        @Override
        public void apply(Consumer<Object> consumer) {
        }

        @Override
        public Object get() throws NoResultException {
            throw new NoResultException("No result emitted");
        }
    }

    private static class ValueContainer<T> implements BoxedResult<T> {
        private final T value;

        ValueContainer(@NotNull T value) {
            this.value = value;
        }

        @Override
        public void apply(Consumer<T> consumer) {
            consumer.consume(value);
        }

        @Override
        public T get() throws NoResultException {
            return value;
        }
    }

    private static class NoResultException extends Throwable {
        private NoResultException(String message) {
            super(message);
        }
    }
}
