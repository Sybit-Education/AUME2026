package de.seezeit.aume;

import java.time.Duration;
import java.time.Instant;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.InvitationEntity;
import de.seezeit.aume.residence.InvitationRepository;
import de.seezeit.aume.residence.MembershipService;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Single-use test codes for the {@code dev} profile. */
@Component
@Profile("dev")
@Order(3)
public class MembershipSampleData implements CommandLineRunner {
    private final InvitationRepository invitations;
    private final ResidenceRepository residences;
    private final FlatRepository flats;
    private final UserRepository users;

    public MembershipSampleData(InvitationRepository invitations, ResidenceRepository residences,
                                FlatRepository flats, UserRepository users) {
        this.invitations = invitations;
        this.residences = residences;
        this.flats = flats;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        invite("WG-SONNE-1", "S-01");
        invite("WG-SONNE-2", "S-01");
        invite("WG-SONNE-3", "S-02");
    }

    private void invite(String code, String label) {
        var hash = MembershipService.hash(code);
        if (invitations.existsByTokenHash(hash)) return;
        var residence = residences.findFirstByName("Wohnanlage Sonne").orElseThrow();
        var flat = flats.findByResidenceIdAndLabel(residence.getId(), label).orElseThrow();
        var clerk = users.findByUserKey("clerk").orElseThrow();
        invitations.save(new InvitationEntity(flat, hash, Instant.now().plus(Duration.ofDays(30)), clerk));
    }
}
