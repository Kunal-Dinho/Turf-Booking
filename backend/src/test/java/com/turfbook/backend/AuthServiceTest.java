package com.turfbook.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("John Doe", "john@example.com", "hashed_secret_password", "CUSTOMER");
        sampleUser.setId("user-123");
    }

    @Test
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "mysecretpassword");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("mysecretpassword")).thenReturn("hashed_secret_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("user-123");
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token-xyz");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token-xyz", response.getToken());
        assertEquals("user-123", response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());

        verify(passwordEncoder).encode("mysecretpassword");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testRegister_DuplicateEmail_ThrowsConflict409() {
        RegisterRequest request = new RegisterRequest("Jane Doe", "john@example.com", "password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testLogin_ValidCredentials_Success() {
        LoginRequest request = new LoginRequest("john@example.com", "mysecretpassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("mysecretpassword", "hashed_secret_password")).thenReturn(true);
        when(jwtService.generateToken(sampleUser)).thenReturn("valid-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("valid-jwt-token", response.getToken());
        assertEquals("user-123", response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());
    }

    @Test
    void testLogin_InvalidPassword_ThrowsUnauthorized401() {
        LoginRequest request = new LoginRequest("john@example.com", "wrongpassword");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongpassword", "hashed_secret_password")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.login(request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid email or password"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void testLogin_EmailNotFound_ThrowsUnauthorized401() {
        LoginRequest request = new LoginRequest("nobody@example.com", "password123");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.login(request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid email or password"));
    }

    @Test
    void testGetCurrentUser_Success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));

        UserResponse response = authService.getCurrentUser("user-123");

        assertNotNull(response);
        assertEquals("user-123", response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());
        assertEquals("CUSTOMER", response.getRole());
    }
}
