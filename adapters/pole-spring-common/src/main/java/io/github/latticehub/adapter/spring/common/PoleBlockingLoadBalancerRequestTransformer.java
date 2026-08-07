package io.github.latticehub.adapter.spring.common;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.net.URI;
import java.util.Objects;

public final class PoleBlockingLoadBalancerRequestTransformer implements LoadBalancerRequestTransformer {
    private final SidecarHttpListenerProvider listenerProvider;
    private final String namespace;

    public PoleBlockingLoadBalancerRequestTransformer(
            SidecarHttpListenerProvider listenerProvider,
            String namespace) {
        this.listenerProvider = Objects.requireNonNull(listenerProvider, "listenerProvider must not be null");
        this.namespace = requireNamespace(namespace);
    }

    @Override
    public HttpRequest transformRequest(HttpRequest request, ServiceInstance instance) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        URI sidecarUri = SpringRequestSupport.sidecarUri(request.getURI(), listenerProvider.listenerAddress());
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(request.getHeaders());
        SpringRequestSupport.applyHeaders(headers, namespace, instance.getServiceId());
        return new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return sidecarUri;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    private static String requireNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return namespace;
    }
}
