package com.turfbook.backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final BookingService bookingService;
    private final RazorpayService razorpayService;

    public PaymentController(BookingService bookingService, RazorpayService razorpayService) {
        this.bookingService = bookingService;
        this.razorpayService = razorpayService;
    }

    /**
     * Public endpoint returning public payment configuration (Razorpay Test Key ID & currency).
     * Secrets are never exposed.
     * GET /api/payments/config
     */
    @GetMapping("/config")
    public PaymentConfigResponse getPaymentConfig() {
        return new PaymentConfigResponse(
                razorpayService.getKeyId(),
                "INR",
                razorpayService.getPaymentWindowMinutes(),
                razorpayService.isTestSimulator()
        );
    }

    /**
     * Verifies Razorpay payment signature and confirms booking.
     * Accessible to guests and authenticated users.
     * POST /api/payments/verify
     */
    @PostMapping("/verify")
    public BookingResponse verifyPayment(@Valid @RequestBody PaymentVerificationRequest request,
                                         Authentication authentication) {
        UserPrincipal principal = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
            principal = p;
        }
        return bookingService.verifyPayment(request, principal);
    }

    /**
     * Webhook endpoint called by Razorpay to report asynchronous events.
     * Validates raw body HMAC signature against webhook secret.
     * POST /api/payments/webhook
     */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        bookingService.handleWebhook(rawBody, signatureHeader);
        return Map.of("status", "ok");
    }

    /**
     * Releases an active pending reservation when user cancels or dismisses checkout.
     * POST /api/payments/dismiss
     */
    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> dismissPayment(@RequestBody Map<String, String> body,
                                              Authentication authentication) {
        String reference = body.get("bookingReference");
        if (reference != null && !reference.isBlank()) {
            UserPrincipal principal = null;
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal p) {
                principal = p;
            }
            bookingService.dismissOrCancelPendingPayment(reference.trim(), principal);
        }
        return Map.of("status", "dismissed");
    }
}
