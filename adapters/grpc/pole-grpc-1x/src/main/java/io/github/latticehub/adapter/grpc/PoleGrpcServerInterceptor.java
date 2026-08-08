package io.github.latticehub.adapter.grpc;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import io.github.latticehub.client.TrafficContextException;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.ArrayList;
import java.util.List;

final class PoleGrpcServerInterceptor implements ServerInterceptor {
    static final PoleGrpcServerInterceptor INSTANCE = new PoleGrpcServerInterceptor();
    private static final Metadata.Key<String> BAGGAGE =
            Metadata.Key.of(TrafficContextBaggage.HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
            ServerCall<RequestT, ResponseT> call,
            Metadata headers,
            ServerCallHandler<RequestT, ResponseT> next) {
        TrafficContext context;
        try {
            context = TrafficContextBaggage.extract(baggage(headers)).orElse(null);
        } catch (TrafficContextException exception) {
            call.close(Status.INVALID_ARGUMENT.withDescription("invalid Pole traffic baggage"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        return withContext(context, () -> new ContextRestoringListener<>(next.startCall(call, headers), context));
    }

    private static List<String> baggage(Metadata headers) {
        List<String> values = new ArrayList<>();
        Iterable<String> headersValues = headers.getAll(BAGGAGE);
        if (headersValues != null) {
            headersValues.forEach(values::add);
        }
        return values;
    }

    private static void withContext(TrafficContext context, Runnable action) {
        withContext(context, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T withContext(TrafficContext context, java.util.concurrent.Callable<T> action) {
        try (TrafficContext.Scope ignored = context == null ? TrafficContext.clear() : TrafficContext.attach(context)) {
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("gRPC listener callback failed", exception);
        }
    }

    private static final class ContextRestoringListener<RequestT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<RequestT> {
        private final TrafficContext context;

        private ContextRestoringListener(ServerCall.Listener<RequestT> delegate, TrafficContext context) {
            super(delegate);
            this.context = context;
        }

        @Override
        public void onMessage(RequestT message) {
            withContext(context, () -> super.onMessage(message));
        }

        @Override
        public void onHalfClose() {
            withContext(context, super::onHalfClose);
        }

        @Override
        public void onCancel() {
            withContext(context, super::onCancel);
        }

        @Override
        public void onComplete() {
            withContext(context, super::onComplete);
        }

        @Override
        public void onReady() {
            withContext(context, super::onReady);
        }
    }
}
