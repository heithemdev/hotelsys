package com.hotelsys.service;

import com.hotelsys.model.Row;
import com.hotelsys.model.StayDraft;
import com.hotelsys.model.User;
import com.hotelsys.repository.Db;
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
    public static String category(int stars) { check(stars >= 0 && stars <= 5,"Classement invalide"); return stars == 0 ? "Non classé" : stars + (stars == 1 ? " étoile" : " étoiles"); }
    public static int stars(String category) { return category.matches("^[1-5].*") ? category.charAt(0)-'0' : 0; }
    private static boolean mealEnabled(Row hotel,String meal) { return hotel.flag(meal.toLowerCase(Locale.ROOT)+"_enabled"); }
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
    public User setup(String hotelName,int stars,String display,char[] password,Set<String> meals) throws SQLException {
        check(MEALS.containsAll(meals),"Service inconnu");
        return setupAccount(hotelName,category(stars),display,"admin."+java.util.UUID.randomUUID().toString().substring(0,12),password,meals);
    }
    private User setupAccount(String hotelName,String category,String display,String login,char[] password,Set<String> meals) throws SQLException {
        String name = required(hotelName, "Nom de l'hôtel", 150); String cat = required(category, "Catégorie", 80);
        String person = required(display, "Votre nom", 100); String username = loginName(login); String hash = Passwords.hash(password);
        User created = db.tx(c -> {
            check(scalar(c, "SELECT count(*) FROM app_user") == 0, "L'administrateur existe déjà");
            execute(c, "UPDATE hotel SET name=?,category=?,breakfast_enabled=?,lunch_enabled=?,dinner_enabled=? WHERE id=1", name, cat, meals.contains("BREAKFAST")?1:0,meals.contains("LUNCH")?1:0,meals.contains("DINNER")?1:0);
            long id = insert(c, "INSERT INTO app_user(display_name,username,password_hash,role) VALUES (?,?,?,'ADMIN')", person, username, hash);
            return new User(id, person, username, "ADMIN");
        });
        user = created; return created;
    }
    public List<Row> loginAccounts(String role) throws SQLException {
        check(Set.of("ADMIN","WORKER").contains(role),"Rôle invalide");
        return db.read(c -> rows(c,"SELECT id,display_name AS label FROM app_user WHERE role=? AND active=1 ORDER BY display_name,id",role));
    }
    public User login(long id,String role,char[] password) throws SQLException {
        User result=db.read(c -> {
            List<Row> found=rows(c,"SELECT * FROM app_user WHERE id=? AND role=? AND active=1",id,role);
            check(!found.isEmpty() && Passwords.verify(password,found.getFirst().text("password_hash")),"Compte ou mot de passe incorrect");
            Row row=found.getFirst(); return new User(row.number("id"),row.text("display_name"),row.text("username"),row.text("role"));
        }); user=result; return result;
    }
    public User login(String name, String role, char[] password) throws SQLException {
        List<Row> accounts = db.read(c -> rows(c,"SELECT id FROM app_user WHERE display_name=? COLLATE NOCASE AND role=? AND active=1", required(name,"Votre nom",100),role));
        check(accounts.size()==1,"Compte ou mot de passe incorrect");
        return login(accounts.getFirst().number("id"),role,password);
    }
    public List<Row> offeredMealRates() throws SQLException {
        return db.read(c -> { require(c,false); Row hotel=one(c,"SELECT * FROM hotel WHERE id=1"); return rows(c,"SELECT meal,price AS adult_price FROM hotel_meal").stream().filter(r -> mealEnabled(hotel,r.text("meal"))).toList(); });
    }
    public List<Row> mealRates() throws SQLException { return read(false,"SELECT meal,price AS adult_price FROM hotel_meal ORDER BY meal"); }
    public List<Row> models() throws SQLException { return read(false, "SELECT *, name AS label FROM room_model ORDER BY name"); }
    public List<Row> rooms() throws SQLException {
        return read(false, """
                SELECT r.*, m.nightly_price, m.name AS model, m.bed_count, m.max_sharing_children, m.facilities,
                r.number || ' · ' || m.name AS label,
                CASE WHEN EXISTS(SELECT 1 FROM reservation_room s WHERE s.room_id=r.id AND s.status='CHECKED_IN' AND datetime(s.departure||'T'||s.departure_time)<=datetime(?)) THEN 'OVERDUE'
                WHEN EXISTS(SELECT 1 FROM reservation_room s WHERE s.room_id=r.id AND s.status='CHECKED_IN') THEN 'CHECKED_IN'
                WHEN EXISTS(SELECT 1 FROM reservation_room s JOIN stay_timing t ON t.id=s.id
                    WHERE s.room_id=r.id AND s.status='CHECKED_OUT' AND t.room_ready_at>datetime(?, 'subsec')) THEN 'PREPARING'
                WHEN EXISTS(SELECT 1 FROM reservation_room s WHERE s.room_id=r.id AND s.status='RESERVED' AND datetime(s.arrival||'T'||s.arrival_time)<=datetime(?) AND datetime(s.departure||'T'||s.departure_time)>datetime(?)) THEN 'RESERVED'
                ELSE 'FREE' END AS occupancy FROM room r JOIN room_model m ON m.id=r.model_id ORDER BY r.number
                """, localNow().toString(), localNow().toString(), localNow().toString(), localNow().toString());
    }
    public List<Row> guests() throws SQLException {
        return read(false, "SELECT *, first_name || ' ' || last_name AS label FROM person ORDER BY last_name,first_name");
    }
    public long saveGuest(long id, String last, String first, LocalDate birth, String sex, String phone, String email,String identityType,String identityNumber) throws SQLException {
        String surname = required(last, "Nom", 100); String given = required(first, "Prénom", 100);
        check(birth != null && birth.getYear() >= 1900 && !birth.isAfter(today()), "Date de naissance invalide");
        check(Set.of("MALE", "FEMALE").contains(sex), "Sexe requis");
        String contact = optional(phone, 40); String mail = optional(email, 200);
        check(contact.isEmpty() || contact.matches("\\+?[0-9]{6,15}"),"Téléphone : 6 à 15 chiffres, avec + facultatif");
        check(mail.isEmpty() || mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"Adresse e-mail invalide");
        check(Set.of("NATIONAL_ID","PASSPORT","RESIDENCE_PERMIT").contains(identityType),"Type de document requis");
        String document=optional(identityNumber,20).toUpperCase(Locale.ROOT);
        check(under(birth,18,today()) || !document.isEmpty(),"Le numéro du document d'identité est obligatoire pour un adulte");
        check(document.isEmpty() || document.matches(identityType.equals("NATIONAL_ID")?"[0-9]{3,20}":"[A-Z0-9]{3,20}"),"Numéro de document invalide (3 à 20 caractères)");
        return db.tx(c -> {
            require(c, false);
            if (id == 0) { return insert(c, "INSERT INTO person(last_name,first_name,birth_date,sex,phone,email,identity_type,identity_number) VALUES (?,?,?,?,?,?,?,?)", surname, given, birth.toString(), sex, contact, mail,identityType,document.isEmpty()?null:document); }
            Row old = one(c, "SELECT * FROM person WHERE id=?", id);
            if (!old.text("birth_date").equals(birth.toString()) || !old.text("sex").equals(sex)) {
                check(scalar(c, "SELECT count(*) FROM reservation_guest g JOIN reservation_room r ON r.id=g.reservation_room_id WHERE g.person_id=? AND r.status IN ('RESERVED','CHECKED_IN')", id) == 0,
                        "Cette identité participe à un séjour actif. Modifiez ou annulez d'abord le séjour pour préserver son devis et ses contrôles");
                check(scalar(c, "SELECT count(*) FROM reservation b JOIN reservation_room r ON r.reservation_id=b.id WHERE b.holder_id=? AND r.status IN ('RESERVED','CHECKED_IN')", id) == 0,
                        "Le titulaire possède une réservation active ; corrigez-la avant son identité");
            }
            execute(c, "UPDATE person SET last_name=?,first_name=?,birth_date=?,sex=?,phone=?,email=?,identity_type=?,identity_number=? WHERE id=?", surname, given, birth.toString(), sex, contact, mail,identityType,document.isEmpty()?null:document,id); return id;
        });
    }
    public long saveModel(long id, String name, int beds, int sharing, String facilities, long price) throws SQLException {
        String label = required(name, "Nom du modèle", 80); String equipment = optional(facilities, 2000);
        check(price>=0 && beds > 0 && beds <= 100 && sharing >= 0 && sharing <= 20, "Tarif ou capacité invalide");
        return db.tx(c -> {
            require(c, true);
            if (id != 0) {
                check(scalar(c, """
                        SELECT count(*) FROM reservation_room s JOIN room r ON r.id=s.room_id
                        WHERE r.model_id=? AND s.status IN ('RESERVED','CHECKED_IN') AND s.departure>?
                        AND ((SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=1)>?
                        OR (SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=0)>?)
                        """, id, today().toString(), beds, sharing) == 0, "La nouvelle capacité est insuffisante pour un séjour existant");
                execute(c, "UPDATE room_model SET name=?,bed_count=?,max_sharing_children=?,facilities=?,nightly_price=? WHERE id=?", label, beds, sharing, equipment, price, id);
            }
            long key = id == 0 ? insert(c, "INSERT INTO room_model(name,bed_count,max_sharing_children,facilities,nightly_price) VALUES (?,?,?,?,?)", label, beds, sharing, equipment, price) : id;
            return key;
        });
    }
    public void saveRoom(long id, String number, long model, boolean active) throws SQLException {
        String label = required(number, "Numéro", 20);
        db.tx(c -> {
            require(c, true);
            if (id == 0) { insert(c, "INSERT INTO room(number,model_id,active) VALUES (?,?,?)", label, model, active ? 1 : 0); }
            else {
                Row target = one(c, "SELECT * FROM room_model WHERE id=?", model);
                check(scalar(c, """
                        SELECT count(*) FROM reservation_room s WHERE s.room_id=? AND s.status IN ('RESERVED','CHECKED_IN') AND s.departure>?
                        AND ((SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=1)>?
                        OR (SELECT count(*) FROM reservation_guest g WHERE g.reservation_room_id=s.id AND g.requires_bed=0)>?)
                        """, id, today().toString(), target.number("bed_count"), target.number("max_sharing_children")) == 0, "Le modèle ne peut pas accueillir un séjour existant");
                execute(c, "UPDATE room SET number=?,model_id=?,active=? WHERE id=?", label, model, active ? 1 : 0, id);
            }
            return null;
        });
    }
    public void deleteRoom(long id) throws SQLException {
        db.tx(c -> { require(c, true); check(scalar(c, "SELECT count(*) FROM reservation_room WHERE room_id=?", id) == 0, "Cette chambre a un historique : désactivez-la"); execute(c, "DELETE FROM room WHERE id=?", id); return null; });
    }
    public void settings(String name, int stars, int bedAge, Set<String> meals, boolean sound,
                         java.util.Map<String, Long> prices) throws SQLException {
        String title = required(name, "Nom de l'hôtel", 150), rating = category(stars);
        check(bedAge >= 0 && bedAge <= 18 && MEALS.containsAll(meals), "Paramètres invalides");
        check(prices.keySet().equals(MEALS) && prices.values().stream().allMatch(price -> price >= 0), "Tarifs repas invalides");
        db.tx(c -> {
            require(c, true);
            execute(c, """
                    UPDATE hotel SET name=?,category=?,bed_sharing_age=?,breakfast_enabled=?,
                        lunch_enabled=?,dinner_enabled=?,payment_sound=? WHERE id=1
                    """, title, rating, bedAge, meals.contains("BREAKFAST") ? 1 : 0,
                    meals.contains("LUNCH") ? 1 : 0, meals.contains("DINNER") ? 1 : 0, sound ? 1 : 0);
            for (var rate : prices.entrySet()) {
                execute(c, "UPDATE hotel_meal SET price=? WHERE meal=?", rate.getValue(), rate.getKey());
            }
            return null;
        });
    }
    public List<Row> workers() throws SQLException { return read(true, "SELECT id,display_name,username,role,active,created_at FROM app_user ORDER BY display_name"); }
    private void saveUserAccount(long id, String name, String login, String role, boolean active, char[] password) throws SQLException {
        String display = required(name, "Nom", 100); String username = loginName(login);
        check(Set.of("ADMIN", "WORKER").contains(role), "Rôle invalide");
        String hash = password.length > 0 ? Passwords.hash(password) : null; check(id != 0 || hash != null, "Mot de passe requis");
        db.tx(c -> {
            require(c, true);
            check(!role.equals("ADMIN") || scalar(c,"SELECT count(*) FROM app_user WHERE role='ADMIN' AND id<>?",id)==0,"Un seul administrateur est autorisé");
            check(scalar(c,"SELECT count(*) FROM app_user WHERE role=? AND display_name=? COLLATE NOCASE AND id<>?",role,display,id)==0,"Ce nom existe déjà pour ce rôle ; utilisez le nom complet");
            if (id == 0) { insert(c, "INSERT INTO app_user(display_name,username,role,active,password_hash) VALUES (?,?,?,?,?)", display, username, role, active ? 1 : 0, hash); }
            else {
                execute(c, "UPDATE app_user SET display_name=?,username=?,role=?,active=? WHERE id=?", display, username, role, active ? 1 : 0, id);
                if (hash != null) { execute(c, "UPDATE app_user SET password_hash=? WHERE id=?", hash, id); }
            }
            return null;
        });
    }
    public void saveUser(long id,String name,String role,boolean active,char[] password) throws SQLException {
        String internal=id==0?"staff."+java.util.UUID.randomUUID().toString().substring(0,12):db.read(c -> { require(c,true); return one(c,"SELECT username FROM app_user WHERE id=?",id).text("username"); });
        saveUserAccount(id,name,internal,role,active,password);
    }

    private record GuestPlan(long id, boolean bed, boolean child) { }
    private record MealPlan(String meal, long adult, long child) { }
    private record Plan(StayDraft draft, Row room, Row hotel, List<GuestPlan> guests, List<MealPlan> meals, long total) { }
    public record Quote(long total, List<Long> roomTotals) { public Quote { roomTotals = List.copyOf(roomTotals); } }

    private Plan plan(Connection c, StayDraft draft, long excluding) throws SQLException {
        check(draft.arrival() != null && draft.departure() != null && draft.arrivalTime()!=null && draft.departureTime()!=null && draft.ends().isAfter(draft.starts()) && !draft.arrival().isBefore(today()), "Choisissez une arrivée à partir d'aujourd'hui et un départ ultérieur");
        check(validHour(draft.arrivalTime()) && validHour(draft.departureTime()),"Arrivées et départs autorisés de 08:00 à 22:00");
        check(!draft.starts().isBefore(java.time.LocalDateTime.now(clock).withSecond(0).withNano(0)),"L'arrivée ne peut pas être dans le passé");
        check(!draft.guests().isEmpty() && new HashSet<>(draft.guests()).size() == draft.guests().size(), "Ajoutez les occupants sans doublons");
        check(MEALS.containsAll(draft.meals()), "Repas inconnu");
        Row room = one(c, "SELECT r.*,m.nightly_price,m.bed_count,m.max_sharing_children FROM room r JOIN room_model m ON m.id=r.model_id WHERE r.id=? AND r.active=1", draft.roomId());
        Row hotel = one(c, "SELECT * FROM hotel WHERE id=1");
        check(scalar(c, "SELECT count(*) FROM stay_timing WHERE room_id=? AND id<>? AND starts_at<datetime(?, '+3 hours') AND room_ready_at>datetime(?, 'subsec')", draft.roomId(), excluding, draft.ends().toString(), draft.starts().toString()) == 0, "Chambre indisponible : prévoyez trois heures entre deux séjours");
        List<GuestPlan> guests = new ArrayList<>();
        for (long guestId : draft.guests()) {
            Row person = one(c, "SELECT * FROM person WHERE id=?", guestId);
            check(under(person.date("birth_date"),18,draft.arrival()) || person.has("identity_number"),"Complétez le document d'identité de " + person.text("first_name"));
            check(!person.date("birth_date").isAfter(draft.arrival()), "Naissance postérieure à l'arrivée");
            check(scalar(c, """
                    SELECT count(*) FROM reservation_guest g JOIN stay_timing r ON r.id=g.reservation_room_id
                    WHERE g.person_id=? AND r.id<>? AND r.starts_at<datetime(?) AND r.ends_at>datetime(?, 'subsec')
                    """, guestId, excluding, draft.ends().toString(), draft.starts().toString()) == 0, "Un occupant est déjà réservé à ces dates : " + person.text("first_name"));
            guests.add(new GuestPlan(guestId, !under(person.date("birth_date"), (int) hotel.number("bed_sharing_age"), draft.arrival()), under(person.date("birth_date"), (int) hotel.number("meal_child_age"), draft.arrival())));
        }
        long beds = guests.stream().filter(GuestPlan::bed).count();
        check(beds <= room.number("bed_count"), "Nombre de lits insuffisant pour les occupants");
        check(guests.size() - beds <= room.number("max_sharing_children"), "Limite d'enfants sans lit dépassée");
        List<MealPlan> meals = new ArrayList<>();
        for (String meal : draft.meals()) {
            check(mealEnabled(hotel,meal),"Ce repas n'est pas proposé par l'établissement");
            Row rate = one(c, "SELECT price FROM hotel_meal WHERE meal=?", meal);
            meals.add(new MealPlan(meal, rate.number("price"), rate.number("price")));
        }
        long daily = room.number("nightly_price");
        for (GuestPlan guest : guests) { for (MealPlan meal : meals) { daily = Math.addExact(daily, guest.child() ? meal.child() : meal.adult()); } }
        return new Plan(draft, room, hotel, guests, meals, Math.multiplyExact(Math.max(1,ChronoUnit.DAYS.between(draft.arrival(), draft.departure())), daily));
    }
    private List<Plan> plans(Connection c, long holder, List<StayDraft> drafts, long excluding) throws SQLException {
        check(!drafts.isEmpty(), "Ajoutez au moins une chambre");
        Row person = one(c, "SELECT * FROM person WHERE id=?", holder);
        check(person.has("identity_number"),"Complétez le document d'identité du titulaire");
        check(!under(person.date("birth_date"), person.text("sex").equals("MALE") ? 19 : 21, today()), "Le titulaire doit avoir 19 ans (homme) ou 21 ans (femme)");
        List<Plan> result = new ArrayList<>();
        for (StayDraft draft : drafts) { result.add(plan(c, draft, excluding)); }
        for (int i = 0; i < drafts.size(); i++) { for (int j = 0; j < i; j++) {
            StayDraft a = drafts.get(i), b = drafts.get(j);
            if (a.starts().isBefore(b.ends().plusHours(3)) && b.starts().isBefore(a.ends().plusHours(3))) {
                check(a.roomId()!=b.roomId(),"Prévoyez trois heures entre deux séjours dans une chambre");
            }
            if(a.starts().isBefore(b.ends()) && b.starts().isBefore(a.ends())) {
                check(a.guests().stream().noneMatch(b.guests()::contains), "Des occupants se chevauchent dans la réservation");
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
    public record BookingResult(long id, boolean paymentRecorded) { }

    public BookingResult book(long holder, List<StayDraft> drafts, String notes, long expected) throws SQLException {
        String details = optional(notes, 2000);
        return db.tx(c -> {
            User actor = require(c, false); List<Plan> plans = plans(c, holder, drafts, 0);
            check(quotation(plans).total() == expected, "Le devis a changé ; calculez-le de nouveau");
            long id = insert(c, "INSERT INTO reservation(holder_id,created_by,booked_at,notes) VALUES (?,?,?,?)", holder, actor.id(), now(), details);
            for (Plan plan : plans) { writeStay(c, id, 0, plan); }
            return new BookingResult(id, collectBalance(c, id, actor));
        });
    }
    private static void writeStay(Connection c, long booking, long line, Plan plan) throws SQLException {
        StayDraft draft = plan.draft();
        if (line != 0) {
            check(one(c, "SELECT status FROM reservation_room WHERE id=?", line).text("status").equals("RESERVED"), "Seul un séjour réservé est modifiable");
            execute(c, "DELETE FROM reservation_guest WHERE reservation_room_id=?", line);
            execute(c, "DELETE FROM reservation_room_meal WHERE reservation_room_id=?", line);
            execute(c, """
                    UPDATE reservation_room SET room_id=?,arrival=?,departure=?,arrival_time=?,departure_time=?,nightly_price=?,bed_count=?,max_sharing_children=?,bed_sharing_age=?,meal_child_age=?,
                    family_basis=NULL,family_document=NULL,family_note='',verified_by=NULL,verified_at=NULL WHERE id=?
                    """, draft.roomId(), draft.arrival().toString(), draft.departure().toString(), draft.arrivalTime().toString(), draft.departureTime().toString(), plan.room().number("nightly_price"), plan.room().number("bed_count"), plan.room().number("max_sharing_children"), plan.hotel().number("bed_sharing_age"), plan.hotel().number("meal_child_age"), line);
        } else {
            line = insert(c, """
                    INSERT INTO reservation_room(reservation_id,room_id,arrival,departure,arrival_time,departure_time,nightly_price,bed_count,max_sharing_children,bed_sharing_age,meal_child_age)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, booking, draft.roomId(), draft.arrival().toString(), draft.departure().toString(), draft.arrivalTime().toString(), draft.departureTime().toString(), plan.room().number("nightly_price"), plan.room().number("bed_count"), plan.room().number("max_sharing_children"), plan.hotel().number("bed_sharing_age"), plan.hotel().number("meal_child_age"));
        }
        for (GuestPlan guest : plan.guests()) { execute(c, "INSERT INTO reservation_guest VALUES (?,?,?,?)", line, guest.id(), guest.bed() ? 1 : 0, guest.child() ? "CHILD" : "ADULT"); }
        for (MealPlan meal : plan.meals()) { execute(c, "INSERT INTO reservation_room_meal VALUES (?,?,?,?)", line, meal.meal(), meal.adult(), meal.child()); }
    }
    public boolean updateStay(long line, StayDraft draft, long expected) throws SQLException {
        return db.tx(c -> {
            User actor = require(c, false); Row existing = one(c, "SELECT * FROM reservation_room WHERE id=?", line);
            Plan plan = plan(c, draft, line); check(plan.total() == expected, "Le devis a changé ; recalculez-le");
            writeStay(c, existing.number("reservation_id"), line, plan);
            return collectBalance(c, existing.number("reservation_id"), actor);
        });
    }
    public List<Row> bookings() throws SQLException {
        return read(false, """
                SELECT b.*,p.first_name||' '||p.last_name AS label,v.total_due,v.net_paid,v.balance,
                (SELECT coalesce(sum(amount),0) FROM cash_entry e WHERE e.reservation_id=b.id AND e.kind='REFUND') AS refunded,
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
    public List<Row> calendar() throws SQLException {
        return read(false, """
                SELECT s.reservation_id,s.arrival,s.arrival_time,date(t.ends_at) AS departure,
                    strftime('%H:%M',t.ends_at) AS departure_time,s.status,p.first_name||' '||p.last_name AS label,room.number
                FROM reservation_room s JOIN stay_timing t ON t.id=s.id
                JOIN reservation b ON b.id=s.reservation_id JOIN person p ON p.id=b.holder_id JOIN room ON room.id=s.room_id
                ORDER BY s.arrival,s.arrival_time,s.id
                """);
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
                check(!localNow().isBefore(start(stay)) && localNow().isBefore(end(stay)), "L'arrivée doit être dans les heures du séjour");
                execute(c, "UPDATE reservation_room SET status='CHECKED_IN',checked_in_at=? WHERE id=?", now(), line);
            } else if (action.equals("CHECKED_OUT")) {
                check(execute(c, "UPDATE reservation_room SET status='CHECKED_OUT',checked_out_at=? WHERE id=? AND status='CHECKED_IN'", now(), line) == 1, "Cette chambre n'est pas en séjour");
            } else if (action.equals("CANCELLED")) {
                check(execute(c, "UPDATE reservation_room SET status='CANCELLED' WHERE id=? AND status='RESERVED'", line) == 1, "Seul un séjour réservé peut être annulé");
            } else { throw new IllegalArgumentException("Transition inconnue"); }
            return null;
        });
    }
    public java.time.LocalDateTime localNow(){ return java.time.LocalDateTime.now(clock); }
    public static java.time.LocalDateTime start(Row stay){return stay.date("arrival").atTime(java.time.LocalTime.parse(stay.text("arrival_time")));}
    public static java.time.LocalDateTime end(Row stay){return stay.date("departure").atTime(java.time.LocalTime.parse(stay.text("departure_time")));}
    public static boolean validHour(java.time.LocalTime time){return time!=null && !time.isBefore(java.time.LocalTime.of(8,0)) && !time.isAfter(java.time.LocalTime.of(22,0));}
    private boolean collectBalance(Connection c,long booking,User actor) throws SQLException {
        long balance=scalar(c,"SELECT balance FROM reservation_balances WHERE reservation_id=?",booking);
        if(balance<=0){return false;}
        List<Row> sessions=rows(c,"SELECT * FROM cash_session WHERE closed_at IS NULL");
        long session;
        if(sessions.isEmpty()){session=insert(c,"INSERT INTO cash_session(opened_by,opened_at,opening_float) VALUES (?,?,0)",actor.id(),now());}
        else{session=sessions.getFirst().number("id");sessionAccess(c,session,false);}
        execute(c,"INSERT INTO cash_entry(session_id,actor_id,recorded_at,kind,amount,reservation_id,reason) VALUES (?,?,?,'PAYMENT',?,?,?)",session,actor.id(),now(),balance,booking,"Réservation · encaissement automatique");
        return true;
    }
    public List<Row> keyStays() throws SQLException {
        return read(false,"SELECT s.*,room.number,p.first_name||' '||p.last_name AS holder,room.number||' · '||p.first_name||' '||p.last_name AS label,coalesce((SELECT action FROM key_event e WHERE e.reservation_room_id=s.id ORDER BY e.id DESC LIMIT 1),'GIVE') AS last_action FROM reservation_room s JOIN room ON room.id=s.room_id JOIN reservation b ON b.id=s.reservation_id JOIN person p ON p.id=b.holder_id WHERE s.status='CHECKED_IN' OR (s.status IN ('CHECKED_OUT','CANCELLED') AND s.arrival<=? AND coalesce((SELECT action FROM key_event e WHERE e.reservation_room_id=s.id ORDER BY e.id DESC LIMIT 1),'GIVE')='GIVE') ORDER BY (s.status='CHECKED_IN') DESC,s.departure,s.departure_time",today().toString());
    }
    public List<Row> keyHistory(long person) throws SQLException {
        return read(false,"SELECT e.*,s.reservation_id,s.arrival,s.departure,room.number,u.display_name,p.first_name||' '||p.last_name AS client FROM key_event e JOIN reservation_room s ON s.id=e.reservation_room_id JOIN room ON room.id=s.room_id JOIN app_user u ON u.id=e.actor_id JOIN person p ON p.id=e.person_id WHERE s.id IN (SELECT reservation_room_id FROM reservation_guest WHERE person_id=?) ORDER BY e.id DESC",person);
    }
    public List<Row> personStays(long person) throws SQLException {
        return read(false,"SELECT s.*,room.number,room.number||' · '||s.arrival||' → '||s.departure AS label FROM reservation_room s JOIN room ON room.id=s.room_id WHERE s.id IN (SELECT reservation_room_id FROM reservation_guest WHERE person_id=?) ORDER BY s.arrival DESC",person);
    }
    public void keys(long line,long person,String action,boolean finalReturn) throws SQLException {
        check(Set.of("TAKE","GIVE").contains(action) && (!finalReturn || action.equals("TAKE")),"Mouvement de clés invalide");
        db.tx(c -> {
            User actor=require(c,false); Row stay=one(c,"SELECT * FROM reservation_room WHERE id=?",line);
            check(scalar(c,"SELECT count(*) FROM reservation_guest WHERE reservation_room_id=? AND person_id=?",line,person)==1,"Choisissez un occupant de cette chambre");
            List<Row> events=rows(c,"SELECT action,final_return FROM key_event WHERE reservation_room_id=? ORDER BY id DESC LIMIT 1",line);
            String last=events.isEmpty()?"GIVE":events.getFirst().text("action");
            check(events.isEmpty() || !last.equals(action) || finalReturn && action.equals("TAKE") && !events.isEmpty() && !events.getFirst().flag("final_return"),action.equals("TAKE")?"Les clés sont déjà à la réception":"Les clés sont déjà chez le client");
            check(stay.text("status").equals("CHECKED_IN") || action.equals("TAKE"),"Ce séjour est terminé ou annulé : retour des clés uniquement");
            execute(c,"INSERT INTO key_event(reservation_room_id,person_id,actor_id,recorded_at,action,final_return) VALUES (?,?,?,?,?,?)",line,person,actor.id(),now(),action,finalReturn?1:0);
            if(finalReturn && stay.text("status").equals("CHECKED_IN")){execute(c,"UPDATE reservation_room SET status='CHECKED_OUT',checked_out_at=? WHERE id=?",now(),line);}
            return null;
        });
    }
    public long extensionQuote(long line,LocalDate departure,java.time.LocalTime time) throws SQLException {
        check(departure!=null && validHour(time),"Choisissez un départ entre 08:00 et 22:00");
        return db.read(c -> {require(c,false);Row stay=one(c,"SELECT * FROM reservation_room WHERE id=? AND status='CHECKED_IN'",line);return extensionQuote(c,stay,departure,time);});
    }
    private long extensionQuote(Connection c,Row stay,LocalDate departure,java.time.LocalTime time) throws SQLException {
        var target=departure.atTime(time);
        check(target.isAfter(end(stay)) && target.isAfter(localNow()),"Le nouveau départ doit prolonger le séjour");
        check(scalar(c,"SELECT count(*) FROM stay_timing WHERE room_id=? AND id<>? AND starts_at<datetime(?, '+3 hours') AND room_ready_at>datetime(?, 'subsec')",stay.number("room_id"),stay.number("id"),target.toString(),start(stay).toString())==0,"Une réservation suit ce séjour : conservez trois heures de préparation");
        check(scalar(c,"SELECT count(*) FROM reservation_guest own JOIN reservation_guest other ON own.person_id=other.person_id AND own.reservation_room_id<>other.reservation_room_id JOIN stay_timing s ON s.id=other.reservation_room_id WHERE own.reservation_room_id=? AND s.starts_at<datetime(?) AND s.ends_at>datetime(?, 'subsec')",stay.number("id"),target.toString(),start(stay).toString())==0,"Un occupant a une autre réservation sur cette période");
        Row total=one(c,"SELECT * FROM room_totals WHERE reservation_room_id=?",stay.number("id"));
        long nights=Math.max(1,ChronoUnit.DAYS.between(stay.date("arrival"),departure));
        return Math.subtractExact(Math.multiplyExact(nights,Math.addExact(stay.number("nightly_price"),total.number("daily_meal_total"))),total.number("total"));
    }
    public boolean extendStay(long line,LocalDate departure,java.time.LocalTime time) throws SQLException {
        check(departure!=null && validHour(time),"Choisissez un départ entre 08:00 et 22:00");
        return db.tx(c -> {
            User actor=require(c,false);Row stay=one(c,"SELECT * FROM reservation_room WHERE id=? AND status='CHECKED_IN'",line);
            extensionQuote(c,stay,departure,time);
            execute(c,"UPDATE reservation_room SET departure=?,departure_time=? WHERE id=?",departure.toString(),time.toString(),line);
            return collectBalance(c, stay.number("reservation_id"), actor);
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
            return rows(c, "SELECT e.*,u.display_name,p.first_name||' '||p.last_name AS client FROM cash_entry e JOIN app_user u ON u.id=e.actor_id LEFT JOIN reservation b ON b.id=e.reservation_id LEFT JOIN person p ON p.id=b.holder_id WHERE e.session_id=? ORDER BY e.id DESC", session); });
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
            require(c, false);
            return one(c, """
                    SELECT (SELECT count(*) FROM room WHERE active=1) AS rooms,
                    (SELECT count(*) FROM reservation_room WHERE status='CHECKED_IN') AS occupied,
                    (SELECT count(*) FROM reservation_room WHERE arrival=? AND status='RESERVED') AS arrivals,
                    (SELECT count(*) FROM reservation_room WHERE departure=? AND status='CHECKED_IN') AS departures
                    """, today().toString(), today().toString());
        });
    }
    public record BusinessStats(Row totals, List<Row> daily, List<Row> balances) { }
    public BusinessStats statistics(LocalDate from,LocalDate through) throws SQLException {
        check(from!=null && through!=null && !through.isBefore(from) && ChronoUnit.DAYS.between(from,through)<366,"Choisissez une période de 1 à 366 jours");
        LocalDate until=through.plusDays(1);
        return db.read(c -> {
            require(c,true);
            String period="""
                    WITH period AS (
                    SELECT s.nightly_price + t.daily_meal_total AS daily_price,
                    max(1,CAST(min(julianday(s.departure),julianday(?))-max(julianday(s.arrival),julianday(?)) AS INTEGER)) AS nights
                    FROM reservation_room s JOIN room_totals t ON t.reservation_room_id=s.id
                    WHERE s.status<>'CANCELLED' AND s.arrival<? AND (s.departure>? OR s.arrival=s.departure AND s.arrival>=?))
                    """;
            long sales = scalar(c, period + "SELECT coalesce(sum(nights * daily_price), 0) FROM period",
                    until.toString(), from.toString(), until.toString(), from.toString(), from.toString());
            String begin=UTC.format(from.atStartOfDay(clock.getZone()).toInstant()),end=UTC.format(until.atStartOfDay(clock.getZone()).toInstant());
            List<Row> entries=rows(c,"SELECT kind,amount,recorded_at FROM cash_entry WHERE recorded_at>=? AND recorded_at<? ORDER BY recorded_at",begin,end);
            java.util.Map<LocalDate,long[]> buckets=new java.util.LinkedHashMap<>();
            for(LocalDate date=from;date.isBefore(until);date=date.plusDays(1)){buckets.put(date,new long[3]);}
            long paid=0,refunded=0,expenses=0;
            for(Row entry:entries){
                long amount=entry.number("amount"); int kind=entry.text("kind").equals("PAYMENT")?0:entry.text("kind").equals("REFUND")?1:2;
                LocalDate date=java.time.Instant.parse(entry.text("recorded_at")).atZone(clock.getZone()).toLocalDate();
                buckets.get(date)[kind]=Math.addExact(buckets.get(date)[kind],amount);
                if(kind==0){paid=Math.addExact(paid,amount);}else if(kind==1){refunded=Math.addExact(refunded,amount);}else{expenses=Math.addExact(expenses,amount);}
            }
            List<Row> daily=new ArrayList<>(); buckets.forEach((date,amounts) -> daily.add(new Row(java.util.Map.of("day",date.toString(),"receipts",amounts[0]-amounts[1],"expenses",amounts[2]))));
            Row totals = new Row(java.util.Map.of("sales", sales, "net_receipts", paid - refunded,
                    "expenses", expenses, "cash_flow", paid - refunded - expenses));
            List<Row> balances=rows(c,"SELECT b.id,p.first_name||' '||p.last_name AS label,p.phone,v.balance FROM reservation b JOIN person p ON p.id=b.holder_id JOIN reservation_balances v ON v.reservation_id=b.id WHERE v.balance>0 ORDER BY v.balance DESC,b.id LIMIT 10");
            return new BusinessStats(totals, List.copyOf(daily), balances);
        });
    }
}
