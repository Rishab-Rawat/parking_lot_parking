# Smart Parking Lot System — Low-Level Design

## 1. Scope Recap

- Multi-floor lot, multiple spot types (motorcycle, compact/car, large/bus, EV, handicapped — extensible).
- Auto-assign a spot on entry based on vehicle size and current availability.
- Record check-in / check-out timestamps.
- Calculate fee on exit based on vehicle type and duration.
- Keep spot availability accurate under concurrent entries/exits.

---

## 2. High-Level Components

```
┌───────────────────┐     ┌───────────────────┐     ┌────────────────────┐
│  Entry Gate API   │────▶│  Spot Allocator    │────▶│  Availability Store │
└───────────────────┘     │  (Strategy)        │     │  (per-floor, per-   │
                           └───────────────────┘     │  size free lists)   │
                                                       └────────────────────┘
┌───────────────────┐     ┌───────────────────┐     ┌────────────────────┐
│  Exit Gate API    │────▶│  Fee Calculator    │────▶│  Payment Service    │
└───────────────────┘     │  (Strategy)        │     └────────────────────┘
                           └───────────────────┘
                    ┌───────────────────────┐
                    │   Ticket / Session     │
                    │   Repository (DB)      │
                    └───────────────────────┘
```

Each box maps to a service/module in the object model below. Entry and exit are two independent workflows that share the ticket and spot repositories.

---

## 3. Object Model (Class Design)

### 3.1 Core Enums

```python
class VehicleSize(Enum):
    MOTORCYCLE = 1
    CAR = 2
    BUS = 3          # occupies multiple contiguous large spots if needed

class SpotType(Enum):
    MOTORCYCLE = 1
    COMPACT = 2
    LARGE = 3
    EV_CHARGING = 4
    HANDICAPPED = 5

class SpotStatus(Enum):
    FREE = 1
    OCCUPIED = 2
    RESERVED = 3      # briefly held during allocation, before commit
    OUT_OF_SERVICE = 4

class TicketStatus(Enum):
    ACTIVE = 1
    PAID = 2
    CLOSED = 3
```

### 3.2 Domain Classes

```python
class Vehicle:
    vehicle_id: str
    license_plate: str
    size: VehicleSize
    vehicle_type: str        # "motorcycle" | "car" | "bus" | "ev"

class ParkingSpot:
    spot_id: str
    floor_id: str
    spot_number: str
    spot_type: SpotType
    status: SpotStatus
    version: int              # optimistic-locking token

    def can_fit(self, vehicle: Vehicle) -> bool: ...

class ParkingFloor:
    floor_id: str
    level: int
    spots: List[ParkingSpot]
    # per-size free-spot index maintained here (see §5)

class ParkingLot:                 # Singleton
    lot_id: str
    floors: List[ParkingFloor]
    capacity_by_type: Dict[SpotType, int]

class Ticket:
    ticket_id: str
    vehicle_id: str
    spot_id: str
    entry_time: datetime
    exit_time: Optional[datetime]
    status: TicketStatus
    amount_due: Optional[Decimal]

class Payment:
    payment_id: str
    ticket_id: str
    amount: Decimal
    method: str                # cash | card | upi | wallet
    paid_at: datetime
```

### 3.3 Services

```python
class SpotAllocator:               # Strategy pattern
    def allocate(self, vehicle: Vehicle) -> ParkingSpot: ...
    def release(self, spot: ParkingSpot) -> None: ...

class FeeCalculator:               # Strategy pattern
    def calculate(self, ticket: Ticket, vehicle: Vehicle) -> Decimal: ...

class EntryGateService:
    def check_in(self, vehicle: Vehicle) -> Ticket: ...

class ExitGateService:
    def check_out(self, ticket_id: str) -> Payment: ...
```

Using **Strategy** for allocation and fee calculation means new pricing schemes (weekday/weekend, festival surge) or new allocation rules (nearest-to-elevator, EV-first) can be swapped without touching gate logic.

---

## 4. Database Schema

```sql
CREATE TABLE parking_lot (
    lot_id        VARCHAR PRIMARY KEY,
    name          VARCHAR NOT NULL,
    address       VARCHAR
);

CREATE TABLE floor (
    floor_id      VARCHAR PRIMARY KEY,
    lot_id        VARCHAR REFERENCES parking_lot(lot_id),
    level         INT NOT NULL
);

CREATE TABLE parking_spot (
    spot_id       VARCHAR PRIMARY KEY,
    floor_id      VARCHAR REFERENCES floor(floor_id),
    spot_number   VARCHAR NOT NULL,
    spot_type     VARCHAR NOT NULL,       -- MOTORCYCLE/COMPACT/LARGE/EV/HANDICAPPED
    status        VARCHAR NOT NULL DEFAULT 'FREE',
    version       INT NOT NULL DEFAULT 0, -- optimistic lock
    UNIQUE (floor_id, spot_number)
);
CREATE INDEX idx_spot_lookup ON parking_spot (floor_id, spot_type, status);

CREATE TABLE vehicle (
    vehicle_id    VARCHAR PRIMARY KEY,
    license_plate VARCHAR UNIQUE NOT NULL,
    size          VARCHAR NOT NULL,
    vehicle_type  VARCHAR NOT NULL
);

CREATE TABLE ticket (
    ticket_id     VARCHAR PRIMARY KEY,
    vehicle_id    VARCHAR REFERENCES vehicle(vehicle_id),
    spot_id       VARCHAR REFERENCES parking_spot(spot_id),
    entry_time    TIMESTAMP NOT NULL,
    exit_time     TIMESTAMP,
    status        VARCHAR NOT NULL DEFAULT 'ACTIVE',
    amount_due    NUMERIC(10,2)
);
CREATE INDEX idx_ticket_active ON ticket (vehicle_id, status);

CREATE TABLE payment (
    payment_id    VARCHAR PRIMARY KEY,
    ticket_id     VARCHAR REFERENCES ticket(ticket_id),
    amount        NUMERIC(10,2) NOT NULL,
    method        VARCHAR NOT NULL,
    paid_at       TIMESTAMP NOT NULL
);

CREATE TABLE rate_card (
    vehicle_type  VARCHAR NOT NULL,
    tier_start_hr INT NOT NULL,      -- e.g. 0, 1, 3, 24
    tier_end_hr   INT,               -- null = open-ended
    rate_per_hour NUMERIC(10,2) NOT NULL,
    PRIMARY KEY (vehicle_type, tier_start_hr)
);
```

Notes:
- `parking_spot.version` supports optimistic locking so two entry requests can't both claim the same spot.
- `rate_card` externalizes pricing so tiers/rates can change without a deploy.
- One partial unique index (`(vehicle_id) WHERE status='ACTIVE'`, if the DB supports partial indexes) prevents a vehicle from holding two open tickets.

---

## 5. Spot Allocation Algorithm

**Goal:** O(1) average-case lookup of a free spot matching a vehicle's size, with correctness under concurrency.

### 5.1 In-memory index (per floor)

Maintain, per floor, a min-heap (or sorted set) of free spot IDs **per spot type**, ordered by distance-to-entrance (or simply spot_number) so the "closest" free spot is picked first:

```
free_spots[floor_id][spot_type] = MinHeap[(distance, spot_id)]
```

This index is a cache backed by the DB; it's rebuilt on startup from `parking_spot WHERE status='FREE'` and kept in sync on every allocate/release.

### 5.2 Size → spot type compatibility

```
MOTORCYCLE -> [MOTORCYCLE, COMPACT, LARGE]   # a bike can use a bigger spot
CAR        -> [COMPACT, LARGE]
BUS        -> [LARGE]                        # may require N contiguous LARGE spots
```

### 5.3 Allocation steps

```
def allocate(vehicle):
    for spot_type in compatibility[vehicle.size]:      # smallest-fit first
        for floor in floors_ordered_by_proximity():     # e.g. ground floor first
            spot = free_spots[floor.id][spot_type].peek()
            if spot is None:
                continue
            if try_claim(spot):        # see §6 for the atomic claim
                free_spots[floor.id][spot_type].pop()
                return spot
    raise NoSpotAvailableException()
```

- **Smallest-fit-first** avoids wasting a LARGE spot on a motorcycle when a MOTORCYCLE spot is free.
- Bus/multi-spot vehicles: allocator looks for a contiguous run of free LARGE spots (`spot_number` sequential within a floor) sized to the vehicle; this is a small sliding-window scan over the floor's LARGE free list.
- Complexity: O(log n) per floor scanned (heap pop), typically O(1) floors touched since it stops at the first hit.

### 5.4 Release (on exit)

```
def release(spot):
    spot.status = FREE
    free_spots[spot.floor_id][spot.spot_type].push(spot)
```

---

## 6. Concurrency Handling

Two race conditions matter: **double-allocation** of one spot to two vehicles, and **stale availability counts**.

1. **Atomic claim at the DB layer (source of truth).**
   Use a conditional update with optimistic locking:
   ```sql
   UPDATE parking_spot
   SET status = 'OCCUPIED', version = version + 1
   WHERE spot_id = :id AND status = 'FREE' AND version = :expected_version;
   ```
   If `rowcount == 0`, another request won it first — the allocator retries with the next candidate spot from the heap rather than blocking.

2. **In-memory index is a cache, DB is authoritative.**
   The heap gives a *candidate* quickly; the conditional UPDATE is the real lock. On a lost race, evict that spot from the local heap and try the next candidate — self-healing, no manual reconciliation needed.

3. **Row-level locking alternative** (if you prefer pessimistic): `SELECT ... FOR UPDATE SKIP LOCKED` on candidate spot rows lets multiple entry threads each grab a *different* free row without blocking each other — good fit for Postgres/MySQL under high entry throughput.

4. **Distributed deployment (multiple gate-service instances):** back the per-floor free-spot heaps with a shared store (Redis sorted sets) instead of local process memory, so all instances see the same candidate list. The DB conditional update remains the final arbiter regardless.

5. **Idempotency:** entry/exit API calls carry an idempotency key (e.g., ANPR camera event ID) so retried network calls don't create duplicate tickets or double-charge.

6. **One active ticket per vehicle:** enforced by the partial unique index in §4, preventing a duplicate check-in for a vehicle already inside.

---

## 7. Fee Calculation Logic

Tiered, per-vehicle-type pricing pulled from `rate_card`, e.g.:

| Vehicle type | 0–1 hr | 1–3 hr | 3–24 hr | Each additional day |
|---|---|---|---|---|
| Motorcycle | ₹20 flat | ₹10/hr | ₹8/hr | ₹100 |
| Car | ₹40 flat | ₹30/hr | ₹20/hr | ₹200 |
| Bus | ₹100 flat | ₹80/hr | ₹60/hr | ₹500 |

```python
def calculate(ticket, vehicle):
    duration = ticket.exit_time - ticket.entry_time
    hours = ceil(duration.total_seconds() / 3600)   # round up partial hour
    tiers = rate_card.for_type(vehicle.vehicle_type)
    remaining, total = hours, Decimal(0)
    for tier in tiers:                                # ascending tier_start_hr
        span = min(remaining, tier.hours_in_tier)
        total += span * tier.rate_per_hour
        remaining -= span
        if remaining <= 0:
            break
    return total
```

- Rounding rule (round up to next hour) is a business decision — flag it explicitly in the spec so finance and engineering agree.
- Extensible to flat day-rate caps, membership discounts, or lost-ticket penalty fees by adding strategies, not by editing this function.

---

## 8. Core Flows (Sequence)

### 8.1 Entry

```
Vehicle arrives → Entry Gate reads plate (ANPR) or ticket kiosk input
  → EntryGateService.check_in(vehicle)
      → SpotAllocator.allocate(vehicle)     # §5 + §6
      → Ticket created (status=ACTIVE, entry_time=now)
      → Availability index updated
  → Gate opens, ticket printed / QR issued
```

### 8.2 Exit

```
Vehicle at exit → Exit Gate scans ticket QR
  → ExitGateService.check_out(ticket_id)
      → load Ticket, set exit_time = now
      → FeeCalculator.calculate(ticket, vehicle)     # §7
      → Payment collected (cash/card/UPI)
      → Ticket.status = PAID → CLOSED
      → SpotAllocator.release(spot)                   # §5.4
  → Gate opens
```

---

## 9. API Sketch

```
POST /api/v1/entry           { license_plate, vehicle_type }        -> Ticket
GET  /api/v1/tickets/{id}                                            -> Ticket
POST /api/v1/exit/{ticket_id}/quote                                  -> amount_due
POST /api/v1/exit/{ticket_id}/pay   { method }                        -> Payment
GET  /api/v1/availability?floor_id=&spot_type=                       -> counts
```

---

## 10. Extensibility Notes

- **EV charging spots**: add `SpotType.EV_CHARGING` + a reservation flag so EV owners can pre-book; fee strategy adds a per-kWh component.
- **Reservations**: introduce a `RESERVED` status with a TTL hold before a physical vehicle arrives, expiring back to `FREE` if unused.
- **Monthly passes/subscriptions**: separate `Subscription` entity; fee calculator checks subscription first before falling back to hourly rate_card.
- **Analytics**: ticket + payment tables are append-mostly, making them straightforward to stream into a warehouse for occupancy/revenue reporting without touching the transactional path.

---

This design keeps allocation and pricing swappable (Strategy pattern), keeps the source of truth in the database with optimistic locking for correctness under load, and treats the in-memory/Redis free-spot index purely as a performance cache rather than a system of record.
