package com.aiantfarm.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component("mdcRequestFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcRequestFilter extends OncePerRequestFilter {

    public static final String REQ_ID = "requestId";
    public static final String USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // 1) Request ID from common headers or generate
        String requestId = firstNonBlank(
                req.getHeader("X-Request-Id"),
                req.getHeader("X-Correlation-Id"),
                req.getHeader("X-Amzn-Trace-Id")
        );
        if (isBlank(requestId)) requestId = UUID.randomUUID().toString();
        MDC.put(REQ_ID, requestId);

        // Echo back so clients can correlate
        res.setHeader("X-Request-Id", requestId);

        // 2) User id if authenticated
        String userId = currentUserId();
        if (!isBlank(userId)) MDC.put(USER_ID, userId);

        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(REQ_ID);
            MDC.remove(USER_ID);
        }
    }

    private static String currentUserId() {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.isAuthenticated() && a.getName() != null && !"anonymousUser".equals(a.getName())) {
                return a.getName(); // or pull from principal/claims if you prefer
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return null;
    }
}
