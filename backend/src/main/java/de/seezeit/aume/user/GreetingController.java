package de.seezeit.aume.user;

import java.security.Principal;
import de.seezeit.aume.auth.AppUserService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    private final AppUserService users;

    public GreetingController(AppUserService users) {
        this.users = users;
    }

    @GetMapping(path = "/api/greeting", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('GREETING_READ')")
    @Transactional(readOnly = true)
    public String greeting(Principal principal) {
        var user = users.requireUser(principal.getName());
        return "Hallo " + user.getName() + ", willkommen in " + user.getResidence().getName() + ".";
    }
}
