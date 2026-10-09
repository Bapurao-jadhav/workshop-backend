package com.workshop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.config.AppProperties;
import com.workshop.entity.Role;
import com.workshop.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static AppProperties props(String secret, long minutes) {
        return new AppProperties(new AppProperties.Jwt(secret, minutes), new AppProperties.Cors(List.of()),
                new AppProperties.Admin("", "", ""), new AppProperties.Mail("x@y.z"), "UTC");
    }

    private static User user() {
        User u = new User();
        u.setId(12L);
        u.setEmail("a@b.com");
        u.setRole(Role.USER);
        return u;
    }

    @Test
    void roundTripsUserId() {
        JwtService service = new JwtService(props("0123456789-0123456789-0123456789-ab", 5));

        String token = service.generateToken(user());

        assertThat(service.extractUserId(token)).contains(12L);
    }

    @Test
    void rejectsTamperedAndGarbageTokens() {
        JwtService service = new JwtService(props("0123456789-0123456789-0123456789-ab", 5));
        String token = service.generateToken(user());

        assertThat(service.extractUserId(token + "x")).isEmpty();
        assertThat(service.extractUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService a = new JwtService(props("0123456789-0123456789-0123456789-ab", 5));
        JwtService b = new JwtService(props("ZZZZZZZZZZ-ZZZZZZZZZZ-ZZZZZZZZZZ-ab", 5));

        assertThat(b.extractUserId(a.generateToken(user()))).isEmpty();
    }

    @Test
    void rejectsExpiredTokens() {
        JwtService service = new JwtService(props("0123456789-0123456789-0123456789-ab", -1));

        assertThat(service.extractUserId(service.generateToken(user()))).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService(props("too-short", 5)))
                .isInstanceOf(IllegalStateException.class);
    }
}
