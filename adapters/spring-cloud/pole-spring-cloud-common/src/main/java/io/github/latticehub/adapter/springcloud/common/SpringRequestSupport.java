package io.github.latticehub.adapter.springcloud.common;

import io.github.latticehub.client.TargetService;
import io.github.latticehub.client.TargetServiceMetadata;
import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import org.springframework.http.HttpHeaders;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

final class SpringRequestSupport {
    private SpringRequestSupport() {
    }

    static URI sidecarUri(URI original, InetSocketAddress listener) {
        try {
            return new URI(
                    "http",
                    null,
                    listener.getHostString(),
                    listener.getPort(),
                    original.getRawPath(),
                    original.getRawQuery(),
                    original.getRawFragment());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("cannot route request through Sidecar HTTP listener", exception);
        }
    }

    static void applyHeaders(HttpHeaders headers, String namespace, String service) {
        Map<String, String> target = TargetServiceMetadata.encode(TargetService.builder()
                .namespace(namespace)
                .service(service)
                .build());
        headers.set(TargetServiceMetadata.NAMESPACE, target.get(TargetServiceMetadata.NAMESPACE));
        headers.set(TargetServiceMetadata.SERVICE, target.get(TargetServiceMetadata.SERVICE));
        List<String> existingBaggage = headers.getOrEmpty(TrafficContextBaggage.HEADER);
        TrafficContextBaggage.inject(existingBaggage, TrafficContext.current().orElse(null))
                .ifPresentOrElse(
                        value -> headers.set(TrafficContextBaggage.HEADER, value),
                        () -> headers.remove(TrafficContextBaggage.HEADER));
    }
}
