-- V1__initial_schema.sql
-- Creates all tables for the Ticket Booking System
-- Author: Ticket Booking System

-- ============================================================
-- USERS TABLE
-- ============================================================
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- EVENTS TABLE
-- ============================================================
CREATE TABLE events (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    venue            VARCHAR(200) NOT NULL,
    event_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    total_seats      INTEGER NOT NULL CHECK (total_seats > 0),
    available_seats  INTEGER NOT NULL CHECK (available_seats >= 0),
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_available_seats CHECK (available_seats <= total_seats)
);

CREATE INDEX idx_events_event_date ON events(event_date);

-- ============================================================
-- SEATS TABLE
-- version column enables JPA @Version optimistic locking
-- status: AVAILABLE | LOCKED | BOOKED
-- ============================================================
CREATE TABLE seats (
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_number  VARCHAR(20) NOT NULL,
    category     VARCHAR(50) NOT NULL,
    price        NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_seat_event UNIQUE (event_id, seat_number),
    CONSTRAINT chk_seat_status CHECK (status IN ('AVAILABLE', 'LOCKED', 'BOOKED'))
);

CREATE INDEX idx_seats_event_id       ON seats(event_id);
CREATE INDEX idx_seats_event_status   ON seats(event_id, status);
CREATE INDEX idx_seats_status         ON seats(status);

-- ============================================================
-- BOOKINGS TABLE
-- idempotency_key enforces exactly-once booking semantics
-- booking_reference is user-facing reference number
-- ============================================================
CREATE TABLE bookings (
    id                BIGSERIAL PRIMARY KEY,
    booking_reference VARCHAR(20) NOT NULL,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    event_id          BIGINT NOT NULL REFERENCES events(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount      NUMERIC(10, 2) NOT NULL,
    idempotency_key   VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE,
    confirmed_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_bookings_reference     UNIQUE (booking_reference),
    CONSTRAINT uk_bookings_idempotency   UNIQUE (idempotency_key),
    CONSTRAINT chk_booking_status        CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED','FAILED'))
);

CREATE INDEX idx_bookings_user_id        ON bookings(user_id);
CREATE INDEX idx_bookings_event_id       ON bookings(event_id);
CREATE INDEX idx_bookings_status         ON bookings(status);
CREATE INDEX idx_bookings_expires_at     ON bookings(expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_bookings_idempotency    ON bookings(idempotency_key);

-- ============================================================
-- BOOKING_SEATS — join table (one booking → many seats)
-- ============================================================
CREATE TABLE booking_seats (
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    seat_id    BIGINT NOT NULL REFERENCES seats(id),
    CONSTRAINT uk_booking_seat UNIQUE (booking_id, seat_id)
);

CREATE INDEX idx_booking_seats_booking_id ON booking_seats(booking_id);
CREATE INDEX idx_booking_seats_seat_id    ON booking_seats(seat_id);

-- ============================================================
-- FLASH_SALES TABLE
-- total_tickets: how many tickets available in flash sale
-- sold_tickets:  how many have been sold (atomic counter)
-- ============================================================
CREATE TABLE flash_sales (
    id             BIGSERIAL PRIMARY KEY,
    event_id       BIGINT NOT NULL REFERENCES events(id),
    name           VARCHAR(200) NOT NULL,
    total_tickets  INTEGER NOT NULL CHECK (total_tickets > 0),
    sold_tickets   INTEGER NOT NULL DEFAULT 0 CHECK (sold_tickets >= 0),
    price          NUMERIC(10, 2) NOT NULL,
    starts_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sold_tickets CHECK (sold_tickets <= total_tickets),
    CONSTRAINT chk_flash_sale_dates CHECK (ends_at > starts_at)
);

CREATE INDEX idx_flash_sales_event_id ON flash_sales(event_id);
CREATE INDEX idx_flash_sales_active   ON flash_sales(active, starts_at, ends_at);

-- ============================================================
-- FLASH_SALE_PURCHASES TABLE
-- ============================================================
CREATE TABLE flash_sale_purchases (
    id              BIGSERIAL PRIMARY KEY,
    flash_sale_id   BIGINT NOT NULL REFERENCES flash_sales(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    booking_id      BIGINT REFERENCES bookings(id),
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    purchase_ref    VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_flash_purchase_ref UNIQUE (purchase_ref)
);

CREATE INDEX idx_flash_purchases_sale_id ON flash_sale_purchases(flash_sale_id);
CREATE INDEX idx_flash_purchases_user_id ON flash_sale_purchases(user_id);

-- ============================================================
-- IDEMPOTENCY_RECORDS TABLE
-- Stores processed request outcomes to handle duplicate POSTs
-- ============================================================
CREATE TABLE idempotency_records (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    response_body   TEXT,
    status_code     INTEGER NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_idempotency_key       ON idempotency_records(idempotency_key);
CREATE INDEX idx_idempotency_created   ON idempotency_records(created_at);
