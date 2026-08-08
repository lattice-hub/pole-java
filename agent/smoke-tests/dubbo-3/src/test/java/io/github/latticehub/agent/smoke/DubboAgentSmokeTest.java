package io.github.latticehub.agent.smoke;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.filter.support.ConsumerContextFilter;
import org.apache.dubbo.rpc.filter.ContextFilter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboAgentSmokeTest {
    @AfterEach
    void resetContext() {
        TrafficContext.reset();
    }

    @Test
    void agentPropagatesThroughRealDubboFilters() throws Exception {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("echo");
        invocation.setServiceName("io.github.latticehub.EchoService");
        invocation.setAttachment(TrafficContextBaggage.HEADER, "vendor=value");
        Invoker<Object> terminal = invoker(current -> AsyncRpcResult.newDefaultAsyncResult("ok", current));
        TrafficContext expected = TrafficContext.builder().lane("gray").bucket(23).build();

        try (TrafficContext.Scope ignored = TrafficContext.attach(expected)) {
            new ConsumerContextFilter(ApplicationModel.defaultModel()).invoke(terminal, invocation);
        }
        assertTrue(invocation.getAttachment(TrafficContextBaggage.HEADER).contains("vendor=value"));

        AtomicReference<TrafficContext> observed = new AtomicReference<>();
        Invoker<Object> provider = invoker(current -> {
            observed.set(TrafficContext.current().orElseThrow());
            return AsyncRpcResult.newDefaultAsyncResult("ok", current);
        });
        new ContextFilter(ApplicationModel.defaultModel()).invoke(provider, invocation);

        assertEquals(expected, observed.get());
        assertTrue(TrafficContext.current().isEmpty());
    }

    private static Invoker<Object> invoker(java.util.function.Function<Invocation, Result> action) {
        return new Invoker<>() {
            @Override public Class<Object> getInterface() { return Object.class; }
            @Override public Result invoke(Invocation invocation) { return action.apply(invocation); }
            @Override public URL getUrl() { return URL.valueOf("dubbo://127.0.0.1:20880/io.github.latticehub.EchoService"); }
            @Override public boolean isAvailable() { return true; }
            @Override public void destroy() { }
        };
    }
}
