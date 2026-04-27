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
