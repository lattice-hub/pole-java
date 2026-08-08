package io.github.latticehub.adapter.dubbo.v3;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import org.apache.dubbo.rpc.Invocation;

import java.util.List;

public final class DubboTrafficContextAdapter {
    private DubboTrafficContextAdapter() {
    }

    public static void inject(Object candidate) {
        Invocation invocation = requireInvocation(candidate);
        String existing = invocation.getAttachment(TrafficContextBaggage.HEADER);
        List<String> baggage = existing == null ? List.of() : List.of(existing);
        TrafficContextBaggage.inject(baggage, TrafficContext.current().orElse(null))
                .ifPresentOrElse(
                        value -> invocation.setAttachment(TrafficContextBaggage.HEADER, value),
                        () -> invocation.getAttachments().remove(TrafficContextBaggage.HEADER));
    }

    public static AutoCloseable enterProvider(Object candidate) {
        Invocation invocation = requireInvocation(candidate);
        String baggage = invocation.getAttachment(TrafficContextBaggage.HEADER);
        return TrafficContextBaggage.extract(baggage == null ? List.of() : List.of(baggage))
                .<AutoCloseable>map(TrafficContext::attach)
                .orElseGet(TrafficContext::clear);
    }

    private static Invocation requireInvocation(Object candidate) {
        if (!(candidate instanceof Invocation invocation)) {
            throw new IllegalArgumentException("candidate must be a Dubbo Invocation");
        }
        return invocation;
    }
}
