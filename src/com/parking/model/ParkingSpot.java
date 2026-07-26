package com.parking.model;

import com.parking.model.Enums.SpotStatus;
import com.parking.model.Enums.SpotType;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A single parking spot. Status transitions are done via compare-and-set
 * so that two threads racing to claim the same spot can never both succeed
 * (mirrors the "conditional UPDATE ... WHERE status='FREE'" pattern from
 * the DB-backed design).
 */
public class ParkingSpot {
    private final String spotId;
    private final String floorId;
    private final String spotNumber;   // e.g. "A1", used for contiguous-spot lookup for buses
    private final SpotType spotType;
    private final AtomicReference<SpotStatus> status;

    public ParkingSpot(String spotId, String floorId, String spotNumber, SpotType spotType) {
        this.spotId = spotId;
        this.floorId = floorId;
        this.spotNumber = spotNumber;
        this.spotType = spotType;
        this.status = new AtomicReference<>(SpotStatus.FREE);
    }

    /** Atomically claim this spot. Returns true only if this thread won the race. */
    public boolean tryClaim() {
        return status.compareAndSet(SpotStatus.FREE, SpotStatus.OCCUPIED);
    }

    /** Release the spot back to the free pool. */
    public boolean release() {
        return status.compareAndSet(SpotStatus.OCCUPIED, SpotStatus.FREE);
    }

    public boolean isFree() {
        return status.get() == SpotStatus.FREE;
    }

    public String getSpotId() { return spotId; }
    public String getFloorId() { return floorId; }
    public String getSpotNumber() { return spotNumber; }
    public SpotType getSpotType() { return spotType; }
    public SpotStatus getStatus() { return status.get(); }

    @Override
    public String toString() {
        return String.format("Spot[%s, floor=%s, type=%s, status=%s]",
                spotId, floorId, spotType, status.get());
    }
}
