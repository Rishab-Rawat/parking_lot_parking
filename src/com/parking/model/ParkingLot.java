package com.parking.model;

import java.util.ArrayList;
import java.util.List;

/** Singleton root aggregate representing the whole facility. */
public class ParkingLot {
    private static final ParkingLot INSTANCE = new ParkingLot("LOT-1", "Downtown Parking Lot");

    private final String lotId;
    private final String name;
    private final List<ParkingFloor> floors = new ArrayList<>();

    private ParkingLot(String lotId, String name) {
        this.lotId = lotId;
        this.name = name;
    }

    public static ParkingLot getInstance() {
        return INSTANCE;
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public List<ParkingFloor> getFloors() {
        // floors are iterated ground-up, i.e. proximity order, on allocation
        return floors;
    }

    public String getLotId() { return lotId; }
    public String getName() { return name; }

    public ParkingSpot findSpotById(String spotId) {
        for (ParkingFloor floor : floors) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getSpotId().equals(spotId)) return spot;
            }
        }
        throw new IllegalArgumentException("Unknown spot: " + spotId);
    }
}
