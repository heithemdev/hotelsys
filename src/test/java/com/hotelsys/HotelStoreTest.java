package com.hotelsys;

import com.hotelsys.model.StayDraft;
import com.hotelsys.repository.Db;
import com.hotelsys.service.HotelStore;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HotelStoreTest {
    @TempDir Path directory;
    private static final String SECRET = "Une-reception-2026";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);
    private Db db;
    private HotelStore store;
    private long holder, model, room;
    private int nextDocument;

    @BeforeEach void setup() throws Exception {
        db = new Db(directory.resolve("hotel.db")); db.initialize();
        store = new HotelStore(db, Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), HotelStore.ZONE));
        store.setup("Hôtel test",3,"Admin",SECRET.toCharArray(),Set.of("DINNER"));
        model = store.saveModel(0, "Classique", 2, 1, "Balcon", List.of(new HotelStore.MealRate("DINNER", 100000, null, 50)));
        store.saveRoom(0, "101", model, 900000, true); room = store.rooms().getFirst().number("id");
        holder = guest("Amine", 30, "MALE");
    }
    private long guest(String name, int age, String sex) throws SQLException { return store.saveGuest(0, "Client", name, TODAY.minusYears(age), sex, "", "","NATIONAL_ID",age>=18?"100000"+(++nextDocument):""); }
    private StayDraft draft(List<Long> guests) { return new StayDraft(room, TODAY, TODAY.plusDays(3), guests, Set.of("DINNER")); }
    private long book(StayDraft draft) throws SQLException { return store.book(holder, List.of(draft), "", store.quote(holder, List.of(draft), 0).total()); }

    @Test void completeStayAdmissionMealsPaymentAndClosure() throws Exception {
        long spouse = guest("Leïla", 28, "FEMALE"), child = guest("Adam", 5, "MALE");
        long id = book(draft(List.of(holder, spouse, child))); long line = store.stays(id).getFirst().number("id");
        assertEquals(3450000, store.bookings().getFirst().number("total_due"));
        assertThrows(SQLException.class, () -> store.changeStatus(line, "CHECKED_IN"));
        store.verifyFamily(line, "MIXED_FAMILY", "FAMILY_BOOK", "Vérifié"); store.changeStatus(line, "CHECKED_IN");
        long session = store.openCash(100000); store.cashEntry(session, "PAYMENT", id, 3450000, "");
        assertEquals(0, store.bookings().getFirst().number("balance"));
        store.cashEntry(session, "EXPENSE", null, 50000, "Fournitures");
        store.changeStatus(line, "CHECKED_OUT"); store.closeCash(session, 3500000, "");
        assertEquals(0, store.sessions().getFirst().number("discrepancy"));
        assertThrows(IllegalArgumentException.class, () -> store.cashEntry(session, "PAYMENT", id, 1, ""));
    }
    @Test void capacityBirthdaysAndFixedRoomPrice() throws Exception {
        long second = guest("Karim", 25, "MALE"), five = guest("Enfant", 5, "MALE"), six = guest("Enfant6", 6, "MALE");
        StayDraft single = new StayDraft(room,TODAY,TODAY.plusDays(1),List.of(holder),Set.of());
        StayDraft family = new StayDraft(room,TODAY,TODAY.plusDays(1),List.of(holder,second,five),Set.of());
        assertEquals(900000, store.quote(holder,List.of(single),0).total()); assertEquals(900000,store.quote(holder,List.of(family),0).total());
        assertThrows(IllegalArgumentException.class, () -> store.quote(holder,List.of(draft(List.of(holder,second,six))),0));
        long tooYoung = guest("Jeune",18,"MALE");
        assertThrows(IllegalArgumentException.class, () -> store.quote(tooYoung,List.of(single),0));
        assertEquals(51, HotelStore.discounted(101,50));
    }
    @Test void independentDatesConflictsAndHistoricalPrices() throws Exception {
        long first = book(draft(List.of(holder))); long other = guest("Autre", 31, "MALE");
        assertThrows(IllegalArgumentException.class, () -> book(draft(List.of(other))));
        store.saveRoom(room, "101", model, 2000000, true);
        assertEquals(3000000, store.bookings().getFirst().number("total_due"));
        StayDraft adjacent = new StayDraft(room,TODAY.plusDays(3),TODAY.plusDays(5),List.of(holder),Set.of());
        long second = book(adjacent); assertTrue(second > first);
        store.changeStatus(store.stays(first).getFirst().number("id"),"CANCELLED");
        assertDoesNotThrow(() -> book(draft(List.of(other))));
        store.saveRoom(0,"102",model,700000,true);
        long room2=store.rooms().stream().filter(r -> r.text("number").equals("102")).findFirst().orElseThrow().number("id");
        List<StayDraft> group=List.of(new StayDraft(room,TODAY.plusDays(10),TODAY.plusDays(12),List.of(holder),Set.of()),
                new StayDraft(room2,TODAY.plusDays(12),TODAY.plusDays(15),List.of(holder),Set.of()));
        long grouped=store.book(holder,group,"Groupe",store.quote(holder,group,0).total());
        assertEquals(2,store.stays(grouped).size());
        db.initialize(); assertEquals(4, store.bookings().size());
    }
    @Test void staleQuotePermissionsAndLastAdmin() throws Exception {
        StayDraft stay = draft(List.of(holder)); long old = store.quote(holder,List.of(stay),0).total();
        store.saveRoom(room,"101",model,2000000,true);
        assertThrows(IllegalArgumentException.class, () -> store.book(holder,List.of(stay),"",old));
        long admin = store.user().id();
        assertThrows(SQLException.class, () -> store.saveUser(admin,"Admin","ADMIN",false,new char[0]));
        store.saveUser(0,"Réception","WORKER",true,SECRET.toCharArray());
        long worker=store.loginAccounts("WORKER").getFirst().number("id");store.login(worker,"WORKER",SECRET.toCharArray());
        assertThrows(IllegalArgumentException.class, () -> store.saveRoom(0,"999",model,100,true));
        assertThrows(IllegalArgumentException.class, store::dailyReceipts);
        assertThrows(IllegalArgumentException.class, () -> store.login(worker,"WORKER","incorrect".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> store.login(worker,"ADMIN",SECRET.toCharArray()));
    }
    @Test void cancellationRefundAndImmutableMoney() throws Exception {
        long booking = book(draft(List.of(holder))); long session = store.openCash(0);
        store.cashEntry(session,"PAYMENT",booking,500000,"");
        store.changeStatus(store.stays(booking).getFirst().number("id"),"CANCELLED");
        assertEquals(-500000,store.bookings().getFirst().number("balance"));
        assertThrows(IllegalArgumentException.class, () -> store.cashEntry(session,"REFUND",booking,500001,"Annulation"));
        store.cashEntry(session,"REFUND",booking,500000,"Annulation");
        assertEquals(0,store.bookings().getFirst().number("balance"));
        assertThrows(SQLException.class, () -> db.tx(c -> Db.execute(c,"DELETE FROM cash_entry")));
        assertEquals(2,store.cashEntries(session).size());
    }
    @Test void schemaAndTestFixturesAreHealthy() throws Exception {
        Db demoDb = new Db(directory.resolve("demo.db")); demoDb.initialize(); HotelStore demo = new HotelStore(demoDb);
        demo.setup("Exemple",3,"Démo",SECRET.toCharArray(),Set.of("BREAKFAST"));com.hotelsys.service.DemoData.seed(demo);
        assertEquals(8,demo.rooms().size()); assertEquals(6,demo.bookings().size());
        demoDb.read(c -> { assertEquals("ok", Db.one(c,"PRAGMA integrity_check").text("integrity_check")); assertTrue(Db.rows(c,"PRAGMA foreign_key_check").isEmpty()); return null; });
    }
    @Test void businessFiguresUsePeriodNightsAndSeparateCashFromSales() throws Exception {
        long booking=book(draft(List.of(holder)));long session=store.openCash(100000);
        store.cashEntry(session,"PAYMENT",booking,500000,"");store.cashEntry(session,"EXPENSE",null,50000,"Achat");
        var totals=store.statistics(TODAY,TODAY).totals();
        assertEquals(1,totals.number("room_nights"));assertEquals(1000000,totals.number("sales"));assertEquals(900000,totals.number("adr"));
        assertEquals(10000,totals.number("occupancy_bp"));assertEquals(450000,totals.number("cash_flow"));assertEquals(2500000,totals.number("open_balance"));
        store.changeStatus(store.stays(booking).getFirst().number("id"),"CANCELLED");
        assertEquals(0,store.statistics(TODAY,TODAY).totals().number("sales"));assertEquals(500000,store.statistics(TODAY,TODAY).totals().number("net_receipts"));
    }
    @Test void disabledMealsAndDuplicateDocumentsAreRejected() throws Exception {
        assertThrows(SQLException.class,() -> store.saveGuest(0,"Autre","Client",TODAY.minusYears(30),"MALE","","","NATIONAL_ID","1000001"));
        assertThrows(IllegalArgumentException.class,() -> store.saveGuest(0,"Autre","Client",TODAY.minusYears(30),"MALE","","","NATIONAL_ID","ABC"));
        store.settings("Hôtel test",4,6,12,Set.of("BREAKFAST"),false);
        assertTrue(store.offeredMealRates(model).isEmpty());assertThrows(IllegalArgumentException.class,() -> book(draft(List.of(holder))));
        assertEquals("4 étoiles",store.hotel().text("category"));assertFalse(store.hotel().flag("payment_sound"));
        assertThrows(IllegalArgumentException.class,() -> store.setup("Autre",2,"Autre",SECRET.toCharArray(),Set.of()));
    }
    @Test void versionOneMigrationPreservesDataAndCreatesBackup() throws Exception {
        Path file=directory.resolve("legacy.db");Db legacy=new Db(file);
        try(var c=legacy.connect();var statement=c.createStatement();var input=getClass().getResourceAsStream("/db/migrations/001_initial.sql")){
            statement.executeUpdate(new String(java.util.Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            statement.executeUpdate("UPDATE hotel SET name='Hôtel conservé'");statement.executeUpdate("INSERT INTO person(last_name,first_name,birth_date,sex) VALUES ('Client','Ancien','1990-01-01','MALE')");statement.executeUpdate("PRAGMA user_version=1");
        }
        legacy.initialize();assertEquals("Hôtel conservé",legacy.read(c -> Db.one(c,"SELECT name FROM hotel")).text("name"));
        assertEquals(2L,legacy.read(c -> Db.scalar(c,"PRAGMA user_version")).longValue());assertEquals(1L,legacy.read(c -> Db.scalar(c,"SELECT count(*) FROM person")).longValue());
        try(var backups=java.nio.file.Files.list(directory.resolve("backups"))){assertTrue(backups.findAny().isPresent());}
    }
}
