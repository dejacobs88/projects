package com.sbom.store;

import com.sbom.model.Component;
import com.sbom.model.QueryResult;
import com.sbom.model.SbomDocument;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed store. Schema:
 *   documents          1 row per SBOM (unique by serial_number)
 *   components         1 row per package in a document, indexed on (name, version)
 *   component_licenses 1 row per license on a component, indexed on license
 */
public class SqliteSbomRepository implements SbomRepository {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS documents (
                id            INTEGER PRIMARY KEY,
                name          TEXT NOT NULL,
                serial_number TEXT NOT NULL UNIQUE,
                source_file   TEXT,
                ingested_at   TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS components (
                id          INTEGER PRIMARY KEY,
                document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                name        TEXT NOT NULL COLLATE NOCASE,
                version     TEXT,
                purl        TEXT
            );
            CREATE TABLE IF NOT EXISTS component_licenses (
                component_id INTEGER NOT NULL REFERENCES components(id) ON DELETE CASCADE,
                license      TEXT NOT NULL COLLATE NOCASE
            );
            CREATE INDEX IF NOT EXISTS idx_components_name_version ON components(name, version);
            CREATE INDEX IF NOT EXISTS idx_components_document ON components(document_id);
            CREATE INDEX IF NOT EXISTS idx_licenses_license ON component_licenses(license);
            CREATE INDEX IF NOT EXISTS idx_licenses_component ON component_licenses(component_id);
            """;

    // Shared SELECT: one row per matching component, with all of its licenses joined into one column.
    private static final String SELECT = """
            SELECT d.name AS doc, c.name AS comp, c.version AS ver,
                   (SELECT group_concat(license, ', ') FROM component_licenses WHERE component_id = c.id) AS lic
            FROM components c JOIN documents d ON d.id = c.document_id
            """;

    private final Connection conn;

    public SqliteSbomRepository(String dbPath) {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.executeUpdate(SCHEMA);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open database " + dbPath, e);
        }
    }

    @Override
    public void save(SbomDocument doc) {
        try {
            conn.setAutoCommit(false);
            // Replace semantics: drop the old copy (cascades to components/licenses), then insert.
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM documents WHERE serial_number = ?")) {
                del.setString(1, doc.serialNumber());
                del.executeUpdate();
            }
            long docId;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO documents(name, serial_number, source_file, ingested_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, doc.name());
                ins.setString(2, doc.serialNumber());
                ins.setString(3, doc.sourceFile());
                ins.setString(4, Instant.now().toString());
                ins.executeUpdate();
                docId = generatedId(ins);
            }
            try (PreparedStatement comp = conn.prepareStatement(
                    "INSERT INTO components(document_id, name, version, purl) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement lic = conn.prepareStatement(
                    "INSERT INTO component_licenses(component_id, license) VALUES (?, ?)")) {
                for (Component c : doc.components()) {
                    if (c.name() == null) {
                        continue;
                    }
                    comp.setLong(1, docId);
                    comp.setString(2, c.name());
                    comp.setString(3, c.version());
                    comp.setString(4, c.purl());
                    comp.executeUpdate();
                    long compId = generatedId(comp);
                    for (String license : c.licenses()) {
                        lic.setLong(1, compId);
                        lic.setString(2, license);
                        lic.addBatch();
                    }
                }
                lic.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Failed to save " + doc.sourceFile(), e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // connection is unusable anyway
            }
        }
    }

    @Override
    public List<QueryResult> findByComponent(String name, String version) {
        String sql = SELECT + " WHERE c.name = ? AND (? IS NULL OR c.version = ?) ORDER BY doc, comp, ver";
        return query(sql, name, version, version);
    }

    @Override
    public List<QueryResult> findByLicense(String license) {
        String sql = SELECT
                + " WHERE c.id IN (SELECT component_id FROM component_licenses WHERE license = ?)"
                + " ORDER BY doc, comp, ver";
        return query(sql, license);
    }

    private List<QueryResult> query(String sql, String... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            List<QueryResult> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new QueryResult(
                            rs.getString("doc"), rs.getString("comp"), rs.getString("ver"), rs.getString("lic")));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    private static long generatedId(Statement st) throws SQLException {
        try (ResultSet keys = st.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // original exception is more useful
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // nothing useful to do on close
        }
    }
}
