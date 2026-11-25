package de.seezeit.aume.user;

import java.security.Principal;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import de.seezeit.aume.auth.AppUserService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    private final AppUserService users;
    private final MessageSource messages;

    public GreetingController(AppUserService users, MessageSource messages) {
        this.users = users;
        this.messages = messages;
    }

    @GetMapping(path = "/api/greeting", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('GREETING_READ')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> greeting(Principal principal, Locale locale) {
        var user = users.requireUser(principal.getName());
        String greeting = messages.getMessage("greeting.welcome",
            new Object[]{user.getName(), user.getResidence().getName()}, locale);
        return ResponseEntity.ok().headers(headers -> headers.setContentLanguage(locale)).body(greeting);
    }
}
