package com.turfbook.backend;

public class PaymentConfigResponse {

    private String keyId;
    private String currency;
    private int paymentWindowMinutes;
    private boolean isSimulator;

    public PaymentConfigResponse() {
    }

    public PaymentConfigResponse(String keyId, String currency, int paymentWindowMinutes) {
        this(keyId, currency, paymentWindowMinutes, false);
    }

    public PaymentConfigResponse(String keyId, String currency, int paymentWindowMinutes, boolean isSimulator) {
        this.keyId = keyId;
        this.currency = currency;
        this.paymentWindowMinutes = paymentWindowMinutes;
        this.isSimulator = isSimulator;
    }

    public boolean isSimulator() {
        return isSimulator;
    }

    public void setSimulator(boolean simulator) {
        isSimulator = simulator;
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
