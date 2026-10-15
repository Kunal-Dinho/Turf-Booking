package com.turfbook.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerSecurityTest {

    @Mock
    private AuthService authService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private AuthController authController;

    private BookingController bookingController;

    @BeforeEach
    void setUp() {
        bookingController = new BookingController(bookingService);
    }

    @Test
    void testRegister_Success() {
        RegisterRequest req = new RegisterRequest("David", "david@example.com", "secure123");
        AuthResponse expected = new AuthResponse("tok-1", "user-d", "David", "david@example.com");

        when(authService.register(any(RegisterRequest.class))).thenReturn(expected);

        AuthResponse res = authController.register(req);

        assertNotNull(res);
        assertEquals("tok-1", res.getToken());
        assertEquals("david@example.com", res.getEmail());
    }

    @Test
    void testLogin_Success() {
        LoginRequest req = new LoginRequest("david@example.com", "secure123");
        AuthResponse expected = new AuthResponse("tok-1", "user-d", "David", "david@example.com");

        when(authService.login(any(LoginRequest.class))).thenReturn(expected);

        AuthResponse res = authController.login(req);

        assertNotNull(res);
        assertEquals("tok-1", res.getToken());
    }

    @Test
    void testGetCurrentUser_Unauthenticated_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authController.getCurrentUser(null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void testGetCurrentUser_Authenticated_ReturnsProfile() {
        UserPrincipal principal = new UserPrincipal("user-d", "david@example.com", "CUSTOMER");
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null);

        UserResponse userResponse = new UserResponse("user-d", "David", "david@example.com", "CUSTOMER", Instant.now());
        when(authService.getCurrentUser("user-d")).thenReturn(userResponse);

        UserResponse res = authController.getCurrentUser(auth);

        assertNotNull(res);
        assertEquals("user-d", res.getId());
        assertEquals("david@example.com", res.getEmail());
    }

    @Test
    void testGetMyBookings_Unauthenticated_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingController.getMyBookings(null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void testGetMyBookings_Authenticated_ReturnsBookings() {
        UserPrincipal principal = new UserPrincipal("user-d", "david@example.com", "CUSTOMER");
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null);

        BookingResponse b = new BookingResponse();
        b.setId("b-1");
        b.setCustomerId("user-d");

        when(bookingService.getMyBookings("user-d")).thenReturn(List.of(b));

        List<BookingResponse> res = bookingController.getMyBookings(auth);

        assertEquals(1, res.size());
        assertEquals("user-d", res.get(0).getCustomerId());
    }

    @Test
    void testCancelBooking_Unauthenticated_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingController.cancelBooking("TB-TEST-1", null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(bookingService, never()).cancelBooking(any(), any());
    }

    @Test
    void testCancelBooking_Authenticated_DelegatesToService() {
        UserPrincipal principal = new UserPrincipal("user-d", "david@example.com", "CUSTOMER");
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null);

        BookingResponse response = new BookingResponse();
        response.setBookingReference("TB-TEST-1");
        response.setStatus("CANCELLED");

        when(bookingService.cancelBooking("TB-TEST-1", principal)).thenReturn(response);

        BookingResponse res = bookingController.cancelBooking("TB-TEST-1", auth);

        assertNotNull(res);
        assertEquals("CANCELLED", res.getStatus());
        verify(bookingService).cancelBooking("TB-TEST-1", principal);
    }

    @Test
    void testGetBookingByReference_PassesAuthDetails() {
        UserPrincipal principal = new UserPrincipal("user-d", "david@example.com", "CUSTOMER");
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null);

        BookingResponse response = new BookingResponse();
        response.setBookingReference("TB-TEST-1");

        when(bookingService.getBookingByReference("TB-TEST-1", "user-d", false)).thenReturn(response);

        BookingResponse res = bookingController.getBookingByReference("TB-TEST-1", auth);

        assertNotNull(res);
        verify(bookingService).getBookingByReference("TB-TEST-1", "user-d", false);
    }
}
