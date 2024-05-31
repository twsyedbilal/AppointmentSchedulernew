package com.example.slabiak.appointmentscheduler.controllerrest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.slabiak.appointmentscheduler.entity.Appointment;
import com.example.slabiak.appointmentscheduler.entity.ChatMessage;
import com.example.slabiak.appointmentscheduler.security.CustomUserDetails;
import com.example.slabiak.appointmentscheduler.service.AppointmentService;
import com.example.slabiak.appointmentscheduler.service.ExchangeService;
import com.example.slabiak.appointmentscheduler.service.UserService;
import com.example.slabiak.appointmentscheduler.service.WorkService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/rest/appointments")
public class AppointmentRestController {

	private static final String REJECTION_CONFIRMATION_VIEW = "appointments/rejectionConfirmation";

	private final WorkService workService;
	private final UserService userService;
	private final AppointmentService appointmentService;
	private final ExchangeService exchangeService;

	public AppointmentRestController(WorkService workService, UserService userService,
			AppointmentService appointmentService, ExchangeService exchangeService) {
		this.workService = workService;
		this.userService = userService;
		this.appointmentService = appointmentService;
		this.exchangeService = exchangeService;
	}

	@GetMapping("/all")
	public ResponseEntity<?> showAllAppointments() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();

		if (currentUser.hasRole("ROLE_CUSTOMER")) {
			List<Appointment> appointmentByCustomerId = appointmentService
					.getAppointmentByCustomerId(currentUser.getId());
			return new ResponseEntity<>(appointmentByCustomerId, HttpStatus.ACCEPTED);
		} else if (currentUser.hasRole("ROLE_PROVIDER")) {
			List<Appointment> appointmentByProviderId = appointmentService
					.getAppointmentByProviderId(currentUser.getId());
			return new ResponseEntity<>(appointmentByProviderId, HttpStatus.ACCEPTED);
		} else if (currentUser.hasRole("ROLE_ADMIN")) {
			List<Appointment> allAppointments = appointmentService.getAllAppointments();
			return new ResponseEntity<>(allAppointments, HttpStatus.ACCEPTED);
		} else {
			return new ResponseEntity<>(null, HttpStatus.ACCEPTED);
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> showAppointmentDetail(@PathVariable("id") int appointmentId) {
		
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		Appointment appointment = appointmentService.getAppointmentByIdWithAuthorization(appointmentId);
		ChatMessage ch = new ChatMessage();
		boolean allowedToRequestRejection = appointmentService.isCustomerAllowedToRejectAppointment(currentUser.getId(),
				appointmentId);
		boolean allowedToAcceptRejection = appointmentService.isProviderAllowedToAcceptRejection(currentUser.getId(),
				appointmentId);
		boolean allowedToExchange = exchangeService.checkIfEligibleForExchange(currentUser.getId(), appointmentId);
		String cancelNotAllowedReason = appointmentService.getCancelNotAllowedReason(currentUser.getId(),
				appointmentId);

		if (appointment == null) {
			return new ResponseEntity<>("Appointment not found", HttpStatus.NOT_FOUND);
		}

		Map<String, Object> response = new HashMap<>();
		response.put("appointment", appointment);
		response.put("chatMessage", ch);
		response.put("allowedToRequestRejection", allowedToRequestRejection);
		response.put("allowedToAcceptRejection", allowedToAcceptRejection);
		response.put("allowedToExchange", allowedToExchange);
		response.put("allowedToCancel", cancelNotAllowedReason == null);
		response.put("cancelNotAllowedReason", cancelNotAllowedReason);

		if (allowedToRequestRejection) {
			response.put("remainingTime",
					formatDuration(Duration.between(LocalDateTime.now(), appointment.getEnd().plusDays(1))));
		}

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/reject")
	public ResponseEntity<String> processAppointmentRejectionRequestByToken(@RequestParam("token") String token) {
		boolean result = appointmentService.requestAppointmentRejection(token);

		if (result) {
			return new ResponseEntity<>("Rejection request processed successfully", HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Failed to process rejection request", HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/rejectById")
	public ResponseEntity<String> processAppointmentRejectionRequestById(@RequestParam("token") String token) {
		boolean result = appointmentService.requestAppointmentRejection(token);

		if (result) {
			return new ResponseEntity<>("Rejection request processed successfully", HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Failed to process rejection request", HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/acceptRejection")
	public ResponseEntity<String> acceptAppointmentRejectionRequest(@RequestParam("appointmentId") int appointmentId) {
		
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		boolean result = appointmentService.acceptRejection(appointmentId, currentUser.getId());
		if (result) {
			return new ResponseEntity<>("Rejection request accepted successfully", HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Failed to accept rejection request", HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/acceptRejection")
	public ResponseEntity<String> acceptAppointmentRejectionRequest(@RequestParam("token") String token) {
		boolean result = appointmentService.acceptRejection(token);
		if (result) {
			return new ResponseEntity<>("Rejection request accepted successfully", HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Failed to accept rejection request", HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/messages/new")
	public ResponseEntity<String> addNewChatMessage(@ModelAttribute("chatMessage") ChatMessage chatMessage,
			@RequestParam("appointmentId") int appointmentId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		int authorId = currentUser.getId();
		appointmentService.addMessageToAppointmentChat(appointmentId, authorId, chatMessage);
		return new ResponseEntity<>("Chat message added successfully", HttpStatus.OK);
	}

	@GetMapping("/new")
	public ResponseEntity<?> selectProvider() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		Map<String, Object> response = new HashMap<>();
		if (currentUser.hasRole("ROLE_CUSTOMER_RETAIL")) {
			response.put("providers", userService.getProvidersWithRetailWorks());
		} else if (currentUser.hasRole("ROLE_CUSTOMER_CORPORATE")) {
			response.put("providers", userService.getProvidersWithCorporateWorks());
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/new/{providerId}")
	public ResponseEntity<?> selectService(@PathVariable("providerId") int providerId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		Map<String, Object> response = new HashMap<>();
		if (currentUser.hasRole("ROLE_CUSTOMER_RETAIL")) {
			response.put("works", workService.getWorksForRetailCustomerByProviderId(providerId));
		} else if (currentUser.hasRole("ROLE_CUSTOMER_CORPORATE")) {
			response.put("works", workService.getWorksForCorporateCustomerByProviderId(providerId));
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/new/{providerId}/{workId}")
	public ResponseEntity<String> selectDate(@PathVariable("workId") int workId,
			@PathVariable("providerId") int providerId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		if (workService.isWorkForCustomer(workId, currentUser.getId())) {
			return new ResponseEntity<>("Please select a date", HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
	}

	@GetMapping("/new/{providerId}/{workId}/{dateTime}")
	public ResponseEntity<?> showNewAppointmentSummary(@PathVariable("workId") int workId,
			@PathVariable("providerId") int providerId, @PathVariable("dateTime") String start) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		Map<String, Object> response = new HashMap<>();
		if (appointmentService.isAvailable(workId, providerId, currentUser.getId(), LocalDateTime.parse(start))) {
			response.put("work", workService.getWorkById(workId));
			response.put("provider", userService.getProviderById(providerId).getFirstName() + " "
					+ userService.getProviderById(providerId).getLastName());
			response.put("start", LocalDateTime.parse(start));
			response.put("end", LocalDateTime.parse(start).plusMinutes(workService.getWorkById(workId).getDuration()));
			return new ResponseEntity<>(response, HttpStatus.OK);
		} else {
			return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
		}
	}

	@PostMapping("/new")
	public ResponseEntity<String> bookAppointment(@RequestParam("workId") int workId,
			@RequestParam("providerId") int providerId, @RequestParam("start") String start) {
		
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		appointmentService.createNewAppointment(workId, providerId, currentUser.getId(), LocalDateTime.parse(start));
		return ResponseEntity.status(HttpStatus.CREATED).body("Appointment booked successfully");
	}

	@PostMapping("/cancel")
	public String cancelAppointment(@RequestParam("appointmentId") int appointmentId) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
		
		appointmentService.cancelUserAppointmentById(appointmentId, currentUser.getId());
		return "redirect:/appointments/all";
	}

	public static String formatDuration(Duration duration) {
		long s = duration.getSeconds();
		return String.format("%dh%02dm", s / 3600, (s % 3600) / 60);
	}

}
