package org.strata.jverify.verifier.compiler.simplifications;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

/// A very simple implementation of Flow.Publisher<T>
/// that directly calls onNext() synchronously on each subscriber,
/// and never emits any other events.
///
/// This is necessary because the built-in SubmissionPublisher
/// does not seem to support this mode.
public class SimpleSynchronousPublisher<T> implements Flow.Publisher<T> {

    private final List<Flow.Subscriber<? super T>> subscribers = new ArrayList<>();

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscribers.add(subscriber);
    }

    public void submit(T t) {
        for (Flow.Subscriber<? super T> subscriber : subscribers) {
            subscriber.onNext(t);
        }
    }
}
