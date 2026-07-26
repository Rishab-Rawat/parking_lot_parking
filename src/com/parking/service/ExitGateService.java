package com.parking.service;

import com.parking.model.*;
import com.parking.model.Enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class ExitGateService {
    private final SpotAllocator allocator;
    private final FeeCalculator feeCalculator;
    private final InMemoryRepository repository;
    private final ParkingLot lot;

    public ExitGateService(SpotAllocator allocator, FeeCalculator feeCalculator,
                            InMemoryRepository repository, ParkingLot lot) {
        this.allocator = allocator;
        this.feeCalculator = feeCalculator;
        this.repository = repository;
        this.lot = lot;
    }

    public Payment checkOut(String ticketId, String vehicleType, String paymentMethod) {
        Ticket ticket = repository.findTicket(ticketId);
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new IllegalStateException("Ticket " + ticketId + " is not active (status=" + ticket.getStatus() + ")");
        }

        ticket.setExitTime(LocalDateTime.now());

        // In a full system the Vehicle would be looked up by ticket.getVehicleId();
        // passed in here directly to keep the demo self-contained.
        Vehicle vehicle = new Vehicle(ticket.getVehicleId(), "N/A", null, vehicleType);
        var amount = feeCalculator.calculate(ticket, vehicle);
        ticket.setAmountDue(amount);

        Payment payment = new Payment(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8),
                ticket.getTicketId(),
                amount,
                paymentMethod,
                LocalDateTime.now());
        repository.savePayment(payment);

        ticket.setStatus(TicketStatus.CLOSED);

        ParkingSpot spot = lot.findSpotById(ticket.getSpotId());
        allocator.release(spot);

        System.out.printf("[EXIT] %s -> %s (spot %s released)%n", ticket, payment, spot.getSpotId());
        return payment;
    }
}
