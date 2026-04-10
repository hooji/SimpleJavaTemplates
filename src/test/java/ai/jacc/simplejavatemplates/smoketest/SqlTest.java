package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.*;

import ai.jacc.simplejavatemplates.TemplateException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive test suite for SimpleJavaTemplates SQL methods.
 * Must be run with: java -javaagent:SimpleJavaTemplates.jar -cp ... SqlTest
 * Requires H2 on the classpath.
 */
public class SqlTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        setupTables(conn);

        testSqlWithConnection(conn);
        testQueryWithConnection(conn);
        testQueryRowsWithConnection(conn);
        testQueryFirstWithConnection(conn);
        testUpdateWithConnection(conn);
        testVoidInsertWithConnection(conn);
        testInsertAndReturnLongKeyWithConnection(conn);
        testLinkedHashMapColumnOrdering(conn);
        testNoConnectionVariants(conn);
        testSqlInjectionSafety(conn);
        testNullParameterValues(conn);
        testDollarEscaping(conn);
        testNoPlaceholders(conn);
        testMultiplePlaceholders(conn);
        testRepeatedVariable(conn);
        testDynamicTemplate(conn);
        testEmptyResults(conn);
        testErrorConditions(conn);
        testMissingConnectionError();

        conn.close();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void setupTables(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE users (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "name VARCHAR(100), " +
            "email VARCHAR(100), " +
            "status VARCHAR(20), " +
            "age INT)");
        stmt.execute("INSERT INTO users (name, email, status, age) " +
            "VALUES ('Alice', 'alice@example.com', 'active', 30)");
        stmt.execute("INSERT INTO users (name, email, status, age) " +
            "VALUES ('Bob', 'bob@example.com', 'inactive', 25)");
        stmt.execute("INSERT INTO users (name, email, status, age) " +
            "VALUES ('Charlie', 'charlie@example.com', 'active', 35)");
        stmt.close();
    }

    // ========================================================================
    // sql(Connection, String) — returns PreparedStatement
    // ========================================================================

    static void testSqlWithConnection(Connection conn) {
        section("sql(Connection, String)");

        // Basic query
        try {
            String status = "active";
            PreparedStatement ps = sql(conn, "SELECT * FROM users WHERE status = ${status}");
            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next()) count++;
            rs.close();
            ps.close();
            check("sql basic query", 2, count);
        } catch (Throwable e) { fail("sql basic query", e); }

        // Multiple parameters
        try {
            String status = "active";
            int minAge = 31;
            PreparedStatement ps = sql(conn,
                "SELECT * FROM users WHERE status = ${status} AND age > ${minAge}");
            ResultSet rs = ps.executeQuery();
            int count = 0;
            String name = null;
            while (rs.next()) {
                count++;
                name = rs.getString("name");
            }
            rs.close();
            ps.close();
            check("sql multi-param count", 1, count);
            check("sql multi-param name", "Charlie", name);
        } catch (Throwable e) { fail("sql multi-param", e); }
    }

    // ========================================================================
    // query(Connection, String) — returns ResultSet
    // ========================================================================

    static void testQueryWithConnection(Connection conn) {
        section("query(Connection, String)");

        try {
            String name = "Bob";
            ResultSet rs = query(conn, "SELECT email FROM users WHERE name = ${name}");
            check("query has result", true, rs.next());
            check("query email", "bob@example.com", rs.getString("email"));
            rs.close();
        } catch (Throwable e) { fail("query basic", e); }
    }

    // ========================================================================
    // queryRows(Connection, String) — returns List<LinkedHashMap<String, Object>>
    // ========================================================================

    static void testQueryRowsWithConnection(Connection conn) {
        section("queryRows(Connection, String)");

        // Get all active users
        try {
            String status = "active";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT name, age FROM users WHERE status = ${status} ORDER BY age");
            check("queryRows count", 2, rows.size());
            check("queryRows first name", "Alice", rows.get(0).get("NAME"));
            check("queryRows second name", "Charlie", rows.get(1).get("NAME"));
            check("queryRows first age", 30, rows.get(0).get("AGE"));
        } catch (Throwable e) { fail("queryRows active users", e); }

        // Column alias
        try {
            String status = "active";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT name AS full_name FROM users WHERE status = ${status} ORDER BY name");
            check("queryRows alias key", true, rows.get(0).containsKey("FULL_NAME"));
            check("queryRows alias value", "Alice", rows.get(0).get("FULL_NAME"));
        } catch (Throwable e) { fail("queryRows alias", e); }
    }

    // ========================================================================
    // queryFirst(Connection, String) — returns Map or null
    // ========================================================================

    static void testQueryFirstWithConnection(Connection conn) {
        section("queryFirst(Connection, String)");

        // Found
        try {
            String name = "Alice";
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT email, age FROM users WHERE name = ${name}");
            check("queryFirst found", true, row != null);
            check("queryFirst email", "alice@example.com", row.get("EMAIL"));
            check("queryFirst age", 30, row.get("AGE"));
        } catch (Throwable e) { fail("queryFirst found", e); }

        // Not found — returns null
        try {
            String name = "Nobody";
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT * FROM users WHERE name = ${name}");
            check("queryFirst not found", null, row);
        } catch (Throwable e) { fail("queryFirst not found", e); }
    }

    // ========================================================================
    // update(Connection, String) — returns affected row count
    // ========================================================================

    static void testUpdateWithConnection(Connection conn) {
        section("update(Connection, String)");

        try {
            String newStatus = "suspended";
            String name = "Bob";
            int count = update(conn,
                "UPDATE users SET status = ${newStatus} WHERE name = ${name}");
            check("update count", 1, count);

            // Verify the update
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT status FROM users WHERE name = ${name}");
            check("update verify", "suspended", row.get("STATUS"));

            // Revert
            String revertStatus = "inactive";
            update(conn, "UPDATE users SET status = ${revertStatus} WHERE name = ${name}");
        } catch (Throwable e) { fail("update", e); }

        // Update multiple rows
        try {
            String status = "active";
            int newAge = 99;
            int count = update(conn,
                "UPDATE users SET age = ${newAge} WHERE status = ${status}");
            check("update multi-row count", 2, count);

            // Revert
            int age30 = 30;
            String nameAlice = "Alice";
            update(conn, "UPDATE users SET age = ${age30} WHERE name = ${nameAlice}");
            int age35 = 35;
            String nameCharlie = "Charlie";
            update(conn, "UPDATE users SET age = ${age35} WHERE name = ${nameCharlie}");
        } catch (Throwable e) { fail("update multi-row", e); }
    }

    // ========================================================================
    // insert(Connection, String) — void, no generated key
    // ========================================================================

    static void testVoidInsertWithConnection(Connection conn) {
        section("insert(Connection, String) — void");

        try {
            String name = "Diana";
            String email = "diana@example.com";
            String status = "active";
            int age = 28;
            insert(conn,
                "INSERT INTO users (name, email, status, age) " +
                "VALUES (${name}, ${email}, ${status}, ${age})");

            // Verify the insert
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT name, email FROM users WHERE name = ${name}");
            check("void insert verify name", "Diana", row.get("NAME"));
            check("void insert verify email", "diana@example.com", row.get("EMAIL"));

            // Clean up
            update(conn, "DELETE FROM users WHERE name = ${name}");
        } catch (Throwable e) { fail("void insert", e); }
    }

    // ========================================================================
    // insertAndReturnLongKey(Connection, String) — returns generated key
    // ========================================================================

    static void testInsertAndReturnLongKeyWithConnection(Connection conn) {
        section("insertAndReturnLongKey(Connection, String)");

        try {
            String name = "Diana";
            String email = "diana@example.com";
            String status = "active";
            int age = 28;
            long newId = insertAndReturnLongKey(conn,
                "INSERT INTO users (name, email, status, age) " +
                "VALUES (${name}, ${email}, ${status}, ${age})");
            check("insertAndReturnLongKey returns key", true, newId > 0);

            // Verify the insert
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT name, email FROM users WHERE name = ${name}");
            check("insertAndReturnLongKey verify name", "Diana", row.get("NAME"));
            check("insertAndReturnLongKey verify email", "diana@example.com", row.get("EMAIL"));

            // Clean up
            update(conn, "DELETE FROM users WHERE name = ${name}");
        } catch (Throwable e) { fail("insertAndReturnLongKey", e); }
    }

    // ========================================================================
    // LinkedHashMap column ordering
    // ========================================================================

    static void testLinkedHashMapColumnOrdering(Connection conn) {
        section("LinkedHashMap column ordering");

        // queryFirst preserves column order
        try {
            String name = "Alice";
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT name, email, status, age FROM users WHERE name = ${name}");
            Iterator<String> keys = row.keySet().iterator();
            check("queryFirst col 1", "NAME", keys.next());
            check("queryFirst col 2", "EMAIL", keys.next());
            check("queryFirst col 3", "STATUS", keys.next());
            check("queryFirst col 4", "AGE", keys.next());
        } catch (Throwable e) { fail("queryFirst column ordering", e); }

        // queryRows preserves column order
        try {
            String status = "active";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT age, name FROM users WHERE status = ${status} ORDER BY name");
            Iterator<String> keys = rows.get(0).keySet().iterator();
            check("queryRows col 1", "AGE", keys.next());
            check("queryRows col 2", "NAME", keys.next());
        } catch (Throwable e) { fail("queryRows column ordering", e); }
    }

    // ========================================================================
    // No-Connection variants (pulls conn from locals)
    // ========================================================================

    static void testNoConnectionVariants(Connection conn) {
        section("No-Connection variants (implicit conn)");

        // sql() without explicit connection
        try {
            String status = "active";
            PreparedStatement ps = sql("SELECT * FROM users WHERE status = ${status}");
            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next()) count++;
            rs.close();
            ps.close();
            check("sql() implicit conn", 2, count);
        } catch (Throwable e) { fail("sql() implicit conn", e); }

        // query() without explicit connection
        try {
            String name = "Alice";
            ResultSet rs = query("SELECT email FROM users WHERE name = ${name}");
            check("query() implicit conn", true, rs.next());
            check("query() implicit conn email", "alice@example.com", rs.getString("email"));
            rs.close();
        } catch (Throwable e) { fail("query() implicit conn", e); }

        // queryRows() without explicit connection
        try {
            String status = "active";
            List<LinkedHashMap<String, Object>> rows = queryRows(
                "SELECT name FROM users WHERE status = ${status} ORDER BY name");
            check("queryRows() implicit conn count", 2, rows.size());
            check("queryRows() implicit conn first", "Alice", rows.get(0).get("NAME"));
        } catch (Throwable e) { fail("queryRows() implicit conn", e); }

        // queryFirst() without explicit connection
        try {
            String name = "Charlie";
            LinkedHashMap<String, Object> row = queryFirst(
                "SELECT age FROM users WHERE name = ${name}");
            check("queryFirst() implicit conn", 35, row.get("AGE"));
        } catch (Throwable e) { fail("queryFirst() implicit conn", e); }

        // update() without explicit connection
        try {
            String newStatus = "paused";
            String name = "Bob";
            int count = update(
                "UPDATE users SET status = ${newStatus} WHERE name = ${name}");
            check("update() implicit conn", 1, count);

            // Revert
            String revertStatus = "inactive";
            update(conn, "UPDATE users SET status = ${revertStatus} WHERE name = ${name}");
        } catch (Throwable e) { fail("update() implicit conn", e); }

        // insert() (void) without explicit connection
        try {
            String name = "Eve";
            String email = "eve@example.com";
            String status = "active";
            int age = 22;
            insert("INSERT INTO users (name, email, status, age) " +
                "VALUES (${name}, ${email}, ${status}, ${age})");

            LinkedHashMap<String, Object> row = queryFirst(
                "SELECT name FROM users WHERE name = ${name}");
            check("insert() implicit conn", "Eve", row.get("NAME"));

            // Clean up
            update(conn, "DELETE FROM users WHERE name = ${name}");
        } catch (Throwable e) { fail("insert() implicit conn", e); }

        // insertAndReturnLongKey() without explicit connection
        try {
            String name = "Frank";
            String email = "frank@example.com";
            String status = "active";
            int age = 33;
            long newId = insertAndReturnLongKey(
                "INSERT INTO users (name, email, status, age) " +
                "VALUES (${name}, ${email}, ${status}, ${age})");
            check("insertAndReturnLongKey() implicit conn", true, newId > 0);

            // Clean up
            update(conn, "DELETE FROM users WHERE name = ${name}");
        } catch (Throwable e) { fail("insertAndReturnLongKey() implicit conn", e); }
    }

    // ========================================================================
    // SQL injection safety
    // ========================================================================

    static void testSqlInjectionSafety(Connection conn) {
        section("SQL injection safety");

        // Attempt SQL injection via parameter value
        try {
            String malicious = "'; DROP TABLE users; --";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT * FROM users WHERE name = ${malicious}");
            check("injection returns 0 rows", 0, rows.size());

            // Verify table still exists
            List<LinkedHashMap<String, Object>> allRows = queryRows(conn, "SELECT * FROM users");
            check("table survives injection", true, allRows.size() >= 3);
        } catch (Throwable e) { fail("sql injection safety", e); }

        // Parameter with SQL keywords
        try {
            String keyword = "SELECT * FROM users";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT * FROM users WHERE name = ${keyword}");
            check("sql keyword as value", 0, rows.size());
        } catch (Throwable e) { fail("sql keyword as value", e); }
    }

    // ========================================================================
    // NULL parameter values
    // ========================================================================

    static void testNullParameterValues(Connection conn) {
        section("NULL parameter values");

        try {
            // Insert a row with null email
            String name = "NullTester";
            String email = null;
            String status = "active";
            int age = 40;
            insert(conn,
                "INSERT INTO users (name, email, status, age) " +
                "VALUES (${name}, ${email}, ${status}, ${age})");

            // Query it back
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT email FROM users WHERE name = ${name}");
            check("null param insert/query", null, row.get("EMAIL"));

            // Clean up
            update(conn, "DELETE FROM users WHERE name = ${name}");
        } catch (Throwable e) { fail("null param", e); }
    }

    // ========================================================================
    // Dollar sign escaping in SQL
    // ========================================================================

    static void testDollarEscaping(Connection conn) {
        section("Dollar escaping in SQL");

        // $$ in SQL template becomes literal $ in the query text
        try {
            String name = "Alice";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT name, '$$' AS dollar_sign FROM users WHERE name = ${name}");
            check("$$ in SQL", "$", rows.get(0).get("DOLLAR_SIGN"));
        } catch (Throwable e) { fail("$$ in SQL", e); }
    }

    // ========================================================================
    // No placeholders
    // ========================================================================

    static void testNoPlaceholders(Connection conn) {
        section("No placeholders in SQL");

        try {
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT COUNT(*) AS cnt FROM users");
            check("no placeholders", true,
                ((Number) rows.get(0).get("CNT")).intValue() >= 3);
        } catch (Throwable e) { fail("no placeholders", e); }
    }

    // ========================================================================
    // Multiple placeholders
    // ========================================================================

    static void testMultiplePlaceholders(Connection conn) {
        section("Multiple placeholders");

        try {
            String status = "active";
            int minAge = 25;
            int maxAge = 32;
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT name FROM users WHERE status = ${status} " +
                "AND age >= ${minAge} AND age <= ${maxAge} ORDER BY name");
            check("multi-placeholder count", 1, rows.size());
            check("multi-placeholder name", "Alice", rows.get(0).get("NAME"));
        } catch (Throwable e) { fail("multi-placeholder", e); }
    }

    // ========================================================================
    // Same variable used multiple times
    // ========================================================================

    static void testRepeatedVariable(Connection conn) {
        section("Repeated variable in SQL");

        try {
            String val = "Alice";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT * FROM users WHERE name = ${val} OR email LIKE ${val}");
            check("repeated var count", 1, rows.size());
            check("repeated var name", "Alice", rows.get(0).get("NAME"));
        } catch (Throwable e) { fail("repeated var", e); }
    }

    // ========================================================================
    // Dynamic template (non-literal)
    // ========================================================================

    static void testDynamicTemplate(Connection conn) {
        section("Dynamic SQL template");

        try {
            String name = "Bob";
            String tmpl = "SELECT age FROM users WHERE name = ${name}";
            LinkedHashMap<String, Object> row = queryFirst(conn, tmpl);
            check("dynamic template", 25, row.get("AGE"));
        } catch (Throwable e) { fail("dynamic template", e); }
    }

    // ========================================================================
    // Empty results
    // ========================================================================

    static void testEmptyResults(Connection conn) {
        section("Empty results");

        // queryRows with no matches
        try {
            String status = "deleted";
            List<LinkedHashMap<String, Object>> rows = queryRows(conn,
                "SELECT * FROM users WHERE status = ${status}");
            check("queryRows empty", 0, rows.size());
        } catch (Throwable e) { fail("queryRows empty", e); }

        // queryFirst with no matches
        try {
            String name = "Nobody";
            LinkedHashMap<String, Object> row = queryFirst(conn,
                "SELECT * FROM users WHERE name = ${name}");
            check("queryFirst empty", null, row);
        } catch (Throwable e) { fail("queryFirst empty", e); }

        // update with no matches
        try {
            String name = "Nobody";
            int count = update(conn,
                "UPDATE users SET age = 0 WHERE name = ${name}");
            check("update empty", 0, count);
        } catch (Throwable e) { fail("update empty", e); }
    }

    // ========================================================================
    // Error conditions
    // ========================================================================

    static void testErrorConditions(Connection conn) {
        section("Error conditions");

        // Null template
        try {
            queryRows(conn, null);
            fail("null template", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("null template error", true, e.getMessage().contains("null"));
        } catch (Throwable e) { fail("null template", e); }

        // Unknown variable
        try {
            queryRows(conn, "SELECT * FROM users WHERE id = ${nonexistent}");
            fail("unknown var", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unknown var error", true,
                e.getMessage().contains("nonexistent") && e.getMessage().contains("not found"));
        } catch (Throwable e) { fail("unknown var", e); }

        // Unclosed placeholder
        try {
            int x = 1;
            queryRows(conn, "SELECT * FROM users WHERE id = ${unclosed");
            fail("unclosed placeholder", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unclosed error", true, e.getMessage().contains("unclosed"));
        } catch (Throwable e) { fail("unclosed placeholder", e); }

        // Empty placeholder
        try {
            int x = 1;
            queryRows(conn, "SELECT * FROM users WHERE id = ${}");
            fail("empty placeholder", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("empty placeholder error", true, e.getMessage().contains("not a valid"));
        } catch (Throwable e) { fail("empty placeholder", e); }

        // Invalid identifier
        try {
            int x = 1;
            queryRows(conn, "SELECT * FROM users WHERE id = ${123bad}");
            fail("invalid ident", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("invalid ident error", true, e.getMessage().contains("not a valid"));
        } catch (Throwable e) { fail("invalid ident", e); }

        // Bad SQL (should wrap SQLException)
        try {
            int x = 1;
            queryRows(conn, "SELECT * FROM nonexistent_table WHERE id = ${x}");
            fail("bad SQL", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("bad SQL error", true, e.getMessage().contains("SQL error"));
        } catch (Throwable e) { fail("bad SQL", e); }
    }

    // ========================================================================
    // Missing connection error (no-conn variant with no Connection in scope)
    // ========================================================================

    static void testMissingConnectionError() {
        section("Missing connection error");

        try {
            String userId = "123";
            queryRows("SELECT * FROM users WHERE id = ${userId}");
            fail("missing conn", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("missing conn message mentions Connection", true,
                e.getMessage().contains("Connection"));
            check("missing conn message mentions overload", true,
                e.getMessage().contains("queryRows(conn, template)"));
            check("missing conn lists available locals", true,
                e.getMessage().contains("userId"));
        } catch (Throwable e) { fail("missing conn", e); }
    }

    // ========================================================================
    // Test harness (mirrors SmokeTest style)
    // ========================================================================

    static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    static void check(String label, Object expected, Object actual) {
        if (expected instanceof Boolean) {
            if (Boolean.TRUE.equals(expected) && Boolean.TRUE.equals(actual)) {
                System.out.println("  PASS " + label);
                passed++;
                return;
            }
        }
        if (expected == null && actual == null) {
            System.out.println("  PASS " + label + ": null");
            passed++;
        } else if (expected != null && expected.equals(actual)) {
            System.out.println("  PASS " + label + ": " + actual);
            passed++;
        } else {
            System.err.println("  FAIL " + label + ": expected <" + expected +
                "> but got <" + actual + ">");
            failed++;
        }
    }

    static void fail(String label, Throwable e) {
        System.err.println("  FAIL " + label + ": " + e);
        e.printStackTrace(System.err);
        failed++;
    }
}
