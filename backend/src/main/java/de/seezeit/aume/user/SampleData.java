package de.seezeit.aume.user;

import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;

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
        users.findByUserKey(key).orElseGet(() -> users.save(new UserEntity(key, name,
            Set.of(roles.findByName(role).orElseThrow()), residence)));
    }
}
