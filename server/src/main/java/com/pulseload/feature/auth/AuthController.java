package com.pulseload.feature.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Supports the SPA's session-cookie login. {@code GET /auth/me} both reports
 * who is logged in and,
 * by resolving the {@link CsrfToken}, triggers the XSRF-TOKEN cookie to be
 * written so the SPA can
 * echo it back on mutating requests. {@code POST /auth/logout} ends the
 * session.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  @GetMapping("/me")
  public Map<String, Object> me(Authentication authentication, CsrfToken csrfToken) {
    // Reading the token here is what causes CookieCsrfTokenRepository to emit the
    // cookie.
    csrfToken.getToken();
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
    return Map.of("username", authentication.getName(), "roles", roles);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }
}
