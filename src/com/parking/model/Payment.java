package com.parking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Payment {
    private final String paymentId;
    private final String ticketId;
    private final BigDecimal amount;
    private final String method;
    private final LocalDateTime paidAt;

    public Payment(String paymentId, String ticketId, BigDecimal amount, String method, LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.method = method;
        this.paidAt = paidAt;
    }

    public String getPaymentId() { return paymentId; }
    public String getTicketId() { return ticketId; }
    public BigDecimal getAmount() { return amount; }
    public String getMethod() { return method; }
    public LocalDateTime getPaidAt() { return paidAt; }

    @Override
    public String toString() {
        return String.format("Payment[%s, ticket=%s, amount=%s, method=%s, paidAt=%s]",
                paymentId, ticketId, amount, method, paidAt);
    }
}
