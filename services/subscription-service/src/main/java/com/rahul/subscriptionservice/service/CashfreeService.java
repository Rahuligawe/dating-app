package com.rahul.subscriptionservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CashfreeService {

    private final RestTemplate restTemplate;

    @Value("${cashfree.app.id}")
    private String appId;

    @Value("${cashfree.secret.key}")
    private String secretKey;

    @Value("${cashfree.env:TEST}")
    private String env;

    public CashfreeService(@Qualifier("externalRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String getBaseUrl() {
        return "TEST".equalsIgnoreCase(env)
                ? "https://sandbox.cashfree.com/pg"
                : "https://api.cashfree.com/pg";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", appId);
        headers.set("x-client-secret", secretKey);
        headers.set("x-api-version", "2025-01-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Creates a Cashfree order.
     * Returns: orderId, paymentSessionId, appId, amount, currency
     * Android SDK needs paymentSessionId to launch checkout.
     */
    public Map<String, Object> createOrder(int amountPaise, String orderId,
                                           String userId, String customerPhone) {
        double amountRupees = amountPaise / 100.0;

        Map<String, Object> body = new HashMap<>();
        body.put("order_id", orderId);
        body.put("order_amount", amountRupees);
        body.put("order_currency", "INR");

        Map<String, Object> customer = new HashMap<>();
        customer.put("customer_id", userId);
        customer.put("customer_phone", customerPhone != null ? customerPhone : "9999999999");
        body.put("customer_details", customer);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, buildHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    getBaseUrl() + "/orders", request, Map.class);

            Map<String, Object> resp = response.getBody();
            log.info("Cashfree order created: orderId={}", resp.get("order_id"));

            return Map.of(
                    "orderId",          resp.get("order_id"),
                    "cfOrderId",        String.valueOf(resp.get("cf_order_id")),
                    "paymentSessionId", resp.get("payment_session_id"),
                    "amount",           amountPaise,
                    "currency",         "INR",
                    "appId",            appId
            );
        } catch (Exception e) {
            log.error("Cashfree order creation failed: {}", e.getMessage());
            throw new RuntimeException("Payment order creation failed: " + e.getMessage());
        }
    }

    /**
     * Creates a recurring subscription (UPI AutoPay / card mandate) for PREMIUM/ULTRA plans.
     * Returns: subscriptionId, authLink (user must open to approve mandate)
     */
    public Map<String, Object> createRecurringSubscription(
            String subscriptionId, String planName, int amountRupees,
            String intervalType, int intervalCount, String userId,
            String customerPhone, String returnUrl) {

        Map<String, Object> plan = new HashMap<>();
        plan.put("plan_type", "PERIODIC");
        plan.put("plan_name", planName);
        plan.put("plan_currency", "INR");
        plan.put("plan_recurring_amount", amountRupees);
        plan.put("plan_interval_type", intervalType);  // MONTH or YEAR
        plan.put("plan_intervals", intervalCount);
        plan.put("plan_max_cycles", intervalType.equals("YEAR") ? 10 : 120);

        Map<String, Object> customer = new HashMap<>();
        customer.put("customer_id", userId.replaceAll("[^a-zA-Z0-9_-]", "").substring(0, Math.min(50, userId.length())));
        customer.put("customer_phone", customerPhone != null ? customerPhone : "9999999999");

        Map<String, Object> authDetails = new HashMap<>();
        authDetails.put("authorization_amount", 1);  // ₹1 auth charge

        // First charge starts now
        String firstChargeTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                .plusMinutes(5)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> body = new HashMap<>();
        body.put("subscription_id", subscriptionId);
        body.put("plan", plan);
        body.put("customer_details", customer);
        body.put("authorization_details", authDetails);
        body.put("subscription_first_charge_time", firstChargeTime);
        body.put("subscription_return_url", returnUrl);
        body.put("subscription_note", planName + " - AuraLink");

        HttpHeaders headers = buildHeaders();
        headers.set("x-api-version", "2023-08-01");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    getBaseUrl() + "/subscriptions", request, Map.class);
            Map<String, Object> resp = response.getBody();
            log.info("Cashfree subscription created: subId={} status={}", subscriptionId, resp.get("subscription_status"));
            return Map.of(
                    "subscriptionId", resp.getOrDefault("subscription_id", subscriptionId),
                    "authLink",       resp.getOrDefault("auth_link", ""),
                    "status",         resp.getOrDefault("subscription_status", "INITIALIZED")
            );
        } catch (Exception e) {
            log.error("Cashfree subscription creation failed: {}", e.getMessage());
            throw new RuntimeException("Subscription creation failed: " + e.getMessage());
        }
    }

    /**
     * Gets subscription status from Cashfree.
     */
    public String getSubscriptionStatus(String subscriptionId) {
        HttpHeaders headers = buildHeaders();
        headers.set("x-api-version", "2023-08-01");
        HttpEntity<?> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    getBaseUrl() + "/subscriptions/" + subscriptionId,
                    HttpMethod.GET, request, Map.class);
            Map<String, Object> resp = response.getBody();
            return (String) resp.getOrDefault("subscription_status", "UNKNOWN");
        } catch (Exception e) {
            log.error("Cashfree get subscription status failed: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * Verifies payment by fetching order status from Cashfree API.
     * Returns true if order_status == "PAID".
     */
    public boolean verifyPayment(String orderId) {
        HttpEntity<?> request = new HttpEntity<>(buildHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    getBaseUrl() + "/orders/" + orderId,
                    HttpMethod.GET, request, Map.class);

            Map<String, Object> resp = response.getBody();
            String status = (String) resp.get("order_status");
            log.info("Cashfree order {} status: {}", orderId, status);
            return "PAID".equals(status);

        } catch (Exception e) {
            log.error("Cashfree payment verification failed for order {}: {}", orderId, e.getMessage());
            return false;
        }
    }
}
