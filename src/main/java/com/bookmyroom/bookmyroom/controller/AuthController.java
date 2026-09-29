
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.User;
import com.bookmyroom.bookmyroom.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<String> ROLES =
            Set.of("ADMIN", "RECEPTIONIST", "ACCOUNTANT");

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Request for creating a staff account
    public record CreateUserRequest(
            @NotBlank String username,

            @NotBlank
            @Size(min = 12, max = 72)
            String password,

            @NotBlank String role
    ) {}

    // Request for resetting a password
    public record ResetPasswordRequest(
            @NotBlank
            @Size(min = 12, max = 72)
            String password
    ) {}

    // Response without sensitive password information
    public record UserResponse(
            Long id,
            String username,
            String role,
            boolean enabled
    ) {}

    // 1. VIEW CURRENT LOGGED-IN USER
    @GetMapping("/me")
    public UserResponse currentUser(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please log in first");
        }

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"));

        return toResponse(user);
    }

    // 2. CREATE STAFF ACCOUNT - ADMIN ONLY
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request) {

        String username = request.username().trim();
        String role = request.role().trim().toUpperCase();

        if (username.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username cannot be empty");
        }

        if (!ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid user role");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists");
        }

        User user = new User();

        user.setUsername(username);
        user.setPassword(
                passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setEnabled(true);

        return toResponse(userRepository.save(user));
    }

    // 3. VIEW ALL STAFF ACCOUNTS - ADMIN ONLY
    @GetMapping("/users")
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 4. ENABLE OR DISABLE ACCOUNT - ADMIN ONLY
    @PutMapping("/users/{id}/enabled")
    public UserResponse updateAccountStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication authentication) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"));

        if (authentication != null
                && user.getUsername().equals(
                authentication.getName())
                && !enabled) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot disable your own account");
        }

        user.setEnabled(enabled);

        return toResponse(userRepository.save(user));
    }

    // 5. RESET STAFF PASSWORD - ADMIN ONLY
    @PutMapping("/users/{id}/password")
    public UserResponse resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"));

        user.setPassword(
                passwordEncoder.encode(request.password()));

        return toResponse(userRepository.save(user));
    }

    // Convert User entity into a safe response
    private UserResponse toResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isEnabled()
        );
    }
}