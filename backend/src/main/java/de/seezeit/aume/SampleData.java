package de.seezeit.aume;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import de.seezeit.aume.residence.FlatEntity;
import de.seezeit.aume.residence.FlatRepository;
import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;
import de.seezeit.aume.residence.StaffAssignmentEntity;
import de.seezeit.aume.residence.StaffAssignmentRepository;
import de.seezeit.aume.residence.StaffKind;
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
    private static final Map<String, String> KEYCLOAK_SUBJECTS = Map.of(
        "student", "00000000-0000-4000-8000-000000000001",
        "clerk", "00000000-0000-4000-8000-000000000002",
        "caretaker", "00000000-0000-4000-8000-000000000003",
        "caretaker2", "00000000-0000-4000-8000-000000000004");

    private final ResidenceRepository residences;
    private final FlatRepository flats;
    private final StaffAssignmentRepository staff;
    private final UserRepository users;
    private final RoleRepository roles;
    private final String issuer;

    public SampleData(ResidenceRepository residences, FlatRepository flats, StaffAssignmentRepository staff,
                      UserRepository users, RoleRepository roles,
                      @Value("${KEYCLOAK_ISSUER_URI}") String issuer) {
        this.residences = residences;
        this.flats = flats;
        this.staff = staff;
        this.users = users;
        this.roles = roles;
        this.issuer = issuer;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var sonne = residence("Sonnenbühl West I");
        var petershausen = residence("Petershausen");
        var egg = residence("Egg");

        var caretaker = user("caretaker", "Objektbetreuung", "CARETAKER", sonne);
        user("student", "Studierende", "STUDENT", sonne);
        var clerk = user("clerk", "Sachbearbeitung", "CLERK", sonne);
        // Weitere Personen nur als Stammdaten; die Mock-Anmeldung kennt nur die drei Kennungen oben.
        var caretaker2 = user("caretaker2", "Objektbetreuung Petershausen", "CARETAKER", petershausen);
        var caretaker3 = user("caretaker3", "Objektbetreuung Egg", "CARETAKER", egg);
        var clerk2 = user("clerk2", "Sachbearbeitung Petershausen", "CLERK", petershausen);

        flat(sonne, "S-01", 4);
        flat(sonne, "S-02", 3);
        flat(petershausen, "4.12", 4);
        flat(petershausen, "4.13", 3);
        flat(petershausen, "3.08", 4);
        flat(petershausen, "3.09", 5);
        flat(petershausen, "3.10", 3);
        flat(egg, "E-12", 3);
        flat(egg, "E-14", 4);

        assign(sonne, StaffKind.CARETAKER, caretaker);
        assign(sonne, StaffKind.DEPUTY, caretaker2);
        assign(petershausen, StaffKind.CARETAKER, caretaker2);
        assign(petershausen, StaffKind.DEPUTY, caretaker3);
        // Egg hat bewusst keine Vertretung: Die Vertretung darf leer sein.
        assign(egg, StaffKind.CARETAKER, caretaker3);

        // Die Mock-Sachbearbeitung sieht Sonnenbühl West I und Egg, aber nicht Petershausen.
        assign(sonne, StaffKind.CLERK, clerk);
        assign(egg, StaffKind.CLERK, clerk);
        assign(petershausen, StaffKind.CLERK, clerk2);
    }

    private ResidenceEntity residence(String name) {
        return residences.findFirstByName(name).orElseGet(() -> residences.save(new ResidenceEntity(name)));
    }

    private void flat(ResidenceEntity residence, String label, int capacity) {
        flats.findByResidenceIdAndLabel(residence.getId(), label)
            .orElseGet(() -> flats.save(new FlatEntity(residence, label, capacity)));
    }

    private void assign(ResidenceEntity residence, StaffKind kind, UserEntity user) {
        staff.findByResidenceIdAndKind(residence.getId(), kind)
            .orElseGet(() -> staff.save(new StaffAssignmentEntity(user, residence, kind)));
    }

    private UserEntity user(String key, String name, String role, ResidenceEntity residence) {
        var user = users.findByUserKey(key).orElseGet(() -> users.save(create(key, name, role, residence)));
        var subject = KEYCLOAK_SUBJECTS.get(key);
        if (subject != null && user.getIdentitySubject() == null) user.linkIdentity(issuer, subject);
        return user;
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
