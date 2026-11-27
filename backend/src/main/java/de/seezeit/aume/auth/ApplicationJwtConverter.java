package de.seezeit.aume.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApplicationJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AppUserService users;

    public ApplicationJwtConverter(AppUserService users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var user = users.requireIdentity(jwt.getIssuer().toString(), jwt.getSubject());
        return new JwtAuthenticationToken(jwt, users.authorities(user), user.getUserKey());
    }
}
