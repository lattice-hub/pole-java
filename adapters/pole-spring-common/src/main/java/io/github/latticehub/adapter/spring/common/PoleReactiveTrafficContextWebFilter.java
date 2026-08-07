package io.github.latticehub.adapter.spring.common;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import io.github.latticehub.client.TrafficContextException;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PoleReactiveTrafficContextWebFilter implements WebFilter {
    private static final String HOOK_KEY = PoleReactiveTrafficContextWebFilter.class.getName();
    private static final String CONTEXT_KEY = HOOK_KEY + ".context";
    private static final Object CLEARED = new Object();
    private static final Object ABSENT = new Object();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    public PoleReactiveTrafficContextWebFilter() {
        if (INSTALLED.compareAndSet(false, true)) {
            Hooks.onEachOperator(HOOK_KEY, Operators.lift((scannable, subscriber) ->
                    new TrafficContextSubscriber<>(subscriber)));
            Schedulers.onScheduleHook(HOOK_KEY, TrafficContext::wrap);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        TrafficContext context;
        try {
            context = TrafficContextBaggage
                    .extract(exchange.getRequest().getHeaders().getOrEmpty(TrafficContextBaggage.HEADER))
                    .orElse(null);
        } catch (TrafficContextException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        Object propagated = context == null ? CLEARED : context;
        return Mono.create(sink -> {
            Disposable subscription = withContext(context, () -> chain.filter(exchange)
                    .contextWrite(current -> current.put(CONTEXT_KEY, propagated))
                    .subscribe(ignored -> { }, sink::error, sink::success));
            sink.onCancel(subscription);
        });
    }

    private static <T> T withContext(TrafficContext context, java.util.concurrent.Callable<T> action) {
        try (TrafficContext.Scope ignored = context == null ? TrafficContext.clear() : TrafficContext.attach(context)) {
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("reactive TrafficContext callback failed", exception);
        }
    }

    private static final class TrafficContextSubscriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> delegate;
        private final Object propagated;

        private TrafficContextSubscriber(CoreSubscriber<? super T> delegate) {
            this.delegate = delegate;
            this.propagated = delegate.currentContext().getOrDefault(CONTEXT_KEY, ABSENT);
        }

        @Override
        public Context currentContext() {
            return delegate.currentContext();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            run(() -> delegate.onSubscribe(new Subscription() {
                @Override
                public void request(long count) {
                    run(() -> subscription.request(count));
                }

                @Override
                public void cancel() {
                    run(subscription::cancel);
                }
            }));
        }

        @Override
        public void onNext(T value) {
            run(() -> delegate.onNext(value));
        }

        @Override
        public void onError(Throwable throwable) {
            run(() -> delegate.onError(throwable));
        }

        @Override
        public void onComplete() {
            run(delegate::onComplete);
        }

        private void run(Runnable action) {
            if (propagated == ABSENT) {
                action.run();
                return;
            }
            TrafficContext context = propagated instanceof TrafficContext trafficContext ? trafficContext : null;
            try (TrafficContext.Scope ignored = context == null
                    ? TrafficContext.clear()
                    : TrafficContext.attach(context)) {
                action.run();
            }
        }
    }
}
