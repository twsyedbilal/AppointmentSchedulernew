package com.example.slabiak.appointmentscheduler.controllerrest;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.slabiak.appointmentscheduler.dao.user.UserRepository;
import com.example.slabiak.appointmentscheduler.entity.user.Role;
import com.example.slabiak.appointmentscheduler.entity.user.User;
import com.example.slabiak.appointmentscheduler.security.AuthenticationService;
import com.example.slabiak.appointmentscheduler.security.CustomUserDetails;


@RestController
@RequestMapping("/rest")
public class AuthController {

	@Autowired
	private UserRepository userRepo;
	@Autowired
	private final AuthenticationService authenticationService;

	public AuthController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@GetMapping("/u")
	public ResponseEntity<?> loginuser(@RequestParam String userName,@Valid @RequestParam String password) {
		try {
			StringBuilder res = new StringBuilder();
			Optional<User> findByUserName = userRepo.findByUserName(userName);

			if (findByUserName.isPresent()) {
				User u = findByUserName.get();
				if (userName.equals(findByUserName.get().getUserName())) {
					// &&
					// password.equals(findByUserName.get().getPassword())
					Collection<Role> roles = u.getRoles();
					Collection<GrantedAuthority> authorities = roles.stream()
				                .map(role -> new SimpleGrantedAuthority(role.getName()))
				                .collect(Collectors.toList());

					CustomUserDetails customUserDetails = new CustomUserDetails(u.getId(), u.getFirstName(), u.getLastName(), userName,
							u.getEmail(), null, authorities);
					authenticationService.authenticateUser(customUserDetails);
					res.append(findByUserName.get().getUserName()).append("_").append(findByUserName.get().getId()).append(customUserDetails);

				} else {
					res.append("error");
				}
			}

			return new ResponseEntity<>(res, HttpStatus.OK);

		} catch (AuthenticationException e) {
			return new ResponseEntity<>("Login failed: " + e.getMessage(), HttpStatus.ACCEPTED);
		}
	}

}
