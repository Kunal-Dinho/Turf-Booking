package com.turfbook.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TurfRepository turfRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private RazorpayService razorpayService;
    private BookingService bookingService;
    private Turf sampleTurf;
    private UserPrincipal customerPrincipal;
    private UserPrincipal adminPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String testKeyId = "rzp_test_sampleKey123";
    private final String testKeySecret = "secret_sampleKeySecret456";
    private final String testWebhookSecret = "webhook_sampleSecret789";

    @BeforeEach
    void setUp() {
        razorpayService = new RazorpayService(testKeyId, testKeySecret, testWebhookSecret, 10, objectMapper);
        bookingService = new BookingService(bookingRepository, turfRepository, mongoTemplate, razorpayService, objectMapper);

        sampleTurf = new Turf("Apex Ground", "Bangalore", "Football", 1000.0);
        sampleTurf.setId("turf-apex");

        customerPrincipal = new UserPrincipal("cust-alex-1", "alex@example.com", "CUSTOMER");
        adminPrincipal = new UserPrincipal("admin-root", "admin@turfbook.com", "ADMIN");
    }

    @Test
    void testCreateBooking_CreatesRazorpayOrderWithAuthoritativeAmount() {
        BookingRequest request = new BookingRequest(
                "turf-apex",
                "Alex Hunter",
                "alex@example.com",
                "+91 9999999999",
                LocalDate.now().plusDays(5),
                LocalTime.of(18, 0),
                2
        );

        when(turfRepository.findById("turf-apex")).thenReturn(Optional.of(sampleTurf));
        when(bookingRepository.existsBySlotKeysInAndSlotLockedTrue(anyCollection())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.createBooking(request, "cust-alex-1");

        assertNotNull(response);
        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertEquals("CREATED", response.getPaymentStatus());
        assertTrue(response.isSlotLocked(), "Slot must be locked while payment is pending");
        assertEquals(2000.0, response.getTotalPrice(), "Price must be server-calculated (1000 * 2 = 2000)");
        assertNotNull(response.getRazorpayOrderId());
        assertEquals(testKeyId, response.getRazorpayKeyId(), "Public Key ID must be exposed to client");
        assertNotNull(response.getExpiresAt());
    }

    @Test
    void testVerifyPayment_ValidSignature_ConfirmsBooking() {
        String bookingRef = "TB-261015-XYZ1";
        String orderId = "order_sample_001";
        String paymentId = "pay_sample_001";

        // Generate genuine valid signature
        String validSignature = razorpayService.calculateHmacSha256(orderId + "|" + paymentId, testKeySecret);

        Booking pendingBooking = new Booking();
        pendingBooking.setBookingReference(bookingRef);
        pendingBooking.setRazorpayOrderId(orderId);
        pendingBooking.setStatus("PENDING_PAYMENT");
        pendingBooking.setPaymentStatus("CREATED");
        pendingBooking.setSlotLocked(true);
        pendingBooking.setTotalPrice(2000.0);
        pendingBooking.setCustomerId("cust-alex-1");
        pendingBooking.setExpiresAt(Instant.now().plusSeconds(600));

        Booking confirmedBooking = new Booking();
        confirmedBooking.setBookingReference(bookingRef);
        confirmedBooking.setRazorpayOrderId(orderId);
        confirmedBooking.setRazorpayPaymentId(paymentId);
        confirmedBooking.setStatus("CONFIRMED");
        confirmedBooking.setPaymentStatus("SUCCESS");
        confirmedBooking.setSlotLocked(true);
        confirmedBooking.setTotalPrice(2000.0);
        confirmedBooking.setCustomerId("cust-alex-1");

        when(bookingRepository.findByBookingReference(bookingRef)).thenReturn(Optional.of(pendingBooking));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class)))
                .thenReturn(confirmedBooking);

        PaymentVerificationRequest req = new PaymentVerificationRequest(bookingRef, orderId, paymentId, validSignature);
        BookingResponse result = bookingService.verifyPayment(req, customerPrincipal);

        assertNotNull(result);
        assertEquals("CONFIRMED", result.getStatus());
        assertEquals("SUCCESS", result.getPaymentStatus());
        assertEquals(paymentId, result.getRazorpayPaymentId());
    }

    @Test
    void testVerifyPayment_InvalidSignature_ThrowsBadRequest() {
        String bookingRef = "TB-261015-XYZ1";
        String orderId = "order_sample_001";
        String paymentId = "pay_sample_001";
        String forgedSignature = "forged_invalid_signature_123456";

        Booking pendingBooking = new Booking();
        pendingBooking.setBookingReference(bookingRef);
        pendingBooking.setRazorpayOrderId(orderId);
        pendingBooking.setStatus("PENDING_PAYMENT");
        pendingBooking.setCustomerId("cust-alex-1");
        pendingBooking.setExpiresAt(Instant.now().plusSeconds(600));

        when(bookingRepository.findByBookingReference(bookingRef)).thenReturn(Optional.of(pendingBooking));

        PaymentVerificationRequest req = new PaymentVerificationRequest(bookingRef, orderId, paymentId, forgedSignature);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.verifyPayment(req, customerPrincipal)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid payment signature"));
    }

    @Test
    void testVerifyPayment_WrongOrderAssociation_ThrowsBadRequest() {
        String bookingRef = "TB-261015-XYZ1";
        Booking pendingBooking = new Booking();
        pendingBooking.setBookingReference(bookingRef);
        pendingBooking.setRazorpayOrderId("order_correct_123");
        pendingBooking.setStatus("PENDING_PAYMENT");
        pendingBooking.setCustomerId("cust-alex-1");

        when(bookingRepository.findByBookingReference(bookingRef)).thenReturn(Optional.of(pendingBooking));

        PaymentVerificationRequest req = new PaymentVerificationRequest(bookingRef, "order_wrong_999", "pay_1", "sig");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.verifyPayment(req, customerPrincipal)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("does not match"));
    }

    @Test
    void testVerifyPayment_IdempotentDuplicateCall_ReturnsConfirmedWithoutError() {
        String bookingRef = "TB-261015-XYZ1";
        String orderId = "order_sample_001";
        String paymentId = "pay_sample_001";
        String validSignature = razorpayService.calculateHmacSha256(orderId + "|" + paymentId, testKeySecret);

        Booking alreadyConfirmed = new Booking();
        alreadyConfirmed.setBookingReference(bookingRef);
        alreadyConfirmed.setRazorpayOrderId(orderId);
        alreadyConfirmed.setRazorpayPaymentId(paymentId);
        alreadyConfirmed.setStatus("CONFIRMED");
        alreadyConfirmed.setPaymentStatus("SUCCESS");
        alreadyConfirmed.setCustomerId("cust-alex-1");

        when(bookingRepository.findByBookingReference(bookingRef)).thenReturn(Optional.of(alreadyConfirmed));

        PaymentVerificationRequest req = new PaymentVerificationRequest(bookingRef, orderId, paymentId, validSignature);
        BookingResponse result = bookingService.verifyPayment(req, customerPrincipal);

        assertNotNull(result);
        assertEquals("CONFIRMED", result.getStatus());
        assertEquals("SUCCESS", result.getPaymentStatus());
        // Verify findAndModify was never invoked again
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(), eq(Booking.class));
    }

    @Test
    void testVerifyPayment_ExpiredReservation_ThrowsGone() {
        String bookingRef = "TB-261015-EXPIRED";
        String orderId = "order_exp_001";
        String paymentId = "pay_exp_001";
        String sig = razorpayService.calculateHmacSha256(orderId + "|" + paymentId, testKeySecret);

        Booking expiredBooking = new Booking();
        expiredBooking.setBookingReference(bookingRef);
        expiredBooking.setRazorpayOrderId(orderId);
        expiredBooking.setStatus("PENDING_PAYMENT");
        expiredBooking.setExpiresAt(Instant.now().minusSeconds(120)); // Expired 2 minutes ago
        expiredBooking.setTotalPrice(1500.0);

        when(bookingRepository.findByBookingReference(bookingRef)).thenReturn(Optional.of(expiredBooking));

        PaymentVerificationRequest req = new PaymentVerificationRequest(bookingRef, orderId, paymentId, sig);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.verifyPayment(req, null)
        );

        assertEquals(410, ex.getStatusCode().value(), "Must return 410 GONE for expired reservation");
        assertTrue(ex.getReason().contains("Reservation expired"));
    }

    @Test
    void testWebhook_PaymentCaptured_ConfirmsPendingBooking() {
        String orderId = "order_wb_100";
        String paymentId = "pay_wb_100";

        Booking pending = new Booking();
        pending.setBookingReference("TB-WB-001");
        pending.setRazorpayOrderId(orderId);
        pending.setStatus("PENDING_PAYMENT");
        pending.setTotalPrice(1000.0);
        pending.setExpiresAt(Instant.now().plusSeconds(600));

        when(bookingRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(pending));

        String rawPayload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_wb_100",
                        "order_id": "order_wb_100",
                        "amount": 100000,
                        "currency": "INR"
                      }
                    }
                  }
                }
                """;
        String signature = razorpayService.calculateHmacSha256(rawPayload, testWebhookSecret);

        bookingService.handleWebhook(rawPayload, signature);

        assertEquals("CONFIRMED", pending.getStatus());
        assertEquals("SUCCESS", pending.getPaymentStatus());
        assertEquals(paymentId, pending.getRazorpayPaymentId());
        assertTrue(pending.isSlotLocked());
        verify(bookingRepository).save(pending);
    }

    @Test
    void testWebhook_InvalidSignature_ThrowsBadRequest() {
        String rawPayload = "{\"event\": \"payment.captured\"}";
        String fakeSig = "invalid_signature_xyz";

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.handleWebhook(rawPayload, fakeSig)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid webhook signature"));
    }

    @Test
    void testDismissOrCancelPendingPayment_ReleasesSlot() {
        String ref = "TB-DISMISS-1";
        Booking pending = new Booking();
        pending.setBookingReference(ref);
        pending.setStatus("PENDING_PAYMENT");
        pending.setSlotLocked(true);
        pending.setCustomerId("cust-alex-1");

        when(bookingRepository.findByBookingReference(ref)).thenReturn(Optional.of(pending));

        bookingService.dismissOrCancelPendingPayment(ref, customerPrincipal);

        assertEquals("FAILED", pending.getStatus());
        assertEquals("FAILED", pending.getPaymentStatus());
        assertFalse(pending.isSlotLocked(), "Slot must be released immediately upon dismissal");
        verify(bookingRepository).save(pending);
    }

    @Test
    void testDoubleBookingPrevention_PendingReservationBlocksSameSlot() {
        BookingRequest req1 = new BookingRequest(
                "turf-apex",
                "Player One",
                "one@example.com",
                null,
                LocalDate.now().plusDays(2),
                LocalTime.of(16, 0),
                1
        );

        when(turfRepository.findById("turf-apex")).thenReturn(Optional.of(sampleTurf));
        // Slot is already locked by another user's pending reservation
        when(bookingRepository.existsBySlotKeysInAndSlotLockedTrue(anyCollection())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                bookingService.createBooking(req1, null)
        );

        assertEquals(409, ex.getStatusCode().value(), "Concurrent attempt on locked slot must throw 409 Conflict");
        assertTrue(ex.getReason().contains("already booked"));
    }

    @Test
    void testCancelBooking_PaidConfirmed_InitiatesRefund() {
        String ref = "TB-PAID-001";
        Booking paidBooking = new Booking();
        paidBooking.setBookingReference(ref);
        paidBooking.setTurfId("turf-apex");
        paidBooking.setTurfName("Apex Ground");
        paidBooking.setCustomerId("cust-alex-1");
        paidBooking.setBookingDate(LocalDate.now().plusDays(3));
        paidBooking.setStartTime(LocalTime.of(18, 0));
        paidBooking.setEndTime(LocalTime.of(19, 0));
        paidBooking.setStatus("CONFIRMED");
        paidBooking.setPaymentStatus("SUCCESS");
        paidBooking.setRazorpayPaymentId("pay_live_test_123");
        paidBooking.setTotalPrice(1000.0);
        paidBooking.setSlotLocked(true);

        Booking cancelledBooking = new Booking();
        cancelledBooking.setBookingReference(ref);
        cancelledBooking.setStatus("CANCELLED");
        cancelledBooking.setSlotLocked(false);
        cancelledBooking.setRefundStatus("REFUND_INITIATED");

        when(bookingRepository.findByBookingReference(ref)).thenReturn(Optional.of(paidBooking));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Booking.class)))
                .thenReturn(cancelledBooking);

        BookingResponse response = bookingService.cancelBooking(ref, customerPrincipal);

        assertNotNull(response);
        assertEquals("CANCELLED", response.getStatus());
        assertFalse(response.isSlotLocked());
    }
}
