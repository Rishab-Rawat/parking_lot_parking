package com.parking.service;

import com.parking.model.Ticket;
import com.parking.model.Vehicle;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface FeeCalculator {
    BigDecimal calculate(Ticket ticket, Vehicle vehicle);

    /** Factory for the default tiered hourly rate-card strategy. */
    static FeeCalculator tieredStrategy() {
        return new TieredFeeCalculator();
    }
}

/**
 * Tiered hourly rate card per vehicle type. Partial hours round up.
 * Mirrors the `rate_card` table from the DB design; swap this class
 * out (or make it read from a config/DB) without touching gate logic.
 */
class TieredFeeCalculator implements FeeCalculator {

    private record Tier(int startHour, Integer endHour, BigDecimal ratePerHour) {
        int hoursInTier() {
            return endHour == null ? Integer.MAX_VALUE : endHour - startHour;
        }
    }

    private static final Map<String, List<Tier>> RATE_CARD = Map.of(
            "motorcycle", List.of(
                    new Tier(0, 1, new BigDecimal("20")),
                    new Tier(1, 3, new BigDecimal("10")),
                    new Tier(3, null, new BigDecimal("8"))),
            "car", List.of(
                    new Tier(0, 1, new BigDecimal("40")),
                    new Tier(1, 3, new BigDecimal("30")),
                    new Tier(3, null, new BigDecimal("20"))),
            "bus", List.of(
                    new Tier(0, 1, new BigDecimal("100")),
                    new Tier(1, 3, new BigDecimal("80")),
                    new Tier(3, null, new BigDecimal("60")))
    );

    @Override
    public BigDecimal calculate(Ticket ticket, Vehicle vehicle) {
        Duration duration = Duration.between(ticket.getEntryTime(), ticket.getExitTime());
        long seconds = Math.max(duration.getSeconds(), 0);
        int hours = (int) Math.ceil(seconds / 3600.0);
        if (hours == 0) hours = 1; // minimum one billable hour, adjust per business rule

        List<Tier> tiers = RATE_CARD.getOrDefault(vehicle.getVehicleType().toLowerCase(), RATE_CARD.get("car"));

        int remaining = hours;
        BigDecimal total = BigDecimal.ZERO;
        for (Tier tier : tiers) {
            if (remaining <= 0) break;
            int span = Math.min(remaining, tier.hoursInTier());
            total = total.add(tier.ratePerHour().multiply(BigDecimal.valueOf(span)));
            remaining -= span;
        }
        return total;
    }
}
