package com.hotelsys.service;

import com.hotelsys.repository.Db;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatterBuilder;

import static com.hotelsys.repository.Db.*;

public final class DemoData {
    private static final String MARK = "Démonstration HotelSys v1";
    private static final java.time.format.DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private record Guest(String first, String last, String sex, int age) { }
    private record Stay(int room, int guest, int arrival, int nights, String status, int paid, String note) { }

    private static final Guest[] GUESTS = {
        new Guest("Amine", "Benali", "MALE", 34), new Guest("Sarah", "Bensaïd", "FEMALE", 29),
        new Guest("Karim", "Haddad", "MALE", 41), new Guest("Yacine", "Mansouri", "MALE", 37),
        new Guest("Nadia", "Khelifi", "FEMALE", 31), new Guest("Rachid", "Bouzid", "MALE", 45),
        new Guest("Inès", "Saadi", "FEMALE", 26), new Guest("Sofiane", "Aït Ali", "MALE", 32),
        new Guest("Lina", "Meziane", "FEMALE", 33), new Guest("Leïla", "Mansouri", "FEMALE", 35),
        new Guest("Samir", "Amrane", "MALE", 48), new Guest("Adam", "Mansouri", "MALE", 5)
    };
    private static final Stay[] STAYS = {
        new Stay(0, 0, -10, 3, "CHECKED_OUT", 100, "Séjour professionnel"),
        new Stay(1, 1, -7, 2, "CHECKED_OUT", 100, "Week-end à Béjaïa"),
        new Stay(2, 2, -4, 2, "CHECKED_OUT", 100, "Visite familiale"),
        new Stay(3, 3, -2, 5, "CHECKED_IN", 100, "Séjour en famille"),
        new Stay(4, 4, -1, 3, "CHECKED_IN", 50, "Solde prévu au départ"),
        new Stay(5, 5, 1, 2, "RESERVED", 100, "Arrivée prévue à 14 h"),
        new Stay(6, 6, 4, 2, "RESERVED", 100, "Court séjour"),
        new Stay(7, 7, 8, 3, "RESERVED", 100, "Séjour en suite"),
        new Stay(0, 8, 12, 4, "RESERVED", 100, "Déplacement professionnel"),
        new Stay(1, 9, 18, 2, "RESERVED", 100, "Visite à Béjaïa"),
        new Stay(3, 10, 25, 3, "RESERVED", 100, "Séjour de fin de semaine"),
        new Stay(5, 0, 5, 2, "CANCELLED", 100, "Annulation avec remboursement partiel")
    };

    private DemoData() { }

    public static void seed(Db db) throws IOException, SQLException {
        if (!Files.isRegularFile(db.file())) {
            throw new IllegalArgumentException("Configurez l'hôtel avant d'ajouter les données de démonstration.");
        }
        db.initialize();
        if (db.read(DemoData::alreadySeeded)) {
            System.out.println("Les données de démonstration sont déjà présentes.");
            return;
        }
        Path folder = db.file().getParent().resolve("backups");
        Files.createDirectories(folder);
        Path backup = folder.resolve("hotelsys-pre-demo-" + Instant.now().toEpochMilli() + ".db");
        db.read(c -> { execute(c, "VACUUM INTO ?", backup.toString()); return null; });
        long added = db.tx(c -> alreadySeeded(c) ? -1 : insertDemo(c, Instant.now()));
        if (added < 0) {
            System.out.println("Les données de démonstration sont déjà présentes.");
        } else {
            System.out.println("Démonstration ajoutée : 8 chambres, 12 clients et 12 réservations.");
            System.out.println("Variation des espèces attendues : " + java.math.BigDecimal.valueOf(added, 2).toPlainString() + " DZD.");
            System.out.println("Sauvegarde : " + backup);
        }
    }

    private static boolean alreadySeeded(Connection c) throws SQLException {
        return scalar(c, "SELECT count(*) FROM reservation WHERE notes LIKE ?", MARK + "%") > 0;
    }

    private static long insertDemo(Connection c, Instant instant) throws SQLException {
        String now = UTC.format(instant);
        LocalDate today = instant.atZone(HotelStore.ZONE).toLocalDate();
        long admin = one(c, "SELECT id FROM app_user WHERE role='ADMIN' AND active=1").number("id");
        var sessions = rows(c, "SELECT id FROM cash_session WHERE closed_at IS NULL");
        long session = sessions.isEmpty()
                ? insert(c, "INSERT INTO cash_session(opened_by,opened_at,opening_float) VALUES (?,?,0)", admin, now)
                : sessions.getFirst().number("id");
        long expected = scalar(c, "SELECT expected_cash FROM cash_session_totals WHERE session_id=?", session);

        long classic = model(c, "Classique", 2, 650_000, "Wi-Fi\nClimatisation");
        long family = model(c, "Familiale", 3, 980_000, "Wi-Fi\nClimatisation\nBalcon");
        long suite = model(c, "Suite", 4, 1_540_000, "Wi-Fi\nSalon\nTerrasse");
        long[] models = {classic, classic, classic, family, family, family, suite, suite};
        String[] numbers = {"D-101", "D-102", "D-103", "D-201", "D-202", "D-203", "D-301", "D-302"};
        long[] rooms = new long[numbers.length];
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = insert(c, "INSERT INTO room(number,model_id) VALUES (?,?)", numbers[i], models[i]);
        }
        long[] guests = new long[GUESTS.length];
        for (int i = 0; i < guests.length; i++) {
            Guest guest = GUESTS[i];
            guests[i] = insert(c, """
                    INSERT INTO person(first_name,last_name,sex,birth_date,email,identity_type,identity_number)
                    VALUES (?,?,?,?,?,'PASSPORT',?)
                    """, guest.first(), guest.last(), guest.sex(), today.minusYears(guest.age()).minusMonths(i % 9).toString(),
                    guest.age() < 18 ? "" : "hotel-demo-" + (i + 1) + "@example.invalid",
                    guest.age() < 18 ? null : "DEMOHS" + (101 + i));
        }
        for (Stay stay : STAYS) { insertStay(c, stay, rooms[stay.room()], guests, admin, session, today, now); }
        return scalar(c, "SELECT expected_cash FROM cash_session_totals WHERE session_id=?", session) - expected;
    }

    private static long model(Connection c, String name, int beds, long price, String facilities) throws SQLException {
        return insert(c, "INSERT INTO room_model(name,bed_count,max_sharing_children,nightly_price,facilities) VALUES (?,?,1,?,?)",
                name + " · Démo", beds, price, facilities);
    }

    private static void insertStay(Connection c, Stay stay, long room, long[] guests, long admin, long session,
                                   LocalDate today, String now) throws SQLException {
        LocalDate arrival = today.plusDays(stay.arrival()), departure = arrival.plusDays(stay.nights());
        long holder = guests[stay.guest()];
        long booking = insert(c, "INSERT INTO reservation(holder_id,created_by,booked_at,notes) VALUES (?,?,?,?)",
                holder, admin, at(arrival.isAfter(today) ? today.minusDays(1) : arrival.minusDays(5), 10), MARK + " · " + stay.note());
        long line = insert(c, """
                INSERT INTO reservation_room(reservation_id,room_id,arrival,departure,nightly_price,bed_count,max_sharing_children,bed_sharing_age,meal_child_age)
                SELECT ?, r.id, ?, ?, m.nightly_price, m.bed_count, m.max_sharing_children, 6, 12
                FROM room r JOIN room_model m ON m.id=r.model_id WHERE r.id=?
                """, booking, arrival.toString(), departure.toString(), room);
        execute(c, "INSERT INTO reservation_guest VALUES (?,?,1,'ADULT')", line, holder);
        if (stay.guest() == 3) {
            execute(c, "INSERT INTO reservation_guest VALUES (?,?,1,'ADULT'),(?,?,0,'CHILD')", line, guests[9], line, guests[11]);
            execute(c, """
                    UPDATE reservation_room SET family_basis='MIXED_FAMILY',family_document='FAMILY_BOOK',
                    family_note=?,verified_by=?,verified_at=? WHERE id=?
                    """, MARK, admin, at(arrival, 14), line);
        }
        if (stay.room() % 2 == 0 || stay.guest() == 3) {
            execute(c, "INSERT INTO reservation_room_meal VALUES (?,'BREAKFAST',95000,95000)", line);
        }
        long total = scalar(c, "SELECT total FROM room_totals WHERE reservation_room_id=?", line);
        cash(c, session, admin, now, "PAYMENT", booking, total * stay.paid() / 100);
        switch (stay.status()) {
            case "CHECKED_IN", "CHECKED_OUT" -> {
                execute(c, "UPDATE reservation_room SET status='CHECKED_IN',checked_in_at=? WHERE id=?", at(arrival, 14), line);
                execute(c, """
                        INSERT INTO key_event(reservation_room_id,person_id,actor_id,recorded_at,action,final_return)
                        VALUES (?,?,?,?,'GIVE',0)
                        """, line, holder, admin, at(arrival, 14));
                if (stay.status().equals("CHECKED_OUT")) {
                    execute(c, "UPDATE reservation_room SET status='CHECKED_OUT',checked_out_at=? WHERE id=?", at(departure, 11), line);
                    execute(c, """
                            INSERT INTO key_event(reservation_room_id,person_id,actor_id,recorded_at,action,final_return)
                            VALUES (?,?,?,?,'TAKE',1)
                            """, line, holder, admin, at(departure, 11));
                }
            }
            case "CANCELLED" -> {
                execute(c, "UPDATE reservation_room SET status='CANCELLED' WHERE id=?", line);
                cash(c, session, admin, now, "REFUND", booking, total * 40 / 100);
            }
            default -> { }
        }
    }

    private static String at(LocalDate day, int hour) {
        return UTC.format(day.atTime(LocalTime.of(hour, 0)).atZone(HotelStore.ZONE).toInstant());
    }

    private static void cash(Connection c, long session, long admin, String now, String kind, long booking, long amount) throws SQLException {
        execute(c, """
                INSERT INTO cash_entry(session_id,actor_id,recorded_at,kind,amount,reservation_id,reason)
                VALUES (?,?,?,?,?,?,?)
                """, session, admin, now, kind, amount, booking, MARK + " · " + (kind.equals("PAYMENT") ? "Encaissement fictif" : "Remboursement fictif"));
    }
}
