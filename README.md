# Smart Parking Lot — Java Reference Implementation

A runnable Java implementation of the low-level design 

## Structure

```
src/com/parking/
├── Main.java                     entry point + demo scenarios
├── model/
│   ├── Enums.java                VehicleSize, SpotType, SpotStatus, TicketStatus
│   ├── Vehicle.java
│   ├── ParkingSpot.java          atomic claim/release via AtomicReference CAS
│   ├── ParkingFloor.java
│   ├── ParkingLot.java           singleton root aggregate
│   ├── Ticket.java
│   └── Payment.java
└── service/
    ├── SpotAllocator.java        strategy interface + DefaultSpotAllocator
    ├── FeeCalculator.java        strategy interface + TieredFeeCalculator
    ├── EntryGateService.java     check-in flow
    ├── ExitGateService.java      check-out + fee + payment flow
    ├── InMemoryRepository.java   stand-in for the ticket/payment DB tables
    └── NoSpotAvailableException.java
```

## Build & run

Requires JDK 17+ (uses `record` and `List.of`).

```bash
find src -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.parking.Main
```

## What the demo shows

1. **Sequential flow** — a car checks in, its entry time is backdated to
   simulate a 2-hour stay, then it checks out and the tiered fee is
   calculated and printed.
2. **Concurrency correctness** — 20 motorcycles check in simultaneously
   from a thread pool. The lot has exactly 18 spots a motorcycle is
   compatible with (MOTORCYCLE, COMPACT, and LARGE, via smallest-fit-first
   overflow). The run consistently admits exactly 18 and rejects 2, with
   no spot ever double-assigned — verifying the `AtomicReference.compareAndSet`
   claim in `ParkingSpot` is race-safe.

## Design notes / where this differs from a production system

- `InMemoryRepository` stands in for the `ticket` / `payment` tables from
  the DB schema in the design doc. Swap it for a JDBC/JPA repository to
  persist real data; the service layer doesn't need to change.
- `ParkingSpot.tryClaim()` uses an in-process `AtomicReference` CAS. In a
  multi-instance deployment, the equivalent is the conditional
  `UPDATE ... WHERE status='FREE' AND version=:v` from the design doc, or
  `SELECT ... FOR UPDATE SKIP LOCKED` — same idea, moved to the DB.
- `SpotAllocator` and `FeeCalculator` are both interfaces with a default
  strategy; add new implementations (e.g. `NearestToElevatorAllocator`,
  `WeekendSurgeFeeCalculator`) without touching `EntryGateService` /
  `ExitGateService`.
- `Ticket.entryTime` is mutable only to make the demo's backdating trick
  possible; a production system should inject a `Clock` instead of
  mutating timestamps after creation.
