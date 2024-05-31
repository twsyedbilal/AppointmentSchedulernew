package com.example.slabiak.appointmentscheduler.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

	 public void authenticateUser(CustomUserDetails userDetails) {
	        UsernamePasswordAuthenticationToken authenticationToken = 
	                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
	        
	        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
	    }
}
