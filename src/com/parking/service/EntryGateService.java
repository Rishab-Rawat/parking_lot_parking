package com.parking.service;

import com.parking.model.ParkingSpot;
import com.parking.model.Ticket;
import com.parking.model.Vehicle;

import java.time.LocalDateTime;
import java.util.UUID;

public class EntryGateService {
    private final SpotAllocator allocator;
    private final InMemoryRepository repository;

    public EntryGateService(SpotAllocator allocator, InMemoryRepository repository) {
        this.allocator = allocator;
        this.repository = repository;
    }

    /** Enforces "one active ticket per vehicle" and allocates a spot atomically. */
    public synchronized Ticket checkIn(Vehicle vehicle) {
        Ticket existing = repository.findActiveTicketForVehicle(vehicle.getVehicleId());
        if (existing != null) {
            throw new IllegalStateException(
                    "Vehicle " + vehicle.getVehicleId() + " already has an active ticket: " + existing.getTicketId());
        }

        ParkingSpot spot = allocator.allocate(vehicle);   // throws NoSpotAvailableException if full
        Ticket ticket = new Ticket(
                "TCKT-" + UUID.randomUUID().toString().substring(0, 8),
                vehicle.getVehicleId(),
                spot.getSpotId(),
                LocalDateTime.now());

        repository.saveTicket(ticket);
        System.out.printf("[ENTRY] %s -> %s%n", vehicle, spot);
        return ticket;
    }
}
