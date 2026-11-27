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

    public void initialize() throws IOException, SQLException {
        Files.createDirectories(file.getParent());
        String sql;
        try (var input = Db.class.getResourceAsStream("/db/migrations/001_initial.sql")) {
            if (input == null) { throw new IOException("Schéma SQL introuvable"); }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        tx(c -> {
            long version = scalar(c, "PRAGMA user_version");
            if (version > 1) { throw new SQLException("La base nécessite une version plus récente de HotelSys"); }
            if (version == 0) {
                try (var statement = c.createStatement()) { statement.executeUpdate(sql); statement.executeUpdate("PRAGMA user_version=1"); }
            }
            return null;
        });
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
