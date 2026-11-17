package de.seezeit.aume.residence;

import java.security.Principal;
import de.seezeit.aume.auth.AppUserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResidenceController {
    private final ResidenceRepository residences;
    private final AppUserService users;

    public ResidenceController(ResidenceRepository residences, AppUserService users) {
        this.residences = residences;
        this.users = users;
    }

    @GetMapping("/api/residences")
    @PreAuthorize("hasAuthority('RESIDENCE_READ')")
    @Transactional(readOnly = true)
    public PagedModel<ResidenceView> list(Principal principal, Pageable pageable) {
        var user = users.requireUser(principal.getName());
        return new PagedModel<>(residences.findById(user.getResidence().getId(), pageable)
            .map(residence -> new ResidenceView(residence.getId(), residence.getName())));
    }

    public record ResidenceView(Long id, String name) {}
}
