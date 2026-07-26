package com.parking.model;

import com.parking.model.Enums.SpotType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ParkingFloor {
    private final String floorId;
    private final int level;
    private final List<ParkingSpot> spots = new CopyOnWriteArrayList<>();

    public ParkingFloor(String floorId, int level) {
        this.floorId = floorId;
        this.level = level;
    }

    public void addSpot(ParkingSpot spot) {
        spots.add(spot);
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }

    public List<ParkingSpot> getSpotsByType(SpotType type) {
        return spots.stream().filter(s -> s.getSpotType() == type).toList();
    }

    public String getFloorId() { return floorId; }
    public int getLevel() { return level; }

    public long countFree(SpotType type) {
        return spots.stream().filter(s -> s.getSpotType() == type && s.isFree()).count();
    }
}
