package com.parking.model;

import com.parking.model.Enums.TicketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Ticket {
    private final String ticketId;
    private final String vehicleId;
    private final String spotId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;
    private BigDecimal amountDue;

    public Ticket(String ticketId, String vehicleId, String spotId, LocalDateTime entryTime) {
        this.ticketId = ticketId;
        this.vehicleId = vehicleId;
        this.spotId = spotId;
        this.entryTime = entryTime;
        this.status = TicketStatus.ACTIVE;
    }

    public String getTicketId() { return ticketId; }
    public String getVehicleId() { return vehicleId; }
    public String getSpotId() { return spotId; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }

    @Override
    public String toString() {
        return String.format("Ticket[%s, vehicle=%s, spot=%s, in=%s, out=%s, status=%s, due=%s]",
                ticketId, vehicleId, spotId, entryTime, exitTime, status, amountDue);
    }
}
