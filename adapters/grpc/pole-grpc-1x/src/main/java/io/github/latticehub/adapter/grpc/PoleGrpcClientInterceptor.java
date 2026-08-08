package io.github.latticehub.adapter.grpc;

import io.github.latticehub.client.TrafficContextBaggage;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import java.util.ArrayList;
import java.util.List;

final class PoleGrpcClientInterceptor implements ClientInterceptor {
    static final PoleGrpcClientInterceptor INSTANCE = new PoleGrpcClientInterceptor();
    private static final Metadata.Key<String> BAGGAGE =
            Metadata.Key.of(TrafficContextBaggage.HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> interceptCall(
            MethodDescriptor<RequestT, ResponseT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<ResponseT> responseListener, Metadata headers) {
                List<String> current = new ArrayList<>();
                Iterable<String> values = headers.getAll(BAGGAGE);
                if (values != null) {
                    values.forEach(current::add);
                }
                headers.discardAll(BAGGAGE);
                TrafficContextBaggage.inject(current, null).ifPresent(value -> headers.put(BAGGAGE, value));
                super.start(responseListener, headers);
            }
        };
    }
}
