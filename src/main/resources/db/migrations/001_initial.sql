-- HotelSys : schéma complet, une seule création pour une base vide.
-- Montants en centimes DZD ; dates ISO, heures locales (Africa/Algiers), événements UTC.
PRAGMA foreign_keys=ON;

-- 1. Configuration et catalogue
CREATE TABLE hotel (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 150),
    category TEXT NOT NULL CHECK (length(trim(category)) BETWEEN 1 AND 80),
    bed_sharing_age INTEGER NOT NULL DEFAULT 6 CHECK (bed_sharing_age BETWEEN 0 AND 18),
    meal_child_age INTEGER NOT NULL DEFAULT 12 CHECK (meal_child_age BETWEEN 0 AND 18),
    breakfast_enabled INTEGER NOT NULL DEFAULT 1 CHECK (breakfast_enabled IN (0,1)),
    lunch_enabled INTEGER NOT NULL DEFAULT 1 CHECK (lunch_enabled IN (0,1)),
    dinner_enabled INTEGER NOT NULL DEFAULT 1 CHECK (dinner_enabled IN (0,1)),
    payment_sound INTEGER NOT NULL DEFAULT 1 CHECK (payment_sound IN (0,1))
) STRICT;

CREATE TABLE hotel_meal (
    meal TEXT PRIMARY KEY CHECK (meal IN ('BREAKFAST','LUNCH','DINNER')),
    price INTEGER NOT NULL CHECK (price>=0)
) STRICT;

CREATE TABLE room_model (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(name)) BETWEEN 1 AND 80),
    bed_count INTEGER NOT NULL CHECK (bed_count > 0),
    max_sharing_children INTEGER NOT NULL DEFAULT 1 CHECK (max_sharing_children >= 0),
    facilities TEXT NOT NULL DEFAULT '' CHECK (length(facilities) <= 2000),
    nightly_price INTEGER NOT NULL DEFAULT 0 CHECK (nightly_price >= 0)
) STRICT;

CREATE TABLE room (
    id INTEGER PRIMARY KEY,
    number TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(number)) BETWEEN 1 AND 20),
    model_id INTEGER NOT NULL REFERENCES room_model(id) ON DELETE RESTRICT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1))
) STRICT;

-- 2. Clients et équipe
CREATE TABLE person (
    id INTEGER PRIMARY KEY,
    last_name TEXT NOT NULL CHECK (length(trim(last_name)) BETWEEN 1 AND 100),
    first_name TEXT NOT NULL CHECK (length(trim(first_name)) BETWEEN 1 AND 100),
    birth_date TEXT NOT NULL CHECK (length(birth_date) = 10 AND date(birth_date, '+0 days') IS NOT NULL AND date(birth_date, '+0 days') = birth_date),
    sex TEXT NOT NULL CHECK (sex IN ('MALE', 'FEMALE')),
    phone TEXT NOT NULL DEFAULT '' CHECK (length(phone) <= 40),
    email TEXT NOT NULL DEFAULT '' CHECK (length(email) <= 200),
    identity_type TEXT NOT NULL DEFAULT 'NATIONAL_ID' CHECK (identity_type IN ('NATIONAL_ID','PASSPORT','RESIDENCE_PERMIT')),
    identity_number TEXT CHECK (identity_number IS NULL OR length(identity_number) BETWEEN 3 AND 20)
) STRICT;

CREATE TABLE app_user (
    id INTEGER PRIMARY KEY,
    display_name TEXT NOT NULL CHECK (length(trim(display_name)) BETWEEN 1 AND 100),
    username TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(username) BETWEEN 3 AND 40),
    password_hash TEXT NOT NULL CHECK (length(password_hash) > 50),
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'WORKER')),
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
) STRICT;

-- 3. Réservations et prix acceptés
CREATE TABLE reservation (
    id INTEGER PRIMARY KEY,
    holder_id INTEGER NOT NULL REFERENCES person(id) ON DELETE RESTRICT,
    created_by INTEGER NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    booked_at TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '' CHECK (length(notes) <= 2000)
) STRICT;

CREATE TABLE reservation_room (
    id INTEGER PRIMARY KEY,
    reservation_id INTEGER NOT NULL REFERENCES reservation(id) ON DELETE RESTRICT,
    room_id INTEGER NOT NULL REFERENCES room(id) ON DELETE RESTRICT,
    arrival TEXT NOT NULL CHECK (length(arrival) = 10 AND date(arrival, '+0 days') IS NOT NULL AND date(arrival, '+0 days') = arrival),
    departure TEXT NOT NULL CHECK (length(departure) = 10 AND date(departure, '+0 days') IS NOT NULL AND date(departure, '+0 days') = departure),
    status TEXT NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED')),
    nightly_price INTEGER NOT NULL CHECK (nightly_price >= 0),
    bed_count INTEGER NOT NULL CHECK (bed_count > 0),
    max_sharing_children INTEGER NOT NULL CHECK (max_sharing_children >= 0),
    bed_sharing_age INTEGER NOT NULL CHECK (bed_sharing_age BETWEEN 0 AND 18),
    meal_child_age INTEGER NOT NULL CHECK (meal_child_age BETWEEN 0 AND 18),
    family_basis TEXT CHECK (family_basis IN ('SPOUSES', 'BLOOD_RELATIVES', 'MIXED_FAMILY')),
    family_document TEXT CHECK (family_document IN ('FAMILY_BOOK', 'MARRIAGE_CERTIFICATE', 'BIRTH_CERTIFICATE', 'OTHER')),
    family_note TEXT NOT NULL DEFAULT '' CHECK (length(family_note) <= 500),
    verified_by INTEGER REFERENCES app_user(id) ON DELETE RESTRICT,
    verified_at TEXT,
    checked_in_at TEXT,
    checked_out_at TEXT,
    arrival_time TEXT NOT NULL DEFAULT '14:00' CHECK (arrival_time GLOB '[0-2][0-9]:[0-5][0-9]' AND arrival_time >= '08:00' AND arrival_time <= '22:00'),
    departure_time TEXT NOT NULL DEFAULT '11:00' CHECK (departure_time GLOB '[0-2][0-9]:[0-5][0-9]' AND departure_time >= '08:00' AND departure_time <= '22:00'),
    CHECK (departure || 'T' || departure_time > arrival || 'T' || arrival_time),
    CHECK ((family_basis IS NULL AND family_document IS NULL AND verified_by IS NULL AND verified_at IS NULL) OR (family_basis IS NOT NULL AND family_document IS NOT NULL AND verified_by IS NOT NULL AND verified_at IS NOT NULL)),
    CHECK (family_document IS NULL OR family_document <> 'OTHER' OR length(trim(family_note)) > 0),
    CHECK ((status = 'CHECKED_IN' AND checked_in_at IS NOT NULL AND checked_out_at IS NULL) OR (status = 'CHECKED_OUT' AND checked_in_at IS NOT NULL AND checked_out_at IS NOT NULL) OR (status IN ('RESERVED', 'CANCELLED') AND checked_in_at IS NULL AND checked_out_at IS NULL))
) STRICT;

CREATE TABLE reservation_guest (
    reservation_room_id INTEGER NOT NULL REFERENCES reservation_room(id) ON DELETE RESTRICT,
    person_id INTEGER NOT NULL REFERENCES person(id) ON DELETE RESTRICT,
    requires_bed INTEGER NOT NULL CHECK (requires_bed IN (0, 1)),
    meal_tariff TEXT NOT NULL CHECK (meal_tariff IN ('ADULT', 'CHILD')),
    PRIMARY KEY (reservation_room_id, person_id)
) STRICT;

CREATE TABLE reservation_room_meal (
    reservation_room_id INTEGER NOT NULL REFERENCES reservation_room(id) ON DELETE RESTRICT,
    meal TEXT NOT NULL CHECK (meal IN ('BREAKFAST', 'LUNCH', 'DINNER')),
    adult_price INTEGER NOT NULL CHECK (adult_price >= 0),
    child_price INTEGER NOT NULL CHECK (child_price >= 0),
    PRIMARY KEY (reservation_room_id, meal)
) STRICT;

-- 4. Caisse et journal des clés
CREATE TABLE cash_session (
    id INTEGER PRIMARY KEY,
    opened_by INTEGER NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    opened_at TEXT NOT NULL,
    opening_float INTEGER NOT NULL CHECK (opening_float >= 0),
    closed_by INTEGER REFERENCES app_user(id) ON DELETE RESTRICT,
    closed_at TEXT,
    counted_cash INTEGER CHECK (counted_cash >= 0),
    closing_note TEXT NOT NULL DEFAULT '' CHECK (length(closing_note) <= 1000),
    CHECK ((closed_at IS NULL AND closed_by IS NULL AND counted_cash IS NULL) OR (closed_at IS NOT NULL AND closed_by IS NOT NULL AND counted_cash IS NOT NULL))
) STRICT;

CREATE TABLE cash_entry (
    id INTEGER PRIMARY KEY,
    session_id INTEGER NOT NULL REFERENCES cash_session(id) ON DELETE RESTRICT,
    actor_id INTEGER NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    recorded_at TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('PAYMENT', 'REFUND', 'EXPENSE')),
    amount INTEGER NOT NULL CHECK (amount > 0),
    reservation_id INTEGER REFERENCES reservation(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL DEFAULT '' CHECK (length(reason) <= 1000),
    CHECK ((kind IN ('PAYMENT', 'REFUND') AND reservation_id IS NOT NULL) OR (kind = 'EXPENSE' AND reservation_id IS NULL)),
    CHECK (kind = 'PAYMENT' OR length(trim(reason)) > 0)
) STRICT;

CREATE TABLE key_event (
    id INTEGER PRIMARY KEY,
    reservation_room_id INTEGER NOT NULL REFERENCES reservation_room(id),
    person_id INTEGER NOT NULL REFERENCES person(id),
    actor_id INTEGER NOT NULL REFERENCES app_user(id),
    recorded_at TEXT NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('TAKE','GIVE')),
    final_return INTEGER NOT NULL DEFAULT 0 CHECK (final_return IN (0,1)),
    CHECK (final_return=0 OR action='TAKE')
) STRICT;

-- Valeurs initiales : aucun compte ni client de démonstration.
INSERT INTO hotel(id,name,category) VALUES (1,'Mon hôtel','Non classé');
INSERT INTO hotel_meal(meal,price) VALUES ('BREAKFAST',0),('LUNCH',0),('DINNER',0);

-- 5. Index
CREATE INDEX ix_room_model ON room(model_id);
CREATE INDEX ix_person_name ON person(last_name COLLATE NOCASE, first_name COLLATE NOCASE);
CREATE INDEX ix_reservation_holder ON reservation(holder_id, booked_at);
CREATE INDEX ix_reservation_creator ON reservation(created_by);
CREATE INDEX ix_guest_person ON reservation_guest(person_id, reservation_room_id);
CREATE UNIQUE INDEX ux_one_open_session ON cash_session((1)) WHERE closed_at IS NULL;
CREATE INDEX ix_session_opener ON cash_session(opened_by, opened_at);
CREATE INDEX ix_session_closer ON cash_session(closed_by) WHERE closed_by IS NOT NULL;
CREATE INDEX ix_cash_session_time ON cash_entry(session_id, recorded_at);
CREATE INDEX ix_cash_reservation ON cash_entry(reservation_id) WHERE reservation_id IS NOT NULL;
CREATE INDEX ix_cash_actor ON cash_entry(actor_id);
CREATE INDEX ix_cash_time ON cash_entry(recorded_at);
CREATE UNIQUE INDEX ux_person_identity ON person(identity_type,identity_number) WHERE identity_number IS NOT NULL;
CREATE UNIQUE INDEX ux_single_admin ON app_user(role) WHERE role='ADMIN';
CREATE INDEX ix_reserved_room_availability ON reservation_room(room_id, arrival, departure) WHERE status <> 'CANCELLED';
CREATE INDEX ix_reserved_room_reservation ON reservation_room(reservation_id);
CREATE INDEX ix_reserved_room_arrivals ON reservation_room(status, arrival);
CREATE INDEX ix_reserved_room_verifier ON reservation_room(verified_by) WHERE verified_by IS NOT NULL;
CREATE INDEX ix_key_stay ON key_event(reservation_room_id,id);
CREATE INDEX ix_key_person ON key_event(person_id,id);

-- 6. Totaux calculés
CREATE VIEW room_totals AS
SELECT r.id AS reservation_room_id, r.reservation_id, r.status,
    max(1, CAST(julianday(r.departure) - julianday(r.arrival) AS INTEGER)) AS nights,
    r.nightly_price,
    COALESCE((SELECT sum(CASE g.meal_tariff WHEN 'CHILD' THEN m.child_price ELSE m.adult_price END)
        FROM reservation_guest g JOIN reservation_room_meal m ON m.reservation_room_id = g.reservation_room_id
        WHERE g.reservation_room_id = r.id), 0) AS daily_meal_total,
    max(1, CAST(julianday(r.departure) - julianday(r.arrival) AS INTEGER)) *
        (r.nightly_price + COALESCE((SELECT sum(CASE g.meal_tariff WHEN 'CHILD' THEN m.child_price ELSE m.adult_price END)
        FROM reservation_guest g JOIN reservation_room_meal m ON m.reservation_room_id = g.reservation_room_id
        WHERE g.reservation_room_id = r.id), 0)) AS total
FROM reservation_room r;

CREATE VIEW reservation_balances AS
SELECT b.id AS reservation_id,
    COALESCE((SELECT sum(t.total) FROM room_totals t WHERE t.reservation_id = b.id AND t.status <> 'CANCELLED'), 0) AS total_due,
    COALESCE((SELECT sum(CASE e.kind WHEN 'PAYMENT' THEN e.amount ELSE -e.amount END)
        FROM cash_entry e WHERE e.reservation_id = b.id), 0) AS net_paid,
    COALESCE((SELECT sum(t.total) FROM room_totals t WHERE t.reservation_id = b.id AND t.status <> 'CANCELLED'), 0)
    - COALESCE((SELECT sum(CASE e.kind WHEN 'PAYMENT' THEN e.amount ELSE -e.amount END)
        FROM cash_entry e WHERE e.reservation_id = b.id), 0) AS balance
FROM reservation b;

CREATE VIEW cash_session_totals AS
SELECT s.id AS session_id,
    s.opening_float + COALESCE((SELECT sum(CASE e.kind WHEN 'PAYMENT' THEN e.amount ELSE -e.amount END)
        FROM cash_entry e WHERE e.session_id = s.id), 0) AS expected_cash,
    s.counted_cash - (s.opening_float + COALESCE((SELECT sum(CASE e.kind WHEN 'PAYMENT' THEN e.amount ELSE -e.amount END)
        FROM cash_entry e WHERE e.session_id = s.id), 0)) AS discrepancy
FROM cash_session s;

-- Le départ constaté fixe la disponibilité. Les dates réservées restent la base de la facturation.
CREATE VIEW stay_timing AS
SELECT id, room_id, starts_at, ends_at, datetime(ends_at, '+3 hours', 'subsec') AS room_ready_at
FROM (
    SELECT id, room_id, datetime(arrival || 'T' || arrival_time) AS starts_at,
        CASE WHEN status = 'CHECKED_OUT' THEN datetime(checked_out_at, '+1 hour', 'subsec')
             ELSE datetime(departure || 'T' || departure_time, 'subsec') END AS ends_at
    FROM reservation_room
    WHERE status <> 'CANCELLED'
);

-- 7. Garde-fous et historique immuable
CREATE TRIGGER keep_last_admin_update BEFORE UPDATE OF role, active ON app_user
WHEN OLD.role = 'ADMIN' AND OLD.active = 1 AND (NEW.role <> 'ADMIN' OR NEW.active = 0)
AND NOT EXISTS (SELECT 1 FROM app_user WHERE id <> OLD.id AND role = 'ADMIN' AND active = 1)
BEGIN SELECT RAISE(ABORT, 'last_active_admin'); END;

CREATE TRIGGER keep_last_admin_delete BEFORE DELETE ON app_user
WHEN OLD.role = 'ADMIN' AND OLD.active = 1
AND NOT EXISTS (SELECT 1 FROM app_user WHERE id <> OLD.id AND role = 'ADMIN' AND active = 1)
BEGIN SELECT RAISE(ABORT, 'last_active_admin'); END;

CREATE TRIGGER identity_clear_check AFTER UPDATE OF birth_date, sex ON person
WHEN NEW.birth_date <> OLD.birth_date OR NEW.sex <> OLD.sex
BEGIN
    UPDATE reservation_room SET family_basis = NULL, family_document = NULL, family_note = '', verified_by = NULL, verified_at = NULL
    WHERE status = 'RESERVED' AND id IN (SELECT reservation_room_id FROM reservation_guest WHERE person_id = NEW.id);
END;

CREATE TRIGGER closed_session_immutable BEFORE UPDATE ON cash_session
WHEN OLD.closed_at IS NOT NULL
BEGIN SELECT RAISE(ABORT, 'closed_session'); END;

CREATE TRIGGER session_opening_immutable BEFORE UPDATE ON cash_session
WHEN NEW.opened_by <> OLD.opened_by OR NEW.opened_at <> OLD.opened_at OR NEW.opening_float <> OLD.opening_float
BEGIN SELECT RAISE(ABORT, 'session_opening_immutable'); END;

CREATE TRIGGER session_no_delete BEFORE DELETE ON cash_session
BEGIN SELECT RAISE(ABORT, 'cash_history_immutable'); END;

CREATE TRIGGER cash_entry_open_session BEFORE INSERT ON cash_entry
WHEN NOT EXISTS (SELECT 1 FROM cash_session WHERE id = NEW.session_id AND closed_at IS NULL)
BEGIN SELECT RAISE(ABORT, 'cash_session_not_open'); END;

CREATE TRIGGER cash_entry_no_update BEFORE UPDATE ON cash_entry
BEGIN SELECT RAISE(ABORT, 'cash_history_immutable'); END;

CREATE TRIGGER cash_entry_no_delete BEFORE DELETE ON cash_entry
BEGIN SELECT RAISE(ABORT, 'cash_history_immutable'); END;

CREATE TRIGGER room_available_insert BEFORE INSERT ON reservation_room
WHEN NEW.status <> 'CANCELLED'
BEGIN
    SELECT RAISE(ABORT, 'inactive_room') WHERE NOT EXISTS (SELECT 1 FROM room WHERE id = NEW.room_id AND active = 1);
    SELECT RAISE(ABORT, 'room_overlap') WHERE EXISTS (
        SELECT 1 FROM stay_timing WHERE room_id = NEW.room_id
        AND starts_at < datetime(NEW.departure || 'T' || NEW.departure_time, '+3 hours')
        AND room_ready_at > datetime(NEW.arrival || 'T' || NEW.arrival_time, 'subsec')
    );
END;

CREATE TRIGGER room_available_update BEFORE UPDATE OF room_id, arrival, departure, arrival_time, departure_time, status ON reservation_room
WHEN NEW.status IN ('RESERVED', 'CHECKED_IN')
BEGIN
    SELECT RAISE(ABORT, 'inactive_room')
    WHERE (NEW.room_id <> OLD.room_id OR OLD.status = 'CANCELLED')
    AND NOT EXISTS (SELECT 1 FROM room WHERE id = NEW.room_id AND active = 1);
    SELECT RAISE(ABORT, 'room_overlap') WHERE EXISTS (
        SELECT 1 FROM stay_timing WHERE id <> OLD.id AND room_id = NEW.room_id
        AND starts_at < datetime(NEW.departure || 'T' || NEW.departure_time, '+3 hours')
        AND room_ready_at > CASE WHEN OLD.status = 'RESERVED' AND NEW.status = 'CHECKED_IN'
            THEN datetime(NEW.checked_in_at, '+1 hour', 'subsec')
            ELSE datetime(NEW.arrival || 'T' || NEW.arrival_time, 'subsec') END
    );
END;

CREATE TRIGGER immutable_stay BEFORE UPDATE ON reservation_room
WHEN OLD.status IN ('CHECKED_IN', 'CHECKED_OUT')
AND (NEW.room_id <> OLD.room_id OR NEW.reservation_id <> OLD.reservation_id
    OR NEW.arrival <> OLD.arrival OR NEW.arrival_time <> OLD.arrival_time
    OR NEW.nightly_price <> OLD.nightly_price OR NEW.bed_count <> OLD.bed_count
    OR NEW.max_sharing_children <> OLD.max_sharing_children
    OR NEW.bed_sharing_age <> OLD.bed_sharing_age OR NEW.meal_child_age <> OLD.meal_child_age)
BEGIN SELECT RAISE(ABORT, 'stay_already_started'); END;

CREATE TRIGGER room_status_transition BEFORE UPDATE OF status ON reservation_room
WHEN NEW.status <> OLD.status AND NOT (
    (OLD.status = 'RESERVED' AND NEW.status IN ('CHECKED_IN', 'CANCELLED')) OR
    (OLD.status = 'CHECKED_IN' AND NEW.status = 'CHECKED_OUT') OR
    (OLD.status = 'CANCELLED' AND NEW.status = 'RESERVED'))
BEGIN SELECT RAISE(ABORT, 'invalid_stay_transition'); END;

CREATE TRIGGER guest_insert_guard BEFORE INSERT ON reservation_guest
BEGIN
    SELECT RAISE(ABORT, 'stay_not_reserved') WHERE (SELECT status FROM reservation_room WHERE id = NEW.reservation_room_id) <> 'RESERVED';
    SELECT RAISE(ABORT, 'bed_capacity') WHERE NEW.requires_bed = 1 AND
        (SELECT count(*) FROM reservation_guest WHERE reservation_room_id = NEW.reservation_room_id AND requires_bed = 1)
        >= (SELECT bed_count FROM reservation_room WHERE id = NEW.reservation_room_id);
    SELECT RAISE(ABORT, 'sharing_capacity') WHERE NEW.requires_bed = 0 AND
        (SELECT count(*) FROM reservation_guest WHERE reservation_room_id = NEW.reservation_room_id AND requires_bed = 0)
        >= (SELECT max_sharing_children FROM reservation_room WHERE id = NEW.reservation_room_id);
    SELECT RAISE(ABORT, 'guest_overlap') WHERE EXISTS (
        SELECT 1 FROM reservation_guest g JOIN stay_timing r ON r.id = g.reservation_room_id
        JOIN reservation_room target ON target.id = NEW.reservation_room_id
        WHERE g.person_id = NEW.person_id
        AND r.starts_at < datetime(target.departure || 'T' || target.departure_time) AND r.ends_at > datetime(target.arrival || 'T' || target.arrival_time, 'subsec')
    );
END;

CREATE TRIGGER guest_update_guard BEFORE UPDATE ON reservation_guest
BEGIN
    SELECT RAISE(ABORT, 'guest_key_immutable') WHERE NEW.reservation_room_id <> OLD.reservation_room_id OR NEW.person_id <> OLD.person_id;
    SELECT RAISE(ABORT, 'stay_not_reserved') WHERE (SELECT status FROM reservation_room WHERE id = OLD.reservation_room_id) <> 'RESERVED';
    SELECT RAISE(ABORT, 'bed_capacity') WHERE NEW.requires_bed = 1 AND
        (SELECT count(*) FROM reservation_guest WHERE reservation_room_id = OLD.reservation_room_id AND person_id <> OLD.person_id AND requires_bed = 1)
        >= (SELECT bed_count FROM reservation_room WHERE id = OLD.reservation_room_id);
    SELECT RAISE(ABORT, 'sharing_capacity') WHERE NEW.requires_bed = 0 AND
        (SELECT count(*) FROM reservation_guest WHERE reservation_room_id = OLD.reservation_room_id AND person_id <> OLD.person_id AND requires_bed = 0)
        >= (SELECT max_sharing_children FROM reservation_room WHERE id = OLD.reservation_room_id);
END;

CREATE TRIGGER guest_delete_guard BEFORE DELETE ON reservation_guest
WHEN (SELECT status FROM reservation_room WHERE id = OLD.reservation_room_id) <> 'RESERVED'
BEGIN SELECT RAISE(ABORT, 'stay_not_reserved'); END;

CREATE TRIGGER guest_insert_clear_check AFTER INSERT ON reservation_guest
BEGIN UPDATE reservation_room SET family_basis = NULL, family_document = NULL, family_note = '', verified_by = NULL, verified_at = NULL WHERE id = NEW.reservation_room_id; END;

CREATE TRIGGER guest_update_clear_check AFTER UPDATE ON reservation_guest
BEGIN UPDATE reservation_room SET family_basis = NULL, family_document = NULL, family_note = '', verified_by = NULL, verified_at = NULL WHERE id = NEW.reservation_room_id; END;

CREATE TRIGGER guest_delete_clear_check AFTER DELETE ON reservation_guest
BEGIN UPDATE reservation_room SET family_basis = NULL, family_document = NULL, family_note = '', verified_by = NULL, verified_at = NULL WHERE id = OLD.reservation_room_id; END;

CREATE TRIGGER guest_dates_overlap BEFORE UPDATE OF arrival, departure, arrival_time, departure_time, status ON reservation_room
WHEN NEW.status IN ('RESERVED', 'CHECKED_IN')
BEGIN
    SELECT RAISE(ABORT, 'guest_overlap') WHERE EXISTS (
        SELECT 1 FROM reservation_guest own
        JOIN reservation_guest other ON other.person_id = own.person_id AND other.reservation_room_id <> OLD.id
        JOIN stay_timing r ON r.id = other.reservation_room_id
        WHERE own.reservation_room_id = OLD.id
        AND r.starts_at < datetime(NEW.departure || 'T' || NEW.departure_time)
        AND r.ends_at > CASE WHEN OLD.status = 'RESERVED' AND NEW.status = 'CHECKED_IN'
            THEN datetime(NEW.checked_in_at, '+1 hour', 'subsec')
            ELSE datetime(NEW.arrival || 'T' || NEW.arrival_time, 'subsec') END
    );
END;

CREATE TRIGGER dates_clear_check AFTER UPDATE OF arrival, departure, arrival_time, departure_time ON reservation_room
WHEN NEW.status='RESERVED' AND (NEW.arrival <> OLD.arrival OR NEW.departure <> OLD.departure OR NEW.arrival_time<>OLD.arrival_time OR NEW.departure_time<>OLD.departure_time)
BEGIN UPDATE reservation_room SET family_basis = NULL, family_document = NULL, family_note = '', verified_by = NULL, verified_at = NULL WHERE id = NEW.id; END;

CREATE TRIGGER person_identity_guard BEFORE UPDATE OF birth_date, sex ON person
WHEN (NEW.birth_date <> OLD.birth_date OR NEW.sex <> OLD.sex) AND EXISTS (
    SELECT 1 FROM reservation_guest g JOIN reservation_room r ON r.id = g.reservation_room_id
    WHERE g.person_id = OLD.id AND r.status = 'CHECKED_IN'
)
BEGIN SELECT RAISE(ABORT, 'guest_currently_checked_in'); END;

CREATE TRIGGER meal_insert_guard BEFORE INSERT ON reservation_room_meal
WHEN (SELECT status FROM reservation_room WHERE id = NEW.reservation_room_id) <> 'RESERVED'
BEGIN SELECT RAISE(ABORT, 'stay_not_reserved'); END;

CREATE TRIGGER meal_update_guard BEFORE UPDATE ON reservation_room_meal
WHEN NEW.reservation_room_id <> OLD.reservation_room_id OR (SELECT status FROM reservation_room WHERE id = OLD.reservation_room_id) <> 'RESERVED'
BEGIN SELECT RAISE(ABORT, 'stay_not_reserved'); END;

CREATE TRIGGER meal_delete_guard BEFORE DELETE ON reservation_room_meal
WHEN (SELECT status FROM reservation_room WHERE id = OLD.reservation_room_id) <> 'RESERVED'
BEGIN SELECT RAISE(ABORT, 'stay_not_reserved'); END;

CREATE TRIGGER check_in_guard BEFORE UPDATE OF status ON reservation_room
WHEN NEW.status = 'CHECKED_IN' AND OLD.status <> 'CHECKED_IN'
BEGIN
    SELECT RAISE(ABORT, 'room_still_occupied') WHERE EXISTS (SELECT 1 FROM reservation_room WHERE room_id=NEW.room_id AND id<>NEW.id AND status='CHECKED_IN');
    SELECT RAISE(ABORT, 'missing_guests') WHERE NOT EXISTS (SELECT 1 FROM reservation_guest WHERE reservation_room_id = NEW.id);
    SELECT RAISE(ABORT, 'family_check_required') WHERE NEW.verified_at IS NULL
    AND EXISTS (SELECT 1 FROM reservation_guest g JOIN person p ON p.id = g.person_id WHERE g.reservation_room_id = NEW.id AND p.sex = 'MALE')
    AND EXISTS (SELECT 1 FROM reservation_guest g JOIN person p ON p.id = g.person_id WHERE g.reservation_room_id = NEW.id AND p.sex = 'FEMALE');
END;

CREATE TRIGGER key_no_update BEFORE UPDATE ON key_event BEGIN SELECT RAISE(ABORT,'key_history_immutable'); END;

CREATE TRIGGER key_no_delete BEFORE DELETE ON key_event BEGIN SELECT RAISE(ABORT,'key_history_immutable'); END;

CREATE TRIGGER departure_extension_guard BEFORE UPDATE OF departure,departure_time ON reservation_room
WHEN OLD.status IN ('CHECKED_IN','CHECKED_OUT') AND (NEW.departure<>OLD.departure OR NEW.departure_time<>OLD.departure_time)
AND (OLD.status='CHECKED_OUT' OR datetime(NEW.departure||'T'||NEW.departure_time)<=datetime(OLD.departure||'T'||OLD.departure_time))
BEGIN SELECT RAISE(ABORT,'stay_already_started'); END;

-- Version du format SQLite.
PRAGMA user_version=5;
