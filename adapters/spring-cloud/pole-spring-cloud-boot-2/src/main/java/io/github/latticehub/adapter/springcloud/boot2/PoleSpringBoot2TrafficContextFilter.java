package io.github.latticehub.adapter.springcloud.boot2;

import io.github.latticehub.client.TrafficContext;
import io.github.latticehub.client.TrafficContextBaggage;
import io.github.latticehub.client.TrafficContextException;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

public final class PoleSpringBoot2TrafficContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
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
