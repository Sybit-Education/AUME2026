package de.seezeit.aume.auth;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {
    private final AppUserService users;

    public CurrentUserController(AppUserService users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public AppUserService.UserView me(Principal principal) {
        return users.view(principal.getName());
    }
}
