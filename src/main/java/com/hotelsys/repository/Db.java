package com.hotelsys.repository;

import com.hotelsys.model.Row;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.sqlite.SQLiteConfig;

public final class Db {
    private final Path file;
    public Db(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public Path file() { return file; }

    public static final int SCHEMA_VERSION = 5;

    public void initialize() throws IOException, SQLException {
        Files.createDirectories(file.getParent());
        String schema=resource("001_initial.sql");
        long version=read(c -> scalar(c,"PRAGMA user_version"));
        if(version==SCHEMA_VERSION){return;}
        if(version>SCHEMA_VERSION){throw new SQLException("La base nécessite une version plus récente de HotelSys");}
        if(version==0){
            tx(c -> {try(var statement=c.createStatement()){statement.executeUpdate(schema);}return null;});
            return;
        }
        Path backups=file.getParent().resolve("backups");Files.createDirectories(backups);
        String backup=backups.resolve("hotelsys-v"+version+"-"+System.currentTimeMillis()+".db").toString();
        read(c -> {execute(c,"VACUUM INTO ?",backup);return null;});
        if (version == 4) {
            tx(c -> { updateAvailability(c, schema); return null; });
            return;
        }
        try(var c=connect()){
            execute(c,"PRAGMA foreign_keys=OFF");c.setAutoCommit(false);
            try {importLegacy(c,schema);c.commit();}
            catch(SQLException | RuntimeException failure){c.rollback();throw failure;}
        }
    }

    private static void updateAvailability(Connection c, String schema) throws SQLException {
        var changed = java.util.Set.of("stay_timing", "room_available_insert", "room_available_update",
                "guest_insert_guard", "guest_dates_overlap");
        try (var template = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var statement = template.createStatement()) { statement.executeUpdate(schema); }
            for (Row object : schemaObjects(template)) {
                if (!changed.contains(object.text("name"))) { continue; }
                execute(c, "DROP " + object.text("type") + " IF EXISTS " + identifier(object.text("name")));
                execute(c, object.text("sql"));
            }
        }
        execute(c, "PRAGMA user_version=" + SCHEMA_VERSION);
    }

    private static List<Row> schemaObjects(Connection c) throws SQLException {
        return rows(c, "SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY rowid");
    }

    private static void importLegacy(Connection c,String schema) throws SQLException {
        List<Row> definitions;
        var columns=new LinkedHashMap<String,List<String>>();
        try(var template=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=template.createStatement()){
            statement.executeUpdate(schema);
            definitions=schemaObjects(template);
            for(Row definition:definitions){if(definition.text("type").equals("table")){
                String table=definition.text("name");columns.put(table,rows(template,"PRAGMA table_info("+identifier(table)+")").stream().map(r -> r.text("name")).toList());
            }}
        }
        var source=new LinkedHashMap<String,List<Row>>();
        List<Row> objects=rows(c,"SELECT type,name FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'");
        for(Row object:objects){if(object.text("type").equals("table")){
            String table=object.text("name");
            if(!columns.containsKey(table) && !table.equals("room_model_meal")){throw new SQLException("Table inconnue : "+table+". La base et sa sauvegarde sont conservées.");}
            source.put(table,rows(c,"SELECT * FROM "+identifier(table)));
        }}
        for(String type:List.of("trigger","view","table")){
            for(Row object:objects){if(object.text("type").equals(type)){execute(c,"DROP "+type+" "+identifier(object.text("name")));}}
        }
        for(Row definition:definitions){if(definition.text("type").equals("table")){execute(c,definition.text("sql"));}}
        long admin=source.getOrDefault("app_user",List.of()).stream().filter(r -> r.text("role").equals("ADMIN"))
                .sorted(java.util.Comparator.<Row>comparingLong(r -> -r.number("active")).thenComparingLong(r -> r.number("id"))).mapToLong(r -> r.number("id")).findFirst().orElse(0);
        for(var table:columns.entrySet()){
            for(Row row:source.getOrDefault(table.getKey(),List.of())){
                var values=new LinkedHashMap<>(row.values());
                if(table.getKey().equals("room_model") && !values.containsKey("nightly_price")){
                    long price=source.getOrDefault("room",List.of()).stream().filter(r -> r.number("model_id")==row.number("id"))
                            .min(java.util.Comparator.comparingLong(r -> r.number("id"))).map(r -> r.number("nightly_price")).orElse(0L);
                    values.put("nightly_price",price);
                }
                if(table.getKey().equals("app_user") && row.text("role").equals("ADMIN") && row.number("id")!=admin){values.put("role","WORKER");}
                List<String> fields=table.getValue().stream().filter(values::containsKey).toList();
                String names=String.join(",",fields.stream().map(Db::identifier).toList());
                String parameters=String.join(",",java.util.Collections.nCopies(fields.size(),"?"));
                execute(c,"INSERT INTO "+identifier(table.getKey())+" ("+names+") VALUES ("+parameters+")",fields.stream().map(values::get).toArray());
            }
        }
        if(!source.containsKey("hotel_meal")){
            for(Row rate:source.getOrDefault("room_model_meal",List.of()).stream().sorted(java.util.Comparator.comparingLong(r -> r.number("model_id"))).toList()){
                execute(c,"INSERT OR IGNORE INTO hotel_meal(meal,price) VALUES (?,?)",rate.text("meal"),rate.number("adult_price"));
            }
        }
        execute(c,"INSERT OR IGNORE INTO hotel(id,name,category) VALUES (1,'Mon hôtel','Non classé')");
        execute(c,"INSERT OR IGNORE INTO hotel_meal(meal,price) VALUES ('BREAKFAST',0),('LUNCH',0),('DINNER',0)");
        for(Row definition:definitions){if(!definition.text("type").equals("table")){execute(c,definition.text("sql"));}}
        if(!rows(c,"PRAGMA foreign_key_check").isEmpty() || !one(c,"PRAGMA integrity_check").text("integrity_check").equals("ok")){
            throw new SQLException("Conversion interrompue : relations invalides. La sauvegarde est conservée.");
        }
        execute(c,"PRAGMA user_version="+SCHEMA_VERSION);
    }

    private static String identifier(String name){return "\""+name.replace("\"","\"\"")+"\"";}

    private static String resource(String name) throws IOException {
        try (var input = Db.class.getResourceAsStream("/db/migrations/" + name)) {
            if (input == null) { throw new IOException("Migration introuvable : " + name); }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public Connection connect() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true); config.setBusyTimeout(5000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return DriverManager.getConnection("jdbc:sqlite:" + file, config.toProperties());
    }

    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    public <T> T read(Work<T> work) throws SQLException { try (var c = connect()) { return work.run(c); } }
    public <T> T tx(Work<T> work) throws SQLException {
        try (var c = connect()) {
            c.setAutoCommit(false);
            try { T value = work.run(c); c.commit(); return value; }
            catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    public static PreparedStatement statement(Connection c, String sql, Object... args) throws SQLException {
        var statement = c.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) { statement.setObject(i + 1, args[i]); }
            return statement;
        } catch (SQLException failure) { statement.close(); throw failure; }
    }
    public static List<Row> rows(Connection c, String sql, Object... args) throws SQLException {
        try (var statement = statement(c, sql, args); ResultSet result = statement.executeQuery()) {
            var rows = new ArrayList<Row>(); var metadata = result.getMetaData();
            while (result.next()) {
                var values = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) { values.put(metadata.getColumnLabel(i), result.getObject(i)); }
                rows.add(new Row(values));
            }
            return rows;
        }
    }
    public static Row one(Connection c, String sql, Object... args) throws SQLException {
        List<Row> rows = rows(c, sql, args);
        if (rows.isEmpty()) { throw new IllegalArgumentException("Enregistrement introuvable"); }
        return rows.getFirst();
    }
    public static long scalar(Connection c, String sql, Object... args) throws SQLException {
        return ((Number) one(c, sql, args).values().values().iterator().next()).longValue();
    }
    public static int execute(Connection c, String sql, Object... args) throws SQLException {
        try (var statement = statement(c, sql, args)) { return statement.executeUpdate(); }
    }
    public static long insert(Connection c, String sql, Object... args) throws SQLException {
        execute(c, sql, args); return scalar(c, "SELECT last_insert_rowid()");
    }
}
