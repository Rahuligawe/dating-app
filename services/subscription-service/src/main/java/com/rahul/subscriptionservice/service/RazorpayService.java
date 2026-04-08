package com.rahul.subscriptionservice.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.Map;

@Service
@Slf4j
public class RazorpayService {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    public Map<String, Object> createOrder(int amountPaise, String receipt) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            log.info("KEY ID: {}", keyId);
            log.info("KEY SECRET: {}", keySecret);

            JSONObject options = new JSONObject();
            options.put("amount", Integer.valueOf(amountPaise));
            options.put("currency", "INR");
            options.put("receipt", receipt);

            Order order = client.orders.create(options);

            log.info("Razorpay order created: {}", order.get("id").toString());

            return Map.of(
                    "orderId", order.get("id").toString(),
                    "amount", amountPaise,
                    "currency", "INR",
                    "keyId", keyId
            );

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new RuntimeException("Payment order creation failed: " + e.getMessage());
        }
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes("UTF-8"), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes("UTF-8"));

            // ✅ Correct hex conversion — BigInteger wala chhod do
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String expected = hexString.toString();
            boolean valid = expected.equalsIgnoreCase(signature);

            log.info("Expected signature: {}", expected);
            log.info("Received signature: {}", signature);
            log.info("Signature verification: {}", valid ? "VALID ✅" : "INVALID ❌");

            return valid;

        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}