/*
 * The MIT License
 *
 * Copyright (c) 2022 Vladimir Bragin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.techlook.net.client;

/**
 * Container including the result of an asynchronous action to either a wrong or a successful value
 * @param <L> A Type of a wrong result
 * @param <R> A Type of a right result
 */
public abstract class Either<L, R> {
    /**
     * @return right value
     */
    public abstract BoxedResult<R> right();

    /**
     * @return wrong value
     */
    public abstract BoxedResult<L> left();

    /**
     * Interface for result aggregation
     * @param <T> type of result
     */
    public interface BoxedResult<T> {
        /**
         * pass the result to a consumer asynchronously when it will appear
         * @param consumer result consumer
         */
        void apply(Consumer<? super T> consumer);

        /**
         * wait the result until it appears
         * @return result
         * @throws NoResultException if no result
         */
        T get() throws NoResultException;
    }

    /**
     * create Either with a right value
     * @param result resulted value
     * @param <L> wrong value type
     * @param <R> right value type
     * @return new Either with a right value
     */
    public static <L, R> Either<L, R> right(R result)  {
        return new Right<>(result);
    }

    /**
     * create Either with a wrong value
     * @param leftResult wrong value
     * @param <L> wrong value type
     * @param <R> right result type
     * @return new Either with a wrong value
     */
    public static <L, R> Either<L, R> left(L leftResult)  {
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
        public BoxedResult<R> right() {
            return Empty.instance;
        }

        @Override
        public BoxedResult<L> left() {
            return container;
        }
    }

    private static class Empty implements BoxedResult<Object> {
        static final BoxedResult instance = new Empty();

        @Override
        public void apply(Consumer<? super Object> consumer) {
        }

        @Override
        public Object get() throws NoResultException {
            throw new NoResultException("No result");
        }
    }

    private static class ValueContainer<T> implements BoxedResult<T> {
        private final T value;

        ValueContainer(T value) {
            this.value = value;
        }

        @Override
        public void apply(Consumer<? super T> consumer) {
            consumer.consume(value);
        }

        @Override
        public T get() throws NoResultException {
            return value;
        }
    }

    public static class NoResultException extends Throwable {
        private NoResultException(String message) {
            super(message);
        }
    }
}
