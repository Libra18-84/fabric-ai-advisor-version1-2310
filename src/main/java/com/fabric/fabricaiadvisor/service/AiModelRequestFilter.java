package com.fabric.fabricaiadvisor.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AiModelRequestFilter extends OncePerRequestFilter {

    private final AiModelRegistry registry;

    public AiModelRequestFilter(AiModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !Set.of(
                "/api/advise",
                "/api/ai/ask",
                "/api/intent/extract"
        ).contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        try (var scope = registry.openScope()) {
            response.setHeader(
                    "X-AI-Config-Revision",
                    scope.selection().revision()
            );

            chain.doFilter(request, response);
        }
    }
}