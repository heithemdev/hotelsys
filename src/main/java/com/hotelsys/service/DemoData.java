package com.hotelsys.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import static com.hotelsys.repository.Db.*;

/** Exemple facultatif, inséré une seule fois dans une base vide. */
final class DemoData {
    private DemoData() { }
    static void create(Connection c, long admin, LocalDate today, String now) throws SQLException {
        String[] names = {"Classique", "Familiale", "Suite"}; int[] beds = {2, 3, 4}; long[] models = new long[3];
        for (int i = 0; i < names.length; i++) {
            models[i] = insert(c, "INSERT INTO room_model(name,bed_count,max_sharing_children,facilities) VALUES (?,?,1,?)", names[i], beds[i], i == 2 ? "Télévision · Balcon · Jacuzzi" : "Télévision · Salle de bain · Wi-Fi");
            execute(c, "INSERT INTO room_model_meal VALUES (?,'BREAKFAST',50000,20000,NULL)", models[i]);
            execute(c, "INSERT INTO room_model_meal VALUES (?,'LUNCH',60000,NULL,50)", models[i]);
            execute(c, "INSERT INTO room_model_meal VALUES (?,'DINNER',100000,NULL,50)", models[i]);
        }
        String[] numbers = {"101", "102", "103", "201", "202", "203", "204", "301"}; long[] rooms = new long[8];
        for (int i = 0; i < numbers.length; i++) {
            int model = i < 2 ? 0 : i < 6 ? 1 : 2;
            rooms[i] = insert(c, "INSERT INTO room(number,model_id,nightly_price) VALUES (?,?,?)", numbers[i], models[model], model == 0 ? 600000 : model == 1 ? 900000 : 1400000);
        }
        String[][] identities = {{"Benali","Amine","MALE"},{"Bensaïd","Sarah","FEMALE"},{"Haddad","Karim","MALE"},{"Mansouri","Yacine","MALE"},{"Khelifi","Nadia","FEMALE"},{"Mansouri","Leïla","FEMALE"},{"Mansouri","Adam","MALE"},{"Bouzid","Rachid","MALE"}};
        long[] persons = new long[8];
        for (int i = 0; i < persons.length; i++) {
            persons[i] = insert(c, "INSERT INTO person(last_name,first_name,birth_date,sex,phone) VALUES (?,?,?,?,?)", identities[i][0], identities[i][1], today.minusYears(i == 6 ? 5 : 28 + i).minusDays(20).toString(), identities[i][2], "0550 12 34 " + (10 + i));
        }
        long[] bookings = new long[6];
        int[] occupants = {0,1,2,3,4,7};
        int[] roomIndex = {0,1,2,3,4,5}; int[] start = {-2,0,-4,-1,2,0}; int[] end = {2,3,-1,4,6,2};
        for (int i = 0; i < bookings.length; i++) {
            int who = occupants[i];
            bookings[i] = insert(c, "INSERT INTO reservation(holder_id,created_by,booked_at,notes) VALUES (?,?,?,'Exemple de démonstration')", persons[who], admin, now);
            long line = insert(c, """
                    INSERT INTO reservation_room(reservation_id,room_id,arrival,departure,nightly_price,bed_count,max_sharing_children,bed_sharing_age,meal_child_age)
                    SELECT ?,r.id,?,?,r.nightly_price,m.bed_count,1,6,12 FROM room r JOIN room_model m ON m.id=r.model_id WHERE r.id=?
                    """, bookings[i], today.plusDays(start[i]).toString(), today.plusDays(end[i]).toString(), rooms[roomIndex[i]]);
            execute(c, "INSERT INTO reservation_guest VALUES (?,?,1,'ADULT')", line, persons[who]);
            if (i == 3) {
                execute(c, "INSERT INTO reservation_guest VALUES (?,?,1,'ADULT')", line, persons[5]);
                execute(c, "INSERT INTO reservation_guest VALUES (?,?,0,'CHILD')", line, persons[6]);
                execute(c, "UPDATE reservation_room SET family_basis='MIXED_FAMILY',family_document='FAMILY_BOOK',verified_by=?,verified_at=? WHERE id=?", admin, now, line);
            }
            execute(c, "INSERT INTO reservation_room_meal VALUES (?,'BREAKFAST',50000,20000)", line);
            if (i == 0 || i == 2 || i == 3 || i == 5) { execute(c, "UPDATE reservation_room SET status='CHECKED_IN',checked_in_at=? WHERE id=?", now, line); }
            if (i == 2) { execute(c, "UPDATE reservation_room SET status='CHECKED_OUT',checked_out_at=? WHERE id=?", now, line); }
        }
        long session = insert(c, "INSERT INTO cash_session(opened_by,opened_at,opening_float) VALUES (?,?,100000)", admin, now);
        execute(c, "INSERT INTO cash_entry(session_id,actor_id,recorded_at,kind,amount,reservation_id) VALUES (?,?,?,'PAYMENT',1200000,?)", session, admin, now, bookings[0]);
        execute(c, "INSERT INTO cash_entry(session_id,actor_id,recorded_at,kind,amount,reservation_id) VALUES (?,?,?,'PAYMENT',500000,?)", session, admin, now, bookings[3]);
    }
}
