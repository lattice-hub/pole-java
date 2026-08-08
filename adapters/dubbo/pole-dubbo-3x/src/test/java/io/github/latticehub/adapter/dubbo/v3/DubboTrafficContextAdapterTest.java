package io.github.latticehub.adapter.dubbo.v3;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboTrafficContextAdapterTest {
    @AfterEach
    void resetContext() {
        TrafficContext.reset();
    }

    @Test
    void injectsAndRestoresTrafficContext() throws Exception {
        RpcInvocation invocation = new RpcInvocation();
        invocation.setAttachment(TrafficContextBaggage.HEADER, "vendor=value");
        TrafficContext expected = TrafficContext.builder().lane("gray").bucket(17).build();

        try (TrafficContext.Scope ignored = TrafficContext.attach(expected)) {
            DubboTrafficContextAdapter.inject(invocation);
        }

        assertTrue(invocation.getAttachment(TrafficContextBaggage.HEADER).contains("vendor=value"));
        try (AutoCloseable ignored = DubboTrafficContextAdapter.enterProvider(invocation)) {
            assertEquals(expected, TrafficContext.current().orElseThrow());
        }
        assertTrue(TrafficContext.current().isEmpty());
    }
}
