package io.github.latticehub.agent.smoke;

import com.sun.net.httpserver.HttpServer;
import io.github.latticehub.client.TrafficContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServlet;
import org.apache.thrift.transport.THttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThriftHttpAgentSmokeTest {
    private HttpServer httpServer;

    @AfterEach
    void close() {
        if (httpServer != null) httpServer.stop(0);
        TrafficContext.reset();
    }

    @Test
    void agentInjectsRealTHttpClientRequest() throws Exception {
        AtomicReference<String> baggage = new AtomicReference<>();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/thrift", exchange -> {
            baggage.set(exchange.getRequestHeaders().getFirst("baggage"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        httpServer.start();
        THttpClient client = new THttpClient(
                "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/thrift");
        client.write("request".getBytes(StandardCharsets.UTF_8));
        try (TrafficContext.Scope ignored = TrafficContext.attach(TrafficContext.builder().lane("gray").build())) {
            client.flush();
        }
        assertTrue(baggage.get().contains("latticehub.traffic.lane=gray"));
    }

    @Test
    void agentRestoresContextAroundRealTServletProcessor() throws Exception {
        TrafficContext expected = TrafficContext.builder().campaign("canary").build();
        String baggage = "latticehub.traffic.version=1,latticehub.traffic.campaign=canary";
        AtomicReference<TrafficContext> observed = new AtomicReference<>();
        TProcessor processor = (input, output) -> observed.set(TrafficContext.current().orElseThrow());
        ExposedServlet servlet = new ExposedServlet(processor);
        HttpServletRequest request = request(baggage);
        HttpServletResponse response = response();

        servlet.post(request, response);

        assertEquals(expected, observed.get());
        assertTrue(TrafficContext.current().isEmpty());
    }

    private static HttpServletRequest request(String baggage) {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                ThriftHttpAgentSmokeTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getHeaders" -> Collections.enumeration(java.util.List.of(baggage));
                    case "getInputStream" -> new Input(new byte[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static HttpServletResponse response() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return (HttpServletResponse) java.lang.reflect.Proxy.newProxyInstance(
                ThriftHttpAgentSmokeTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, arguments) -> "getOutputStream".equals(method.getName())
                        ? new Output(output)
                        : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private static final class ExposedServlet extends TServlet {
        private ExposedServlet(TProcessor processor) {
            super(processor, new TBinaryProtocol.Factory());
        }
        private void post(HttpServletRequest request, HttpServletResponse response) throws Exception {
            super.doPost(request, response);
        }
    }

    private static final class Input extends ServletInputStream {
        private final ByteArrayInputStream input;
        private Input(byte[] bytes) { input = new ByteArrayInputStream(bytes); }
        @Override public int read() { return input.read(); }
        @Override public boolean isFinished() { return input.available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener listener) { }
    }

    private static final class Output extends ServletOutputStream {
        private final ByteArrayOutputStream output;
        private Output(ByteArrayOutputStream output) { this.output = output; }
        @Override public void write(int value) { output.write(value); }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener listener) { }
    }
}
