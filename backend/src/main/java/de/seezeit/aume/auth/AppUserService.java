package de.seezeit.aume.auth;

import java.util.List;
import java.util.stream.Stream;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class AppUserService {
    private final UserRepository users;

    public AppUserService(UserRepository users) {
        this.users = users;
    }

    public UserEntity requireUser(String key) {
        return users.findByUserKey(key).orElseThrow(() -> new BadCredentialsException("Unknown application user"));
    }

    public UserEntity requireIdentity(String issuer, String subject) {
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(subject)) {
            throw new BadCredentialsException("Missing identity issuer or subject");
        }
        return users.findByIdentityIssuerAndIdentitySubject(issuer, subject)
            .orElseThrow(() -> new BadCredentialsException("Identity has no application membership"));
    }

    public List<SimpleGrantedAuthority> authorities(UserEntity user) {
        return user.getRoles().stream().flatMap(role -> Stream.concat(
                Stream.of("ROLE_" + role.getName()),
                role.getPrivileges().stream().map(privilege -> privilege.getName())))
            .distinct().sorted().map(SimpleGrantedAuthority::new).toList();
    }

    public UserView view(String key) {
        var user = requireUser(key);
        return new UserView(user.getUserKey(), user.getName(),
            user.getRoles().stream().map(role -> role.getName()).sorted().toList(),
            user.getRoles().stream().flatMap(role -> role.getPrivileges().stream())
                .map(privilege -> privilege.getName()).distinct().sorted().toList());
    }

    public record UserView(String userKey, String name, List<String> roles, List<String> privileges) {}
}
