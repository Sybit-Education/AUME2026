package de.seezeit.aume.user;

import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class SecurityData implements CommandLineRunner {
    private final RoleRepository roles;
    private final PrivilegeRepository privileges;

    public SecurityData(RoleRepository roles, PrivilegeRepository privileges) {
        this.roles = roles;
        this.privileges = privileges;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var greeting = privilege("GREETING_READ");
        var residence = privilege("RESIDENCE_READ");
        var inspection = privilege("INSPECTION_PERFORM");
        var infoRead = privilege("INFO_PAGE_READ");
        var infoEdit = privilege("INFO_PAGE_EDIT");
        var standards = privilege("STANDARDS_VIEW");
        var assignStaff = privilege("RESIDENCE_ASSIGN_STAFF");
        var checklist = privilege("CHECKLIST_READ");
        role("STUDENT", Set.of(greeting, infoRead, checklist));
        role("CARETAKER", Set.of(residence, inspection));
        role("CLERK", Set.of(residence, infoRead, infoEdit, standards, assignStaff));
    }

    private PrivilegeEntity privilege(String name) {
        return privileges.findByName(name).orElseGet(() -> privileges.save(new PrivilegeEntity(name)));
    }

    private void role(String name, Set<PrivilegeEntity> assigned) {
        roles.findByName(name).orElseGet(() -> roles.save(new RoleEntity(name, assigned)));
    }
}
