package io.github.latticehub.adapter.springcloud.boot3;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import io.github.latticehub.client.TrafficContextException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public final class PoleSpringBoot3TrafficContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TrafficContext context;
        try {
            context = TrafficContextBaggage
                    .extract(Collections.list(request.getHeaders(TrafficContextBaggage.HEADER)))
                    .orElse(null);
        } catch (TrafficContextException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            return;
        }
        try (TrafficContext.Scope ignored = context == null
                ? TrafficContext.clear()
                : TrafficContext.attach(context)) {
            filterChain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
