package com.workshop.service;

import com.workshop.config.AppProperties;
import com.workshop.entity.Role;
import com.workshop.entity.User;
import com.workshop.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Provisions the first admin from ADMIN_EMAIL / ADMIN_PASSWORD on startup. There is no public admin
 * registration endpoint. If the variables are missing, no admin is created.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AppProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(AppProperties props, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = props.admin();
        if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            log.info("ADMIN_EMAIL / ADMIN_PASSWORD not set; no admin account provisioned");
            return;
        }
        if (admin.password().length() < 8) {
            log.warn("ADMIN_PASSWORD is shorter than 8 characters; admin account not created");
            return;
        }
        String email = AuthService.normalizeEmail(admin.email());
        if (userRepository.existsByEmail(email)) {
            log.info("Admin provisioning skipped: an account with the configured admin email already exists");
            return;
        }
        User user = new User();
        user.setName(StringUtils.hasText(admin.name()) ? admin.name().trim() : "Administrator");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        log.info("Initial admin account created for {}", email);
    }
}
