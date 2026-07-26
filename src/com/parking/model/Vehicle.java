package com.parking.model;

import com.parking.model.Enums.VehicleSize;

public class Vehicle {
    private final String vehicleId;
    private final String licensePlate;
    private final VehicleSize size;
    private final String vehicleType; // e.g. "motorcycle", "car", "bus"

    public Vehicle(String vehicleId, String licensePlate, VehicleSize size, String vehicleType) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.size = size;
        this.vehicleType = vehicleType;
    }

    public String getVehicleId() { return vehicleId; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleSize getSize() { return size; }
    public String getVehicleType() { return vehicleType; }

    @Override
    public String toString() {
        return String.format("Vehicle[%s, plate=%s, type=%s]", vehicleId, licensePlate, vehicleType);
    }
}
