/*
 * The MIT License
 *
 * Copyright (c) 2021 Vladimir Bragin
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

package org.techlook;

/**
 * Either combines asynchronous result with possibles proper (right) or not like that (wrong, left) values
 * @param <L> Type of wrong result
 * @param <R> Type of proper result
 */
public abstract class Either<L, R> {
    /**
     * @return aggregated proper result
     */
    public abstract BoxedResult<R> right();

    /**
     * @return aggregated wrong result
     */
    public abstract BoxedResult<L> left();

    /**
     * Common interface to aggregate result
     * @param <T> type of result
     */
    public interface BoxedResult<T> {
        /**
         * pass aggregated result to consumer asynchronously when it will be formed
         * @param consumer an object that can obtain aggregated result
         */
        void apply(Consumer<? super T> consumer);

        /**
         * synchronously wait result blocking the thread until result will be formed
         * @return result
         * @throws NoResultException if no result
         */
        T get() throws NoResultException;
    }

    /**
     * create Either with the proper result
     * @param result proper result
     * @param <L> left result
     * @param <R> right result
     * @return new Either with proper result
     */
    public static <L, R> Either<L, R> right(R result)  {
        return new Right<>(result);
    }

    /**
     * create Either with the wrong result
     * @param leftResult wrong result
     * @param <L> left result
     * @param <R> right result
     * @return new Either with wrong result
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
