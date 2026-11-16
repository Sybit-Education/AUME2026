package de.seezeit.aume.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GreetingController {

	static final String HEADER = "X-Dev-User";

	private final UserRepository users;

	public GreetingController(UserRepository users) {
		this.users = users;
	}

	@GetMapping(path = "/api/greeting", produces = MediaType.TEXT_PLAIN_VALUE)
	@Transactional(readOnly = true)
	public String greeting(@RequestHeader(HEADER) String userKey) {
		UserEntity user = users.findByUserKey(userKey)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (user.getRole() != Role.STUDENT) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		return "Hallo " + user.getName() + ", willkommen in " + user.getResidence().getName() + ".";
	}

}
