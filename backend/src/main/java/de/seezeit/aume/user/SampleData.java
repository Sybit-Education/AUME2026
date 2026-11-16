package de.seezeit.aume.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import de.seezeit.aume.residence.ResidenceEntity;
import de.seezeit.aume.residence.ResidenceRepository;

@Component
public class SampleData implements CommandLineRunner {

	private final ResidenceRepository residences;
	private final UserRepository users;

	public SampleData(ResidenceRepository residences, UserRepository users) {
		this.residences = residences;
		this.users = users;
	}

	@Override
	public void run(String... args) {
		ResidenceEntity sonne = residences.save(new ResidenceEntity("Wohnanlage Sonne"));
		users.save(new UserEntity("student", "Studierende", Role.STUDENT, sonne));
		users.save(new UserEntity("caretaker", "Objektbetreuung", Role.CARETAKER, sonne));
		users.save(new UserEntity("clerk", "Sachbearbeitung", Role.CLERK, sonne));
	}

}
