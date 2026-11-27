package com.hotelsys.service;

import com.hotelsys.model.Row;
import com.hotelsys.model.StayDraft;
import com.hotelsys.model.User;
import com.hotelsys.repository.Db;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static com.hotelsys.repository.Db.*;

/** Un service concret pour le projet ; toutes les écritures métier sont transactionnelles. */
public final class HotelStore {
    public static final ZoneId ZONE = ZoneId.of("Africa/Algiers");
    private static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static final Set<String> MEALS = Set.of("BREAKFAST", "LUNCH", "DINNER");
    private final Db db;
    private final Clock clock;
    private User user;

    public HotelStore(Db db) { this(db, Clock.system(ZONE)); }
    public HotelStore(Db db, Clock clock) { this.db = db; this.clock = clock; }
    public Db database() { return db; }
    public User user() { return user; }
    public LocalDate today() { return LocalDate.now(clock); }
    private String now() { return UTC.format(clock.instant()); }
    public static void check(boolean condition, String message) { if (!condition) { throw new IllegalArgumentException(message); } }
    public static String required(String value, String label, int max) {
        String clean = value == null ? "" : value.strip();
        check(!clean.isEmpty() && clean.length() <= max, label + " : saisissez 1 à " + max + " caractères"); return clean;
    }
    public static String optional(String value, int max) {
        String clean = value == null ? "" : value.strip(); check(clean.length() <= max, "Texte trop long (" + max + " maximum)"); return clean;
    }
    public static boolean under(LocalDate birth, int age, LocalDate date) { return birth.plusYears(age).isAfter(date); }
    public static long discounted(long price, int percent) {
        check(price >= 0 && percent >= 0 && percent <= 100, "Remise invalide");
        return BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(100L - percent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
    }
    private User require(Connection c, boolean admin) throws SQLException {
        check(user != null, "Connectez-vous pour continuer");
        Row row = one(c, "SELECT * FROM app_user WHERE id=? AND active=1", user.id());
        check(!admin || row.text("role").equals("ADMIN"), "Action réservée à l'administrateur");
        return new User(row.number("id"), row.text("display_name"), row.text("username"), row.text("role"));
    }
    private List<Row> read(boolean admin, String sql, Object... args) throws SQLException {
        return db.read(c -> { require(c, admin); return rows(c, sql, args); });
    }
    public boolean needsSetup() throws SQLException { return db.read(c -> scalar(c, "SELECT count(*) FROM app_user") == 0); }
    public Row hotel() throws SQLException { return db.read(c -> one(c, "SELECT * FROM hotel WHERE id=1")); }
    public void logout() { user = null; }
    private static String loginName(String value) {
        String name = required(value, "Identifiant", 40).toLowerCase(Locale.ROOT);
        check(name.matches("[a-z0-9][a-z0-9._-]{2,39}"), "Identifiant : 3 à 40 lettres, chiffres, points ou tirets"); return name;
    }
    public User setup(String hotelName, String category, String display, String login, char[] password, boolean demo) throws SQLException {
        String name = required(hotelName, "Nom de l'hôtel", 150); String cat = required(category, "Catégorie", 80);
        String person = required(display, "Votre nom", 100); String username = loginName(login); String hash = Passwords.hash(password);
        User created = db.tx(c -> {
            check(scalar(c, "SELECT count(*) FROM app_user") == 0, "L'administrateur existe déjà");
            execute(c, "UPDATE hotel SET name=?, category=? WHERE id=1", name, cat);
            long id = insert(c, "INSERT INTO app_user(display_name,username,password_hash,role) VALUES (?,?,?,'ADMIN')", person, username, hash);
            if (demo) { DemoData.create(c, id, today(), now()); }
            return new User(id, person, username, "ADMIN");
        });
        user = created; return created;
    }
    public User login(String login, char[] password) throws SQLException {
        String name = optional(login, 40).toLowerCase(Locale.ROOT);
        User result = db.read(c -> {
            List<Row> resultRows = rows(c, "SELECT * FROM app_user WHERE username=? AND active=1", name);
            check(!resultRows.isEmpty() && Passwords.verify(password, resultRows.getFirst().text("password_hash")), "Identifiant ou mot de passe incorrect");
            Row row = resultRows.getFirst(); return new User(row.number("id"), row.text("display_name"), row.text("username"), row.text("role"));
        });
        user = result; return result;
    }
    public List<Row> models() throws SQLException { return read(false, "SELECT *, name AS label FROM room_model ORDER BY name"); }
    public List<Row> mealRates(long model) throws SQLException { return read(false, "SELECT * FROM room_model_meal WHERE model_id=? ORDER BY meal", model); }
    public List<Row> rooms() throws SQLException {
        return read(false, """
                SELECT r.*, m.name AS model, m.bed_count, m.max_sharing_children, m.facilities,
                r.number || ' · ' || m.name AS label,
                CASE WHEN EXISTS(SELECT 1 FROM reservation_room s WHERE s.room_id=r.id AND s.status='CHECKED_IN') THEN 'CHECKED_IN'
                WHEN EXISTS(SELECT 1 FROM reservation_room s WHERE s.room_id=r.id AND s.status='RESERVED' AND s.arrival<=? AND s.departure>?) THEN 'RESERVED'
                ELSE 'FREE' END AS occupancy FROM room r JOIN room_model m ON m.id=r.model_id ORDER BY r.number
                """, today().toString(), today().toString());
    }
    public List<Row> guests() throws SQLException {
        return read(false, "SELECT *, first_name || ' ' || last_name AS label FROM person ORDER BY last_name,first_name");
    }
    public long saveGuest(long id, String last, String first, LocalDate birth, String sex, String phone, String email) throws SQLException {
        String surname = required(last, "Nom", 100); String given = required(first, "Prénom", 100);
        check(birth != null && birth.getYear() >= 1900 && !birth.isAfter(today()), "Date de naissance invalide");
        check(Set.of("MALE", "FEMALE").contains(sex), "Sexe requis");
        String contact = optional(phone, 40); String mail = optional(email, 200);
        return db.tx(c -> {
            require(c, false);
            if (id == 0) { return insert(c, "INSERT INTO person(last_name,first_name,birth_date,sex,phone,email) VALUES (?,?,?,?,?,?)", surname, given, birth.toString(), sex, contact, mail); }
            Row old = one(c, "SELECT * FROM person WHERE id=?", id);
            if (!old.text("birth_date").equals(birth.toString()) || !old.text("sex").equals(sex)) {
                check(scalar(c, "SELECT count(*) FROM reservation_guest g JOIN reservation_room r ON r.id=g.reservation_room_id WHERE g.person_id=? AND r.status IN ('RESERVED','CHECKED_IN')", id) == 0,
                        "Cette identité participe à un séjour actif. Modifiez ou annulez d'abord le séjour pour préserver son devis et ses contrôles");
                check(scalar(c, "SELECT count(*) FROM reservation b JOIN reservation_room r ON r.reservation_id=b.id WHERE b.holder_id=? AND r.status IN ('RESERVED','CHECKED_IN')", id) == 0,
                        "Le titulaire possède une réservation active ; corrigez-la avant son identité");
            }
            execute(c, "UPDATE person SET last_name=?,first_name=?,birth_date=?,sex=?,phone=?,email=? WHERE id=?", surname, given, birth.toString(), sex, contact, mail, id); return id;
        });
    }
    public record MealRate(String meal, long adult, Long childFixed, Integer discount) { }
    public long saveModel(long id, String name, int beds, int sharing, String facilities, List<MealRate> rates) throws SQLException {
        String label = required(name, "Nom du modèle", 80); String equipment = optional(facilities, 2000);
        check(beds > 0 && beds <= 100 && sharing >= 0 && sharing <= 20, "Capacité invalide");
        for (MealRate rate : rates) {
            check(MEALS.contains(rate.meal()) && rate.adult() >= 0 && (rate.childFixed() != null) != (rate.discount() != null), "Tarif repas invalide");
            check(rate.childFixed() == null || rate.childFixed() >= 0, "Prix enfant négatif");
            check(rate.discount() == null || rate.discount() >= 0 && rate.discount() <= 100, "Remise : 0 à 100 %");
        }
        return db.tx(c -> {
            require(c, true);
            if (id != 0) {
                check(scalar(c, """
                        SELECT count(*) FROM reservation_room s JOIN room r ON r.id=s.room_id
                        WHERE r.model_id=? AND s.status IN ('RESERVED','CHECKED_IN') AND s.departure>?
                        AND ((SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=1)>?
                        OR (SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=0)>?)
                        """, id, today().toString(), beds, sharing) == 0, "La nouvelle capacité est insuffisante pour un séjour existant");
                execute(c, "UPDATE room_model SET name=?,bed_count=?,max_sharing_children=?,facilities=? WHERE id=?", label, beds, sharing, equipment, id);
            }
            long key = id == 0 ? insert(c, "INSERT INTO room_model(name,bed_count,max_sharing_children,facilities) VALUES (?,?,?,?)", label, beds, sharing, equipment) : id;
            execute(c, "DELETE FROM room_model_meal WHERE model_id=?", key);
            for (MealRate rate : rates) { execute(c, "INSERT INTO room_model_meal VALUES (?,?,?,?,?)", key, rate.meal(), rate.adult(), rate.childFixed(), rate.discount()); }
            return key;
        });
    }
    public void saveRoom(long id, String number, long model, long price, boolean active) throws SQLException {
        String label = required(number, "Numéro", 20); check(price >= 0, "Prix négatif");
        db.tx(c -> {
            require(c, true);
            if (id == 0) { insert(c, "INSERT INTO room(number,model_id,nightly_price,active) VALUES (?,?,?,?)", label, model, price, active ? 1 : 0); }
            else {
                Row target = one(c, "SELECT * FROM room_model WHERE id=?", model);
                check(scalar(c, """
                        SELECT count(*) FROM reservation_room s WHERE s.room_id=? AND s.status IN ('RESERVED','CHECKED_IN') AND s.departure>?
                        AND ((SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=1)>?
                        OR (SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=0)>?)
                        """, id, today().toString(), target.number("bed_count"), target.number("max_sharing_children")) == 0, "Le modèle ne peut pas accueillir un séjour existant");
                execute(c, "UPDATE room SET number=?,model_id=?,nightly_price=?,active=? WHERE id=?", label, model, price, active ? 1 : 0, id);
            }
            return null;
        });
    }
    public void deleteRoom(long id) throws SQLException {
        db.tx(c -> { require(c, true); check(scalar(c, "SELECT count(*) FROM reservation_room WHERE room_id=?", id) == 0, "Cette chambre a un historique : désactivez-la"); execute(c, "DELETE FROM room WHERE id=?", id); return null; });
    }
    public void settings(String name, String category, int bedAge, int mealAge) throws SQLException {
        String title = required(name, "Nom", 150); String cat = required(category, "Catégorie", 80);
        check(bedAge >= 0 && bedAge <= 18 && mealAge >= 0 && mealAge <= 18, "Seuils d'âge : 0 à 18 ans");
        db.tx(c -> { require(c, true); execute(c, "UPDATE hotel SET name=?,category=?,bed_sharing_age=?,meal_child_age=? WHERE id=1", title, cat, bedAge, mealAge); return null; });
    }
    public List<Row> workers() throws SQLException { return read(true, "SELECT id,display_name,username,role,active,created_at FROM app_user ORDER BY display_name"); }
    public void saveUser(long id, String name, String login, String role, boolean active, char[] password) throws SQLException {
        String display = required(name, "Nom", 100); String username = loginName(login);
        check(Set.of("ADMIN", "WORKER").contains(role), "Rôle invalide");
        String hash = password.length > 0 ? Passwords.hash(password) : null; check(id != 0 || hash != null, "Mot de passe requis");
        db.tx(c -> {
            require(c, true);
            if (id == 0) { insert(c, "INSERT INTO app_user(display_name,username,role,active,password_hash) VALUES (?,?,?,?,?)", display, username, role, active ? 1 : 0, hash); }
            else {
                execute(c, "UPDATE app_user SET display_name=?,username=?,role=?,active=? WHERE id=?", display, username, role, active ? 1 : 0, id);
                if (hash != null) { execute(c, "UPDATE app_user SET password_hash=? WHERE id=?", hash, id); }
            }
            return null;
        });
    }

    private record GuestPlan(long id, boolean bed, boolean child) { }
    private record MealPlan(String meal, long adult, long child) { }
    private record Plan(StayDraft draft, Row room, Row hotel, List<GuestPlan> guests, List<MealPlan> meals, long total) { }
    public record Quote(long total, List<Long> roomTotals) { public Quote { roomTotals = List.copyOf(roomTotals); } }

    private Plan plan(Connection c, StayDraft draft, long excluding) throws SQLException {
        check(draft.arrival() != null && draft.departure() != null && draft.departure().isAfter(draft.arrival()) && !draft.arrival().isBefore(today()), "Choisissez une arrivée à partir d'aujourd'hui et un départ ultérieur");
        check(!draft.guests().isEmpty() && new HashSet<>(draft.guests()).size() == draft.guests().size(), "Ajoutez les occupants sans doublons");
        check(MEALS.containsAll(draft.meals()), "Repas inconnu");
        Row room = one(c, "SELECT r.*,m.bed_count,m.max_sharing_children FROM room r JOIN room_model m ON m.id=r.model_id WHERE r.id=? AND r.active=1", draft.roomId());
        Row hotel = one(c, "SELECT * FROM hotel WHERE id=1");
        check(scalar(c, "SELECT count(*) FROM reservation_room WHERE room_id=? AND id<>? AND status<>'CANCELLED' AND arrival<? AND departure>?", draft.roomId(), excluding, draft.departure().toString(), draft.arrival().toString()) == 0, "Chambre indisponible sur ces dates");
        List<GuestPlan> guests = new ArrayList<>();
        for (long guestId : draft.guests()) {
            Row person = one(c, "SELECT * FROM person WHERE id=?", guestId);
            check(!person.date("birth_date").isAfter(draft.arrival()), "Naissance postérieure à l'arrivée");
            check(scalar(c, """
                    SELECT count(*) FROM reservation_guest g JOIN reservation_room r ON r.id=g.reservation_room_id
                    WHERE g.person_id=? AND r.id<>? AND r.status<>'CANCELLED' AND r.arrival<? AND r.departure>?
                    """, guestId, excluding, draft.departure().toString(), draft.arrival().toString()) == 0, "Un occupant est déjà réservé à ces dates : " + person.text("first_name"));
            guests.add(new GuestPlan(guestId, !under(person.date("birth_date"), (int) hotel.number("bed_sharing_age"), draft.arrival()), under(person.date("birth_date"), (int) hotel.number("meal_child_age"), draft.arrival())));
        }
        long beds = guests.stream().filter(GuestPlan::bed).count();
        check(beds <= room.number("bed_count"), "Nombre de lits insuffisant pour les occupants");
        check(guests.size() - beds <= room.number("max_sharing_children"), "Limite d'enfants sans lit dépassée");
        List<MealPlan> meals = new ArrayList<>();
        for (String meal : draft.meals()) {
            Row rate = one(c, "SELECT * FROM room_model_meal WHERE model_id=? AND meal=?", room.number("model_id"), meal);
            meals.add(new MealPlan(meal, rate.number("adult_price"), rate.has("child_fixed_price") ? rate.number("child_fixed_price") : discounted(rate.number("adult_price"), (int) rate.number("child_discount_percent"))));
        }
        long daily = room.number("nightly_price");
        for (GuestPlan guest : guests) { for (MealPlan meal : meals) { daily = Math.addExact(daily, guest.child() ? meal.child() : meal.adult()); } }
        return new Plan(draft, room, hotel, guests, meals, Math.multiplyExact(ChronoUnit.DAYS.between(draft.arrival(), draft.departure()), daily));
    }
    private List<Plan> plans(Connection c, long holder, List<StayDraft> drafts, long excluding) throws SQLException {
        check(!drafts.isEmpty(), "Ajoutez au moins une chambre");
        Row person = one(c, "SELECT * FROM person WHERE id=?", holder);
        check(!under(person.date("birth_date"), person.text("sex").equals("MALE") ? 19 : 21, today()), "Le titulaire doit avoir 19 ans (homme) ou 21 ans (femme)");
        List<Plan> result = new ArrayList<>();
        for (StayDraft draft : drafts) { result.add(plan(c, draft, excluding)); }
        for (int i = 0; i < drafts.size(); i++) { for (int j = 0; j < i; j++) {
            StayDraft a = drafts.get(i), b = drafts.get(j);
            if (a.arrival().isBefore(b.departure()) && b.arrival().isBefore(a.departure())) {
                check(a.roomId() != b.roomId() && a.guests().stream().noneMatch(b.guests()::contains), "Des chambres ou occupants se chevauchent dans le devis");
            }
        } }
        return result;
    }
    private static Quote quotation(List<Plan> plans) {
        long sum = 0; for (Plan plan : plans) { sum = Math.addExact(sum, plan.total()); }
        return new Quote(sum, plans.stream().map(Plan::total).toList());
    }
    public Quote quote(long holder, List<StayDraft> drafts, long excluding) throws SQLException {
        return db.read(c -> { require(c, false); return quotation(plans(c, holder, drafts, excluding)); });
    }
    public long book(long holder, List<StayDraft> drafts, String notes, long expected) throws SQLException {
        String details = optional(notes, 2000);
        return db.tx(c -> {
            User actor = require(c, false); List<Plan> plans = plans(c, holder, drafts, 0);
            check(quotation(plans).total() == expected, "Le devis a changé ; calculez-le de nouveau");
            long id = insert(c, "INSERT INTO reservation(holder_id,created_by,booked_at,notes) VALUES (?,?,?,?)", holder, actor.id(), now(), details);
            for (Plan plan : plans) { writeStay(c, id, 0, plan); } return id;
        });
    }
    private static void writeStay(Connection c, long booking, long line, Plan plan) throws SQLException {
        StayDraft draft = plan.draft();
        if (line != 0) {
            check(one(c, "SELECT status FROM reservation_room WHERE id=?", line).text("status").equals("RESERVED"), "Seul un séjour réservé est modifiable");
            execute(c, "DELETE FROM reservation_guest WHERE reservation_room_id=?", line);
            execute(c, "DELETE FROM reservation_room_meal WHERE reservation_room_id=?", line);
            execute(c, """
                    UPDATE reservation_room SET room_id=?,arrival=?,departure=?,nightly_price=?,bed_count=?,max_sharing_children=?,bed_sharing_age=?,meal_child_age=?,
                    family_basis=NULL,family_document=NULL,family_note='',verified_by=NULL,verified_at=NULL WHERE id=?
                    """, draft.roomId(), draft.arrival().toString(), draft.departure().toString(), plan.room().number("nightly_price"), plan.room().number("bed_count"), plan.room().number("max_sharing_children"), plan.hotel().number("bed_sharing_age"), plan.hotel().number("meal_child_age"), line);
        } else {
            line = insert(c, """
                    INSERT INTO reservation_room(reservation_id,room_id,arrival,departure,nightly_price,bed_count,max_sharing_children,bed_sharing_age,meal_child_age)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, booking, draft.roomId(), draft.arrival().toString(), draft.departure().toString(), plan.room().number("nightly_price"), plan.room().number("bed_count"), plan.room().number("max_sharing_children"), plan.hotel().number("bed_sharing_age"), plan.hotel().number("meal_child_age"));
        }
        for (GuestPlan guest : plan.guests()) { execute(c, "INSERT INTO reservation_guest VALUES (?,?,?,?)", line, guest.id(), guest.bed() ? 1 : 0, guest.child() ? "CHILD" : "ADULT"); }
        for (MealPlan meal : plan.meals()) { execute(c, "INSERT INTO reservation_room_meal VALUES (?,?,?,?)", line, meal.meal(), meal.adult(), meal.child()); }
    }
    public void updateStay(long line, StayDraft draft, long expected) throws SQLException {
        db.tx(c -> {
            require(c, false); Row existing = one(c, "SELECT * FROM reservation_room WHERE id=?", line);
            Plan plan = plan(c, draft, line); check(plan.total() == expected, "Le devis a changé ; recalculez-le");
            writeStay(c, existing.number("reservation_id"), line, plan); return null;
        });
    }
    public List<Row> bookings() throws SQLException {
        return read(false, """
                SELECT b.*,p.first_name||' '||p.last_name AS label,v.total_due,v.net_paid,v.balance,
                (SELECT min(arrival) FROM reservation_room r WHERE r.reservation_id=b.id) AS arrival,
                (SELECT max(departure) FROM reservation_room r WHERE r.reservation_id=b.id) AS departure,
                (SELECT count(*) FROM reservation_room r WHERE r.reservation_id=b.id) AS rooms,
                CASE WHEN NOT EXISTS(SELECT 1 FROM reservation_room r WHERE r.reservation_id=b.id AND r.status<>'CANCELLED') THEN 'CANCELLED'
                WHEN EXISTS(SELECT 1 FROM reservation_room r WHERE r.reservation_id=b.id AND r.status='CHECKED_IN') THEN 'CHECKED_IN'
                WHEN EXISTS(SELECT 1 FROM reservation_room r WHERE r.reservation_id=b.id AND r.status='RESERVED') THEN 'RESERVED'
                ELSE 'CHECKED_OUT' END AS status
                FROM reservation b JOIN person p ON p.id=b.holder_id JOIN reservation_balances v ON v.reservation_id=b.id ORDER BY b.id DESC
                """);
    }
    public List<Row> stays(long booking) throws SQLException {
        return read(false, "SELECT r.*,room.number,m.name AS model,t.total FROM reservation_room r JOIN room ON room.id=r.room_id JOIN room_model m ON m.id=room.model_id JOIN room_totals t ON t.reservation_room_id=r.id WHERE r.reservation_id=? ORDER BY r.arrival,r.id", booking);
    }
    public List<Row> stayGuests(long line) throws SQLException {
        return read(false, "SELECT p.*,p.first_name||' '||p.last_name AS label,g.requires_bed,g.meal_tariff FROM reservation_guest g JOIN person p ON p.id=g.person_id WHERE g.reservation_room_id=? ORDER BY p.last_name", line);
    }
    public List<Row> stayMeals(long line) throws SQLException { return read(false, "SELECT * FROM reservation_room_meal WHERE reservation_room_id=?", line); }
    public List<Row> calendar(LocalDate from, LocalDate to) throws SQLException {
        return read(false, """
                SELECT s.*,p.first_name||' '||p.last_name AS label,room.number,
                (SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id) AS guests,
                (SELECT group_concat(meal,',') FROM reservation_room_meal meals WHERE meals.reservation_room_id=s.id) AS meals
                FROM reservation_room s JOIN reservation b ON b.id=s.reservation_id JOIN person p ON p.id=b.holder_id JOIN room ON room.id=s.room_id
                WHERE s.status<>'CANCELLED' AND s.arrival<? AND s.departure>? ORDER BY s.arrival
                """, to.toString(), from.toString());
    }
    public void verifyFamily(long line, String basis, String document, String note) throws SQLException {
        check(Set.of("SPOUSES", "BLOOD_RELATIVES", "MIXED_FAMILY").contains(basis), "Relation requise");
        check(Set.of("FAMILY_BOOK", "MARRIAGE_CERTIFICATE", "BIRTH_CERTIFICATE", "OTHER").contains(document), "Type de document requis");
        String details = optional(note, 500); check(!document.equals("OTHER") || !details.isEmpty(), "Précisez le document présenté");
        db.tx(c -> { User actor = require(c, false); check(execute(c, "UPDATE reservation_room SET family_basis=?,family_document=?,family_note=?,verified_by=?,verified_at=? WHERE id=? AND status='RESERVED'", basis, document, details, actor.id(), now(), line) == 1, "Séjour réservé introuvable"); return null; });
    }
    public void changeStatus(long line, String action) throws SQLException {
        db.tx(c -> {
            require(c, false);
            if (action.equals("CHECKED_IN")) {
                Row stay = one(c, "SELECT * FROM reservation_room WHERE id=? AND status='RESERVED'", line);
                check(!today().isBefore(stay.date("arrival")) && today().isBefore(stay.date("departure")), "L'arrivée doit être dans les dates du séjour");
                execute(c, "UPDATE reservation_room SET status='CHECKED_IN',checked_in_at=? WHERE id=?", now(), line);
            } else if (action.equals("CHECKED_OUT")) {
                check(execute(c, "UPDATE reservation_room SET status='CHECKED_OUT',checked_out_at=? WHERE id=? AND status='CHECKED_IN'", now(), line) == 1, "Cette chambre n'est pas en séjour");
            } else if (action.equals("CANCELLED")) {
                check(execute(c, "UPDATE reservation_room SET status='CANCELLED' WHERE id=? AND status='RESERVED'", line) == 1, "Seul un séjour réservé peut être annulé");
            } else { throw new IllegalArgumentException("Transition inconnue"); }
            return null;
        });
    }
    public List<Row> sessions() throws SQLException {
        return db.read(c -> {
            User actor = require(c, false);
            return rows(c, "SELECT s.*,u.display_name AS owner,t.expected_cash,t.discrepancy FROM cash_session s JOIN app_user u ON u.id=s.opened_by JOIN cash_session_totals t ON t.session_id=s.id WHERE (?=1 OR s.opened_by=?) ORDER BY s.id DESC", actor.admin() ? 1 : 0, actor.id());
        });
    }
    public List<Row> cashEntries(long session) throws SQLException {
        return db.read(c -> { User actor = require(c, false); Row owner = one(c, "SELECT opened_by FROM cash_session WHERE id=?", session); check(actor.admin() || owner.number("opened_by") == actor.id(), "Cette caisse appartient à un autre employé");
            return rows(c, "SELECT e.*,u.display_name FROM cash_entry e JOIN app_user u ON u.id=e.actor_id WHERE e.session_id=? ORDER BY e.id DESC", session); });
    }
    private User sessionAccess(Connection c, long session, boolean admin) throws SQLException {
        User actor = require(c, admin); Row row = one(c, "SELECT * FROM cash_session WHERE id=? AND closed_at IS NULL", session);
        check(actor.admin() || row.number("opened_by") == actor.id(), "Cette caisse appartient à un autre employé"); return actor;
    }
    public long openCash(long amount) throws SQLException {
        check(amount >= 0, "Fond négatif");
        return db.tx(c -> { User actor = require(c, false); check(scalar(c, "SELECT count(*) FROM cash_session WHERE closed_at IS NULL") == 0, "Une caisse est déjà ouverte ; clôturez-la avant la relève"); return insert(c, "INSERT INTO cash_session(opened_by,opened_at,opening_float) VALUES (?,?,?)", actor.id(), now(), amount); });
    }
    public void closeCash(long session, long counted, String note) throws SQLException {
        check(counted >= 0, "Montant compté négatif"); String details = optional(note, 1000);
        db.tx(c -> { User actor = sessionAccess(c, session, false); execute(c, "UPDATE cash_session SET closed_by=?,closed_at=?,counted_cash=?,closing_note=? WHERE id=?", actor.id(), now(), counted, details, session); return null; });
    }
    public void cashEntry(long session, String kind, Long booking, long amount, String reason) throws SQLException {
        check(Set.of("PAYMENT", "REFUND", "EXPENSE").contains(kind) && amount > 0, "Montant strictement positif requis");
        String details = optional(reason, 1000); check(kind.equals("PAYMENT") || !details.isEmpty(), "Motif obligatoire");
        check(kind.equals("EXPENSE") == (booking == null), "Réservation requise pour un paiement ou remboursement");
        db.tx(c -> {
            User actor = sessionAccess(c, session, !kind.equals("PAYMENT"));
            if (kind.equals("PAYMENT")) { check(amount <= scalar(c, "SELECT balance FROM reservation_balances WHERE reservation_id=?", booking), "Le paiement dépasse le solde à régler"); }
            if (kind.equals("REFUND")) { check(amount <= scalar(c, "SELECT net_paid FROM reservation_balances WHERE reservation_id=?", booking), "Remboursement supérieur aux encaissements nets"); }
            if (!kind.equals("PAYMENT")) { check(amount <= scalar(c, "SELECT expected_cash FROM cash_session_totals WHERE session_id=?", session), "Espèces disponibles insuffisantes"); }
            execute(c, "INSERT INTO cash_entry(session_id,actor_id,recorded_at,kind,amount,reservation_id,reason) VALUES (?,?,?,?,?,?,?)", session, actor.id(), now(), kind, amount, booking, details); return null;
        });
    }
    public Row overview() throws SQLException {
        return db.read(c -> {
            User actor = require(c, false);
            String start = UTC.format(today().atStartOfDay(clock.getZone()).toInstant()); String end = UTC.format(today().plusDays(1).atStartOfDay(clock.getZone()).toInstant());
            long revenue = actor.admin() ? scalar(c, "SELECT coalesce(sum(CASE kind WHEN 'PAYMENT' THEN amount ELSE -amount END),0) FROM cash_entry WHERE kind<>'EXPENSE' AND recorded_at>=? AND recorded_at<?", start, end) : 0;
            return one(c, """
                    SELECT (SELECT count(*) FROM room WHERE active=1) AS rooms,
                    (SELECT count(*) FROM reservation_room WHERE status='CHECKED_IN') AS occupied,
                    (SELECT count(*) FROM reservation_room WHERE arrival=? AND status='RESERVED') AS arrivals,
                    (SELECT count(*) FROM reservation_room WHERE departure=? AND status='CHECKED_IN') AS departures,
                    ? AS revenue
                    """, today().toString(), today().toString(), revenue);
        });
    }
    public List<Row> dailyReceipts() throws SQLException {
        return db.read(c -> {
            require(c, true); List<Row> result = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today().minusDays(i);
                String from = UTC.format(date.atStartOfDay(clock.getZone()).toInstant()); String to = UTC.format(date.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
                result.add(one(c, "SELECT ? AS day,coalesce(sum(CASE kind WHEN 'PAYMENT' THEN amount ELSE -amount END),0) AS amount FROM cash_entry WHERE kind<>'EXPENSE' AND recorded_at>=? AND recorded_at<?", date.toString(), from, to));
            }
            return result;
        });
    }
    public void loadDemo() throws SQLException {
        db.tx(c -> { User actor = require(c, true); check(scalar(c, "SELECT count(*) FROM room") == 0 && scalar(c, "SELECT count(*) FROM reservation") == 0 && scalar(c, "SELECT count(*) FROM person") == 0 && scalar(c, "SELECT count(*) FROM cash_session") == 0 && scalar(c, "SELECT count(*) FROM room_model") == 0, "L'exemple nécessite une base vide (modèles, chambres, clients et caisse)"); DemoData.create(c, actor.id(), today(), now()); return null; });
    }
}
