package com.transfervio;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessFilter extends OncePerRequestFilter {
  final AuthService auth;
  final RuntimeConfig config;
  final Map<String, Window> limits = new ConcurrentHashMap<>();

  record Window(long minute, int count) {}

  AccessFilter(AuthService auth, RuntimeConfig config) {
    this.auth = auth;
    this.config = config;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Referrer-Policy", "no-referrer");
    if (path.startsWith("/public/") || path.startsWith("/auth/")) {
      long minute = System.currentTimeMillis() / 60000;
      limits.entrySet().removeIf(e -> e.getValue().minute() < minute - 1);
      String key = req.getRemoteAddr() + (path.startsWith("/auth/") ? ":auth" : ":public");
      var window =
          limits.compute(
              key,
              (k, v) -> new Window(minute, v == null || v.minute() != minute ? 1 : v.count() + 1));
      if (window.count() > (path.startsWith("/auth/") ? 20 : 240)) {
        reject(res, 429, "Too many requests; retry in a minute");
        return;
      }
      String origin = req.getHeader("Origin");
      if (origin != null
          && !origin.equals(config.webUrl)
          && !origin.equals(config.webUrl.replace("localhost", "127.0.0.1"))) {
        reject(res, 403, "Origin not allowed");
        return;
      }
    }
    if (path.startsWith("/v1/")) {
      String header = req.getHeader("Authorization");
      try {
        if (header == null || !header.startsWith("Bearer ")) {
          reject(res, 401, "Partner authentication required");
          return;
        }
        var identity = auth.authenticate(header.substring(7));
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
      } catch (org.springframework.web.server.ResponseStatusException e) {
        reject(res, 401, "Invalid or expired credentials");
        return;
      }
    }
    chain.doFilter(req, res);
  }

  static void reject(HttpServletResponse res, int code, String message) throws IOException {
    res.setStatus(code);
    res.setContentType("application/json");
    res.getWriter().write("{\"message\":\"" + message + "\"}");
  }
}
