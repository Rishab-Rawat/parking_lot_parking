package com.parking;

import com.parking.model.*;
import com.parking.model.Enums.SpotType;
import com.parking.model.Enums.VehicleSize;
import com.parking.service.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        ParkingLot lot = ParkingLot.getInstance();
        seedLot(lot);

        SpotAllocator allocator = SpotAllocator.defaultStrategy(lot);
        FeeCalculator feeCalculator = FeeCalculator.tieredStrategy();
        InMemoryRepository repository = new InMemoryRepository();

        EntryGateService entryGate = new EntryGateService(allocator, repository);
        ExitGateService exitGate = new ExitGateService(allocator, feeCalculator, repository, lot);

        System.out.println("=== Initial availability ===");
        printAvailability(lot);

        // --- Demo 1: simple sequential entry + exit with fee calc ---
        System.out.println("\n=== Sequential demo ===");
        Vehicle car1 = new Vehicle("V-1", "DL01AB1234", VehicleSize.CAR, "car");
        Ticket t1 = entryGate.checkIn(car1);

        // simulate the car having been parked for a while by backdating entry time
        backdateEntry(t1, 2 * 3600); // 2 hours ago

        exitGate.checkOut(t1.getTicketId(), "car", "UPI");

        // --- Demo 2: concurrent entries racing for a limited, exhaustible pool of spots ---
        // A motorcycle can use MOTORCYCLE, COMPACT, or LARGE spots (smallest-fit-first overflow).
        // The 2-floor lot has 6 + 8 + 4 = 18 such spots, so 20 concurrent motorcycles should
        // yield exactly 18 admits and 2 rejections once the lot fills up.
        System.out.println("\n=== Concurrency demo: 20 motorcycles racing for 18 compatible spots ===");
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int i = 0; i < 20; i++) {
            int idx = i;
            pool.submit(() -> {
                Vehicle bike = new Vehicle("MB-" + idx, "PLATE-" + idx, VehicleSize.MOTORCYCLE, "motorcycle");
                try {
                    Ticket t = entryGate.checkIn(bike);
                    success.incrementAndGet();
                } catch (NoSpotAvailableException e) {
                    failure.incrementAndGet();
                    System.out.println("[REJECTED] " + bike.getVehicleId() + " -> " + e.getMessage());
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.printf("%nResult: %d admitted, %d rejected (18 compatible spots existed)%n",
                success.get(), failure.get());

        System.out.println("\n=== Final availability ===");
        printAvailability(lot);
    }

    /** Builds a small 2-floor lot: 3 motorcycle, 4 compact, 2 large spots per floor. */
    private static void seedLot(ParkingLot lot) {
        for (int level = 0; level < 2; level++) {
            ParkingFloor floor = new ParkingFloor("FLOOR-" + level, level);
            int spotCounter = 1;
            for (int i = 0; i < 3; i++) {
                floor.addSpot(new ParkingSpot("SPOT-" + level + "-" + spotCounter, floor.getFloorId(),
                        "M" + spotCounter, SpotType.MOTORCYCLE));
                spotCounter++;
            }
            for (int i = 0; i < 4; i++) {
                floor.addSpot(new ParkingSpot("SPOT-" + level + "-" + spotCounter, floor.getFloorId(),
                        "C" + spotCounter, SpotType.COMPACT));
                spotCounter++;
            }
            for (int i = 0; i < 2; i++) {
                floor.addSpot(new ParkingSpot("SPOT-" + level + "-" + spotCounter, floor.getFloorId(),
                        "L" + spotCounter, SpotType.LARGE));
                spotCounter++;
            }
            lot.addFloor(floor);
        }
    }

    private static void printAvailability(ParkingLot lot) {
        for (ParkingFloor floor : lot.getFloors()) {
            System.out.printf("%s: MOTORCYCLE free=%d, COMPACT free=%d, LARGE free=%d%n",
                    floor.getFloorId(),
                    floor.countFree(SpotType.MOTORCYCLE),
                    floor.countFree(SpotType.COMPACT),
                    floor.countFree(SpotType.LARGE));
        }
    }

    private static void backdateEntry(Ticket ticket, long secondsAgo) {
        // Demo-only convenience so checkout shows a non-trivial fee. A real system
        // would inject a Clock rather than mutate entryTime after the fact.
        ticket.setEntryTime(ticket.getEntryTime().minusSeconds(secondsAgo));
    }
}
