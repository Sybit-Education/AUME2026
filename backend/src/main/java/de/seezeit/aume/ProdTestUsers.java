package de.seezeit.aume;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.user.CaretakerEntity;
import de.seezeit.aume.user.ClerkEntity;
import de.seezeit.aume.user.RoleRepository;
import de.seezeit.aume.user.StudentEntity;
import de.seezeit.aume.user.UserEntity;
import de.seezeit.aume.user.UserRepository;

/** The four accounts that can sign in through Keycloak. No sample residences or inspections. */
@Component
@Profile("prod")
@Order(1)
public class ProdTestUsers implements CommandLineRunner {
    private static final Map<String, String> SUBJECTS = Map.of(
        "student", "00000000-0000-4000-8000-000000000001",
        "clerk", "00000000-0000-4000-8000-000000000002",
        "caretaker", "00000000-0000-4000-8000-000000000003",
        "caretaker2", "00000000-0000-4000-8000-000000000004");

    private final ResidenceRepository residences;
    private final UserRepository users;
    private final RoleRepository roles;
    private final String issuer;

    public ProdTestUsers(ResidenceRepository residences, UserRepository users, RoleRepository roles,
            @Value("${KEYCLOAK_ISSUER_URI}") String issuer) {
        this.residences = residences;
        this.users = users;
        this.roles = roles;
        this.issuer = issuer;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var residence = residences.findFirstByName("Seezeit")
            .orElseGet(() -> residences.save(new ResidenceEntity("Seezeit")));
        user("student", "Studierende", "STUDENT", residence);
        user("clerk", "Sachbearbeitung", "CLERK", residence);
        user("caretaker", "Objektbetreuung", "CARETAKER", residence);
        user("caretaker2", "Objektbetreuung Petershausen", "CARETAKER", residence);
    }

    private void user(String key, String name, String role, ResidenceEntity residence) {
        var user = users.findByUserKey(key).orElseGet(() -> users.save(create(key, name, role, residence)));
        if (user.getIdentitySubject() == null) user.linkIdentity(issuer, SUBJECTS.get(key));
    }

    private UserEntity create(String key, String name, String role, ResidenceEntity residence) {
        var assigned = Set.of(roles.findByName(role).orElseThrow());
        return switch (role) {
            case "STUDENT" -> new StudentEntity(key, name, assigned, residence);
            case "CARETAKER" -> new CaretakerEntity(key, name, assigned, residence);
            case "CLERK" -> new ClerkEntity(key, name, assigned, residence);
            default -> throw new IllegalArgumentException("Unknown test user role: " + role);
        };
    }
}
