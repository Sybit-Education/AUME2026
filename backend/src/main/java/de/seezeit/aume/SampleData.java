package de.seezeit.aume;

import java.util.Set;
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

@Component
@Profile("dev")
@Order(1)
public class SampleData implements CommandLineRunner {
    private final ResidenceRepository residences;
    private final UserRepository users;
    private final RoleRepository roles;

    public SampleData(ResidenceRepository residences, UserRepository users, RoleRepository roles) {
        this.residences = residences;
        this.users = users;
        this.roles = roles;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var residence = residences.findFirstByName("Wohnanlage Sonne")
            .orElseGet(() -> residences.save(new ResidenceEntity("Wohnanlage Sonne")));
        user("student", "Studierende", "STUDENT", residence);
        user("caretaker", "Objektbetreuung", "CARETAKER", residence);
        user("clerk", "Sachbearbeitung", "CLERK", residence);
    }

    private void user(String key, String name, String role, ResidenceEntity residence) {
        users.findByUserKey(key).orElseGet(() -> users.save(create(key, name, role, residence)));
    }

    private UserEntity create(String key, String name, String role, ResidenceEntity residence) {
        var assigned = Set.of(roles.findByName(role).orElseThrow());
        return switch (role) {
            case "STUDENT" -> new StudentEntity(key, name, assigned, residence);
            case "CARETAKER" -> new CaretakerEntity(key, name, assigned, residence);
            case "CLERK" -> new ClerkEntity(key, name, assigned, residence);
            default -> throw new IllegalArgumentException("Unknown sample user role: " + role);
        };
    }
}
