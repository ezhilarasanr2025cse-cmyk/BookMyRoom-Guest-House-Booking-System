
package com.bookmyroom.bookmyroom.config;

import com.bookmyroom.bookmyroom.model.User;
import com.bookmyroom.bookmyroom.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminInitializer {

    @Bean
    public CommandLineRunner initializeAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            String username = System.getenv("ADMIN_USERNAME");
            String password = System.getenv("ADMIN_PASSWORD");

            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                System.out.println(
                        "Admin credentials are not configured.");
                return;
            }

            if (password.length() < 12) {
                throw new IllegalStateException(
                        "Admin password must have at least 12 characters.");
            }

            if (!userRepository.existsByUsername(username)) {

                User admin = new User();
                admin.setUsername(username);
                admin.setPassword(
                        passwordEncoder.encode(password));
                admin.setRole("ADMIN");
                admin.setEnabled(true);

                userRepository.save(admin);

                System.out.println(
                        "Administrator account created successfully.");
            }
        };
    }
}