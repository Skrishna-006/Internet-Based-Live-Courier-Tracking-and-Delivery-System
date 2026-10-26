package com.courier.tracking.service;

import com.courier.tracking.dto.CustomerRegistrationRequest;
import com.courier.tracking.dto.UserResponse;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.exception.DuplicateResourceException;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Service managing user authentication, registration, and credential security.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new customer account.
     * Enforces email normalization, BCrypt password hashing, duplicate rejection,
     * and unconditional server-side assignment of ROLE_CUSTOMER.
     */
    @Transactional
    public UserResponse registerCustomer(CustomerRegistrationRequest request) {
        String normalizedEmail = request.email().toLowerCase(Locale.ROOT).trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Customer registration rejected: email '{}' already in use", normalizedEmail);
            throw new DuplicateResourceException("An account with email '" + normalizedEmail + "' already exists.");
        }

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("ROLE_CUSTOMER is not configured in the database"));

        String encodedPassword = passwordEncoder.encode(request.password());
        String cleanFullName = request.fullName().trim();
        String cleanPhone = request.phoneNumber() != null ? request.phoneNumber().trim() : null;

        User user = new User(normalizedEmail, encodedPassword, cleanFullName, cleanPhone, customerRole);

        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Customer account successfully registered: userId={}, email='{}'", saved.getId(), saved.getEmail());

            return new UserResponse(
                    saved.getId(),
                    saved.getEmail(),
                    saved.getFullName(),
                    saved.getPhoneNumber(),
                    saved.getRole().getName().name(),
                    saved.getActive(),
                    saved.getCreatedAt()
            );
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database unique constraint triggered for email '{}' during concurrent registration", normalizedEmail);
            throw new DuplicateResourceException("An account with email '" + normalizedEmail + "' already exists.");
        }
    }
}
