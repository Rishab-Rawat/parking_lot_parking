package com.parking.service;

import com.parking.model.*;
import com.parking.model.Enums.SpotType;
import com.parking.model.Enums.VehicleSize;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public interface SpotAllocator {
    /** Finds and atomically claims a spot for the vehicle, or throws NoSpotAvailableException. */
    ParkingSpot allocate(Vehicle vehicle);

    /** Frees a previously claimed spot. */
    void release(ParkingSpot spot);

    /** Factory for the default smallest-fit / proximity-ordered strategy. */
    static SpotAllocator defaultStrategy(ParkingLot lot) {
        return new DefaultSpotAllocator(lot);
    }
}

/**
 * Default strategy: smallest-fit-first, floor-by-floor in proximity order
 * (floors are stored ground-up in ParkingLot). Uses ParkingSpot.tryClaim()
 * for an atomic, race-safe claim (see model.ParkingSpot).
 */
class DefaultSpotAllocator implements SpotAllocator {

    private static final Map<VehicleSize, List<SpotType>> COMPATIBILITY = new EnumMap<>(VehicleSize.class);
    static {
        COMPATIBILITY.put(VehicleSize.MOTORCYCLE, List.of(SpotType.MOTORCYCLE, SpotType.COMPACT, SpotType.LARGE));
        COMPATIBILITY.put(VehicleSize.CAR, List.of(SpotType.COMPACT, SpotType.LARGE));
        COMPATIBILITY.put(VehicleSize.BUS, List.of(SpotType.LARGE));
    }

    private final ParkingLot lot;

    DefaultSpotAllocator(ParkingLot lot) {
        this.lot = lot;
    }

    @Override
    public ParkingSpot allocate(Vehicle vehicle) {
        List<SpotType> candidateTypes = COMPATIBILITY.get(vehicle.getSize());

        for (SpotType type : candidateTypes) {
            for (ParkingFloor floor : lot.getFloors()) {
                for (ParkingSpot spot : floor.getSpotsByType(type)) {
                    if (spot.tryClaim()) {   // atomic CAS; only one thread can win a given spot
                        return spot;
                    }
                    // lost the race or spot wasn't free -> try next candidate
                }
            }
        }
        throw new NoSpotAvailableException(
                "No available spot for vehicle " + vehicle.getVehicleId() + " (" + vehicle.getSize() + ")");
    }

    @Override
    public void release(ParkingSpot spot) {
        spot.release();
    }
}
