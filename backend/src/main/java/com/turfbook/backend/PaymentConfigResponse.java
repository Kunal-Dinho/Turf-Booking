package com.turfbook.backend;

public class PaymentConfigResponse {

    private String keyId;
    private String currency;
    private int paymentWindowMinutes;

    public PaymentConfigResponse() {
    }

    public PaymentConfigResponse(String keyId, String currency, int paymentWindowMinutes) {
        this.keyId = keyId;
        this.currency = currency;
        this.paymentWindowMinutes = paymentWindowMinutes;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getPaymentWindowMinutes() {
        return paymentWindowMinutes;
    }

    public void setPaymentWindowMinutes(int paymentWindowMinutes) {
        this.paymentWindowMinutes = paymentWindowMinutes;
    }
}
