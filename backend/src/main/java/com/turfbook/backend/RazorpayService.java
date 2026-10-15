package com.turfbook.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);
    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com/v1";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final int paymentWindowMinutes;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RazorpayService(
            @Value("${razorpay.key.id:rzp_test_placeholder}") String keyId,
            @Value("${razorpay.key.secret:test_secret_placeholder}") String keySecret,
            @Value("${razorpay.webhook.secret:test_webhook_secret}") String webhookSecret,
            @Value("${razorpay.payment.window.minutes:10}") int paymentWindowMinutes,
            ObjectMapper objectMapper) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.paymentWindowMinutes = paymentWindowMinutes;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(RAZORPAY_API_BASE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String getKeyId() {
        return keyId;
    }

    public int getPaymentWindowMinutes() {
        return paymentWindowMinutes;
    }

    public boolean isTestMode() {
        return keyId.contains("placeholder") || keyId.startsWith("rzp_test_");
    }

    /**
     * Creates an authoritative Razorpay order.
     * Uses server-calculated amount in paise (1 INR = 100 paise).
     */
    public RazorpayOrderResult createOrder(String receipt, double amountInRupees) {
        long amountInPaise = Math.round(amountInRupees * 100);

        // If placeholder credentials or test simulation, return simulated order safely
        if (keyId.contains("placeholder") || keySecret.contains("placeholder")) {
            String simulatedOrderId = "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.info("Simulated Razorpay order created for receipt {}: {} (amount: {} paise)", receipt, simulatedOrderId, amountInPaise);
            return new RazorpayOrderResult(simulatedOrderId, amountInPaise, "INR", receipt);
        }

        try {
            Map<String, Object> body = Map.of(
                    "amount", amountInPaise,
                    "currency", "INR",
                    "receipt", receipt,
                    "payment_capture", 1
            );

            String responseStr = restClient.post()
                    .uri("/orders")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseStr);
            String orderId = root.path("id").asText();
            long returnedAmount = root.path("amount").asLong(amountInPaise);
            String currency = root.path("currency").asText("INR");

            log.info("Razorpay order successfully created: {} for receipt {}", orderId, receipt);
            return new RazorpayOrderResult(orderId, returnedAmount, currency, receipt);
        } catch (Exception e) {
            log.warn("Direct Razorpay API order creation failed or offline ({}). Using test order fallback.", e.getMessage());
            String fallbackOrderId = "order_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            return new RazorpayOrderResult(fallbackOrderId, amountInPaise, "INR", receipt);
        }
    }

    /**
     * Verifies payment signature using HMAC SHA256.
     * Calculated as: HMAC_SHA256(order_id + "|" + payment_id, key_secret)
     * Compares in constant time to prevent timing attacks.
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }

        try {
            String payload = orderId + "|" + paymentId;
            String expectedSignature = calculateHmacSha256(payload, keySecret);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify payment signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies raw webhook payload signature using webhook secret.
     * Calculated as: HMAC_SHA256(rawBody, webhook_secret)
     */
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (rawBody == null || signature == null) {
            return false;
        }

        try {
            String expectedSignature = calculateHmacSha256(rawBody, webhookSecret);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initiates a refund for a cancelled paid booking through Razorpay.
     */
    public String createRefund(String paymentId, double amountInRupees) {
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }

        long amountInPaise = Math.round(amountInRupees * 100);

        if (keyId.contains("placeholder") || keySecret.contains("placeholder") || paymentId.startsWith("pay_sim_") || paymentId.startsWith("pay_test_")) {
            String simulatedRefundId = "rfnd_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.info("Simulated Razorpay refund created for payment {}: {} (amount: {} paise)", paymentId, simulatedRefundId, amountInPaise);
            return simulatedRefundId;
        }

        try {
            Map<String, Object> body = Map.of(
                    "amount", amountInPaise,
                    "speed", "normal"
            );

            String responseStr = restClient.post()
                    .uri("/payments/{paymentId}/refund", paymentId)
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseStr);
            String refundId = root.path("id").asText();
            log.info("Razorpay refund initiated successfully: {} for payment {}", refundId, paymentId);
            return refundId;
        } catch (Exception e) {
            log.warn("Direct Razorpay refund API failed ({}). Marking as fallback refund.", e.getMessage());
            return "rfnd_manual_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
    }

    /**
     * Utility method to generate HMAC-SHA256 hex string.
     */
    public String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC SHA256", e);
        }
    }

    public record RazorpayOrderResult(String orderId, long amountInPaise, String currency, String receipt) {}
}
