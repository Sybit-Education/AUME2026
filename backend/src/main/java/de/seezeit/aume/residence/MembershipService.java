package de.seezeit.aume.residence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import de.seezeit.aume.auth.AppUserService;
import de.seezeit.aume.user.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Joins a flat with a single-use code and leaves it. */
@Service
@Transactional(readOnly = true)
public class MembershipService {
    public static final String INVALID_CODE = "invalid_code";
    public static final String ALREADY_MEMBER = "already_member";
    public static final String FLAT_FULL = "flat_full";
    public static final String NOT_MEMBER = "not_member";

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int MAX_CODE_LENGTH = 64;

    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final AppUserService users;
    private final EntityManager entities;

    public MembershipService(MembershipRepository memberships, InvitationRepository invitations,
                             AppUserService users, EntityManager entities) {
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
        this.entities = entities;
    }

    public Optional<MembershipView> current(String userKey) {
        var today = today();
        return memberships.findActive(users.requireUser(userKey).getId(), today)
            .map(membership -> view(membership, today));
    }

    /** Returns the active membership or fails with {@code 404 not_member}. */
    public ActiveMembership requireActive(String userKey) {
        var today = today();
        var user = users.requireUser(userKey);
        var membership = memberships.findActive(user.getId(), today)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, NOT_MEMBER));
        var members = memberships.findActiveInFlat(membership.getFlat().getId(), today).stream()
            .map(MembershipEntity::getUser).toList();
        return new ActiveMembership(membership.getFlat(), user, members);
    }

    public record ActiveMembership(FlatEntity flat, UserEntity user, List<UserEntity> members) {}

    @Transactional
    public MembershipView join(String userKey, String code) {
        // Lock in a fixed order to prevent double joins and overfilled flats.
        var user = entities.find(UserEntity.class, users.requireUser(userKey).getId(), LockModeType.PESSIMISTIC_WRITE);
        var today = today();
        if (memberships.findActive(user.getId(), today).isPresent()) {
            throw failure(HttpStatus.CONFLICT, ALREADY_MEMBER);
        }
        var now = Instant.now();
        // All unusable codes return the same error.
        var invitation = normalized(code).map(MembershipService::hash).flatMap(invitations::findByTokenHash)
            .filter(candidate -> usable(candidate, user, now))
            .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST, INVALID_CODE));
        var flat = entities.find(FlatEntity.class, invitation.getFlat().getId(), LockModeType.PESSIMISTIC_WRITE);
        if (memberships.findActiveInFlat(flat.getId(), today).size() >= flat.getCapacity()) {
            throw failure(HttpStatus.CONFLICT, FLAT_FULL);
        }
        var membership = memberships.save(new MembershipEntity(flat, user, today));
        invitation.setUsedBy(user);
        invitation.setUsedAt(now);
        memberships.flush();
        return view(membership, today);
    }

    @Transactional
    public void leave(String userKey) {
        var today = today();
        var membership = memberships.findActive(users.requireUser(userKey).getId(), today)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, NOT_MEMBER));
        membership.setValidTo(today);
    }

    /** Hashes a code with SHA-256, ignoring case. */
    public static String hash(String code) {
        try {
            var normalized = code.strip().toUpperCase(Locale.ROOT);
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<String> normalized(String code) {
        return Optional.ofNullable(code).map(String::strip)
            .filter(value -> !value.isEmpty() && value.length() <= MAX_CODE_LENGTH);
    }

    private static boolean usable(InvitationEntity invitation, UserEntity user, Instant now) {
        return invitation.getUsedAt() == null && invitation.getRevokedAt() == null
            && invitation.getExpiresAt().isAfter(now)
            && invitation.getFlat().getResidence().getId().equals(user.getResidence().getId());
    }

    private MembershipView view(MembershipEntity membership, LocalDate today) {
        var flat = membership.getFlat();
        var members = memberships.findActiveInFlat(flat.getId(), today).stream()
            .map(member -> member.getUser().getName()).toList();
        return new MembershipView(flat.getLabel(), flat.getResidence().getName(), membership.getValidFrom(), members);
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static ResponseStatusException failure(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
