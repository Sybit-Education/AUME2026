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
        var assignStaff = privilege("RESIDENCE_ASSIGN_STAFF");
        var plan = privilege("INSPECTION_PLAN");
        var respond = privilege("INSPECTION_PERIOD_RESPOND");
        role("STUDENT", Set.of(greeting));
        role("CARETAKER", Set.of(residence, respond));
        role("CLERK", Set.of(residence, assignStaff, plan));
    }

    private PrivilegeEntity privilege(String name) {
        return privileges.findByName(name).orElseGet(() -> privileges.save(new PrivilegeEntity(name)));
    }

    private void role(String name, Set<PrivilegeEntity> assigned) {
        roles.findByName(name).orElseGet(() -> roles.save(new RoleEntity(name, assigned)));
    }
}
