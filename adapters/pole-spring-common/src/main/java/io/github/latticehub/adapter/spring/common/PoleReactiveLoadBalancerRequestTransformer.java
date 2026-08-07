package io.github.latticehub.adapter.spring.common;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerClientRequestTransformer;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.net.URI;
import java.util.Objects;

public final class PoleReactiveLoadBalancerRequestTransformer implements LoadBalancerClientRequestTransformer {
    private final SidecarHttpListenerProvider listenerProvider;
    private final String namespace;

    public PoleReactiveLoadBalancerRequestTransformer(
            SidecarHttpListenerProvider listenerProvider,
            String namespace) {
        this.listenerProvider = Objects.requireNonNull(listenerProvider, "listenerProvider must not be null");
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        this.namespace = namespace;
    }

    @Override
    public ClientRequest transformRequest(ClientRequest request, ServiceInstance instance) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        URI sidecarUri = SpringRequestSupport.sidecarUri(request.url(), listenerProvider.listenerAddress());
        return ClientRequest.from(request)
                .url(sidecarUri)
                .headers(headers -> SpringRequestSupport.applyHeaders(
                        headers,
                        namespace,
                        instance.getServiceId()))
                .build();
    }
}
