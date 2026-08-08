package io.github.latticehub.adapter.springcloud.common;

import io.github.latticehub.client.TargetServiceMetadata;
import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringRequestTransformerTest {
    private final SidecarHttpListenerProvider listener = () -> new InetSocketAddress("127.0.0.1", 15001);
    private final DefaultServiceInstance instance =
            new DefaultServiceInstance("orders-1", "orders", "10.0.0.9", 8080, false);

    @Test
    void blockingTransformerRoutesThroughSidecarAndInjectsMetadata() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TrafficContextBaggage.HEADER, "vendor=value");
        HttpRequest request = request(URI.create("http://orders/api/items?q=1"), headers);
        TrafficContext context = TrafficContext.builder().lane("gray").build();

        try (TrafficContext.Scope ignored = TrafficContext.attach(context)) {
            HttpRequest transformed = new PoleBlockingLoadBalancerRequestTransformer(listener, "commerce")
                    .transformRequest(request, instance);

            assertEquals("http://127.0.0.1:15001/api/items?q=1", transformed.getURI().toString());
            assertEquals("commerce", transformed.getHeaders().getFirst(TargetServiceMetadata.NAMESPACE));
            assertEquals("orders", transformed.getHeaders().getFirst(TargetServiceMetadata.SERVICE));
            assertTrue(transformed.getHeaders().getFirst(TrafficContextBaggage.HEADER).contains("vendor=value"));
            assertTrue(transformed.getHeaders().getFirst(TrafficContextBaggage.HEADER)
                    .contains("latticehub.traffic.lane=gray"));
        }
    }

    @Test
    void reactiveTransformerRoutesThroughSidecar() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://orders/api/items"))
                .build();

        ClientRequest transformed = new PoleReactiveLoadBalancerRequestTransformer(listener, "default")
                .transformRequest(request, instance);

        assertEquals("http://127.0.0.1:15001/api/items", transformed.url().toString());
        assertEquals("default", transformed.headers().getFirst(TargetServiceMetadata.NAMESPACE));
        assertEquals("orders", transformed.headers().getFirst(TargetServiceMetadata.SERVICE));
    }

    @Test
    void reactiveFilterRestoresContextAcrossThreadSwitches() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(TrafficContextBaggage.HEADER,
                        "latticehub.traffic.version=1,latticehub.traffic.lane=gray")
                .build());
        PoleReactiveTrafficContextWebFilter filter = new PoleReactiveTrafficContextWebFilter();

        AtomicReference<String> observed = new AtomicReference<>();
        filter.filter(exchange, ignored -> Mono.fromRunnable(() -> observed.set(
                        TrafficContext.current().flatMap(TrafficContext::getLane).orElse("missing")))
                .subscribeOn(Schedulers.boundedElastic())
                .then()).block();

        assertEquals("gray", observed.get());
        assertTrue(TrafficContext.current().isEmpty());
    }

    private static HttpRequest request(URI uri, HttpHeaders headers) {
        return new HttpRequest() {
            @Override
            public HttpMethod getMethod() {
                return HttpMethod.GET;
            }

            @Override
            public String getMethodValue() {
                return "GET";
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
