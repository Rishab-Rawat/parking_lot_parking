package com.parking.service;

import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.Enums.TicketStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the `ticket` / `payment` tables. In a real system this would
 * be a JDBC/JPA repository; here it's kept in-memory so the demo runs
 * standalone. ConcurrentHashMap gives thread-safe reads/writes for the demo.
 */
public class InMemoryRepository {
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, Payment> payments = new ConcurrentHashMap<>();

    public void saveTicket(Ticket ticket) {
        tickets.put(ticket.getTicketId(), ticket);
    }

    public Ticket findActiveTicketForVehicle(String vehicleId) {
        return tickets.values().stream()
                .filter(t -> t.getVehicleId().equals(vehicleId) && t.getStatus() == TicketStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    public Ticket findTicket(String ticketId) {
        Ticket t = tickets.get(ticketId);
        if (t == null) throw new IllegalArgumentException("Unknown ticket: " + ticketId);
        return t;
    }

    public void savePayment(Payment payment) {
        payments.put(payment.getPaymentId(), payment);
    }
}
