package com.omniorder.identity.service;

import com.omniorder.identity.domain.User;
import com.omniorder.identity.domain.UserRole;
import com.omniorder.identity.repository.UserRepository;
import com.omniorder.identity.security.AuthService;
import com.omniorder.identity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldSaveUserWithHashedPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);

        authService.register("alice", "alice@example.com", "password123");

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat((User u) ->
            "alice".equals(u.getUsername())
            && "alice@example.com".equals(u.getEmail())
            && u.getPasswordHash() != null && !u.getPasswordHash().equals("password123")
            && u.getRole() == UserRole.CUSTOMER
        ));
    }

    @Test
    void register_duplicateUsername_shouldThrow() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("alice", "alice@example.com", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already taken");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticate_shouldReturnToken() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        User user = new User("alice", "alice@example.com", "hash", UserRole.CUSTOMER);
        user.setId(userId);
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(jwtService.generateToken("alice", userId, List.of("CUSTOMER"))).thenReturn("token-123");

        String token = authService.authenticate("alice", "password123");

        assertThat(token).isEqualTo("token-123");
    }

    @Test
    void authenticate_wrongPassword_shouldThrow() {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.authenticate("alice", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");
    }
}
