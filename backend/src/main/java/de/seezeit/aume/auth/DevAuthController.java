package de.seezeit.aume.auth;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@SecurityRequirements
@Profile("dev")
public class DevAuthController {
    private static final Set<String> MOCK_USERS = Set.of("student", "caretaker", "caretaker2", "clerk");
    private final AppUserService users;
    private final HttpSessionSecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;

    public DevAuthController(AppUserService users, HttpSessionSecurityContextRepository contexts,
            SessionAuthenticationStrategy sessions) {
        this.users = users;
        this.contexts = contexts;
        this.sessions = sessions;
    }

    @GetMapping("/api/auth/config")
    public Map<String, String> config() {
        return Map.of("mode", "dev");
    }

    @GetMapping("/api/auth/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/api/auth/dev-login")
    @Transactional(readOnly = true)
    public AppUserService.UserView login(@RequestBody LoginRequest login,
            HttpServletRequest request, HttpServletResponse response) {
        if (login.userKey() == null || !MOCK_USERS.contains(login.userKey())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown mock user");
        }
        var user = users.requireUser(login.userKey());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            user.getUserKey(), null, users.authorities(user));
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return users.view(user.getUserKey());
    }

    public record LoginRequest(String userKey) {}
    public record CsrfView(String headerName, String token) {}
}
