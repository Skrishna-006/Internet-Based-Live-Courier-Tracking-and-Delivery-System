package com.courier.tracking.service;

import com.courier.tracking.config.JwtProperties;
import com.courier.tracking.dto.CustomerRegistrationRequest;
import com.courier.tracking.dto.LoginRequest;
import com.courier.tracking.dto.LoginResponse;
import com.courier.tracking.dto.UserResponse;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import com.courier.tracking.exception.DuplicateResourceException;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Service managing user authentication, registration, credential security, and JWT issuance.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
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

    /**
     * Authenticates user credentials and generates a signed JWT token.
     * Enforces email normalization, BCrypt password verification, active account checks,
     * and safe response construction (strictly excluding password hashes and internal credentials).
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email() != null ? request.email().toLowerCase(Locale.ROOT).trim() : "";

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            log.warn("Login failed: invalid credentials for email '{}'", normalizedEmail);
            throw new BadCredentialsException("Invalid email or password");
        } catch (DisabledException ex) {
            log.warn("Login failed: account is deactivated for email '{}'", normalizedEmail);
            throw ex;
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            log.warn("Login rejected: user account {} is inactive", user.getEmail());
            throw new DisabledException("Account is deactivated. Please contact support.");
        }

        String accessToken = jwtTokenProvider.generateToken(user);
        long expiresInSeconds = jwtProperties.getExpirationMs() / 1000L;

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole().getName().name(),
                user.getActive(),
                user.getCreatedAt()
        );

        log.info("User successfully logged in: userId={}, email='{}', role={}", user.getId(), user.getEmail(), user.getRole().getName());
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, userResponse);
    }
}
