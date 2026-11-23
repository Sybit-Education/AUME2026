package de.seezeit.aume.residence;

import java.time.LocalDate;
import java.util.List;

/** The user's flat and current flatmates. */
public record MembershipView(String flat, String residence, LocalDate validFrom, List<String> members) {}
