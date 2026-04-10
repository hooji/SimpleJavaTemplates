package ai.jacc.simplejavatemplates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Template {

    private Template() { }

    @RequiresCallerLocalVariableDetails
    public static String f(String template) {
        throw new AgentNotLoadedException();
    }

    public static String $___f__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        if (template == null) {
            throw new TemplateException("Template string must not be null");
        }

        StringBuilder result = new StringBuilder(template.length());
        int len = template.length();
        int i = 0;

        while (i < len) {
            char c = template.charAt(i);

            if (c == '$') {
                if (i + 1 < len && template.charAt(i + 1) == '$') {
                    // $$ -> literal $
                    result.append('$');
                    i += 2;
                } else if (i + 2 < len && template.charAt(i + 1) == '{'
                           && template.charAt(i + 2) == '{') {
                    // ${{name}} — nested template: look up value, treat it as
                    // a template, and interpolate it with the same variable map.
                    int closeDouble = template.indexOf("}}", i + 3);
                    if (closeDouble == -1) {
                        throw new TemplateException(
                            "Malformed nested placeholder: unclosed '${{' at index " + i +
                            " in template: " + template);
                    }
                    String content = template.substring(i + 3, closeDouble);
                    int colonIdx = content.indexOf(':');
                    String name;
                    String formatSpec;
                    if (colonIdx >= 0) {
                        name = content.substring(0, colonIdx);
                        formatSpec = content.substring(colonIdx + 1);
                    } else {
                        name = content;
                        formatSpec = null;
                    }
                    Object value = lookupName(name, content, localVarValues, template);
                    // Recursively interpolate the value as a template
                    String innerTemplate = String.valueOf(value);
                    String interpolated = $___f__Ljava_lang_String_2___(localVarValues, innerTemplate);
                    if (formatSpec != null) {
                        try {
                            result.append(String.format("%" + formatSpec, interpolated));
                        } catch (java.util.IllegalFormatException e) {
                            throw new TemplateException(
                                "Format error for '${{" + content + "}}': " + e.getMessage(), e);
                        }
                    } else {
                        result.append(interpolated);
                    }
                    i = closeDouble + 2; // skip past }}
                } else if (i + 1 < len && template.charAt(i + 1) == '{') {
                    // ${name} — simple placeholder
                    int closeBrace = template.indexOf('}', i + 2);
                    if (closeBrace == -1) {
                        throw new TemplateException(
                            "Malformed placeholder: unclosed '${' at index " + i +
                            " in template: " + template);
                    }
                    String content = template.substring(i + 2, closeBrace);
                    int colonIdx = content.indexOf(':');
                    String name;
                    String formatSpec;
                    if (colonIdx >= 0) {
                        name = content.substring(0, colonIdx);
                        formatSpec = content.substring(colonIdx + 1);
                    } else {
                        name = content;
                        formatSpec = null;
                    }
                    Object value = lookupName(name, content, localVarValues, template);
                    if (formatSpec != null) {
                        try {
                            result.append(String.format("%" + formatSpec, value));
                        } catch (java.util.IllegalFormatException e) {
                            throw new TemplateException(
                                "Format error for '${" + content + "}': " + e.getMessage(), e);
                        }
                    } else {
                        result.append(String.valueOf(value));
                    }
                    i = closeBrace + 1;
                } else {
                    // $ followed by something other than { or $ — pass through
                    result.append(c);
                    i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    // ========== SQL template stubs (with Connection) ==========

    @RequiresCallerLocalVariableDetails
    public static PreparedStatement sql(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static ResultSet query(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static List<Map<String, Object>> queryRows(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static Map<String, Object> queryFirst(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static int update(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static long insert(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    // ========== SQL template stubs (without Connection — pulls from locals) ==========

    @RequiresCallerLocalVariableDetails
    public static PreparedStatement sql(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static ResultSet query(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static List<Map<String, Object>> queryRows(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static Map<String, Object> queryFirst(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static int update(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static long insert(String template) {
        throw new AgentNotLoadedException();
    }

    // ========== SQL synthetic implementations (Connection variants) ==========

    public static PreparedStatement $___sql__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        return prepareSql(conn, template, localVarValues, false);
    }

    public static ResultSet $___query__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, false);
        try {
            ps.closeOnCompletion();
            return ps.executeQuery();
        } catch (SQLException e) {
            try { ps.close(); } catch (SQLException ignore) { }
            throw new TemplateException("SQL error executing query: " + e.getMessage(), e);
        }
    }

    public static List<Map<String, Object>> $___queryRows__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, false);
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            String[] columnNames = new String[columnCount];
            for (int c = 0; c < columnCount; c++) {
                columnNames[c] = meta.getColumnLabel(c + 1);
            }
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            while (rs.next()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
                for (int c = 0; c < columnCount; c++) {
                    row.put(columnNames[c], rs.getObject(c + 1));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new TemplateException("SQL error executing query: " + e.getMessage(), e);
        } finally {
            if (rs != null) { try { rs.close(); } catch (SQLException ignore) { } }
            try { ps.close(); } catch (SQLException ignore) { }
        }
    }

    public static Map<String, Object> $___queryFirst__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, false);
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
            for (int c = 0; c < columnCount; c++) {
                row.put(meta.getColumnLabel(c + 1), rs.getObject(c + 1));
            }
            return row;
        } catch (SQLException e) {
            throw new TemplateException("SQL error executing query: " + e.getMessage(), e);
        } finally {
            if (rs != null) { try { rs.close(); } catch (SQLException ignore) { } }
            try { ps.close(); } catch (SQLException ignore) { }
        }
    }

    public static int $___update__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, false);
        try {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new TemplateException("SQL error executing update: " + e.getMessage(), e);
        } finally {
            try { ps.close(); } catch (SQLException ignore) { }
        }
    }

    public static long $___insert__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, true);
        ResultSet keys = null;
        try {
            ps.executeUpdate();
            keys = ps.getGeneratedKeys();
            if (!keys.next()) {
                throw new TemplateException(
                    "insert() expected a generated key but none was returned. " +
                    "Ensure the table has an auto-generated key column.");
            }
            return keys.getLong(1);
        } catch (SQLException e) {
            throw new TemplateException("SQL error executing insert: " + e.getMessage(), e);
        } finally {
            if (keys != null) { try { keys.close(); } catch (SQLException ignore) { } }
            try { ps.close(); } catch (SQLException ignore) { }
        }
    }

    // ========== SQL synthetic implementations (no-Connection variants) ==========

    public static PreparedStatement $___sql__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "sql");
        return $___sql__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static ResultSet $___query__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "query");
        return $___query__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static List<Map<String, Object>> $___queryRows__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "queryRows");
        return $___queryRows__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static Map<String, Object> $___queryFirst__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "queryFirst");
        return $___queryFirst__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static int $___update__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "update");
        return $___update__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static long $___insert__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "insert");
        return $___insert__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    // ========== SQL helper methods ==========

    private static final class SqlParseResult {
        final String sql;
        final List<Object> values;
        SqlParseResult(String sql, List<Object> values) {
            this.sql = sql;
            this.values = values;
        }
    }

    private static SqlParseResult parseSqlTemplate(String template,
                                                   Map<String, Object> localVarValues) {
        if (template == null) {
            throw new TemplateException("SQL template string must not be null");
        }

        StringBuilder sql = new StringBuilder(template.length());
        List<Object> values = new ArrayList<Object>();
        int len = template.length();
        int i = 0;

        while (i < len) {
            char c = template.charAt(i);

            if (c == '$') {
                if (i + 1 < len && template.charAt(i + 1) == '$') {
                    sql.append('$');
                    i += 2;
                } else if (i + 1 < len && template.charAt(i + 1) == '{') {
                    int closeBrace = template.indexOf('}', i + 2);
                    if (closeBrace == -1) {
                        throw new TemplateException(
                            "Malformed placeholder: unclosed '${' at index " + i +
                            " in SQL template: " + template);
                    }
                    String name = template.substring(i + 2, closeBrace);
                    if (name.isEmpty() || !isValidJavaIdentifier(name)) {
                        throw new TemplateException(
                            "Malformed placeholder: '${" + name +
                            "}' is not a valid Java identifier in SQL template: " + template);
                    }
                    if (!localVarValues.containsKey(name)) {
                        StringBuilder available = new StringBuilder("Available names: ");
                        if (localVarValues.isEmpty()) {
                            available.append("(none)");
                        } else {
                            boolean first = true;
                            for (String key : localVarValues.keySet()) {
                                if (!first) available.append(", ");
                                available.append(key);
                                first = false;
                            }
                        }
                        throw new TemplateException(
                            "Name '${" + name + "}' not found in caller's local variables. " +
                            available.toString());
                    }
                    sql.append('?');
                    values.add(localVarValues.get(name));
                    i = closeBrace + 1;
                } else {
                    sql.append(c);
                    i++;
                }
            } else {
                sql.append(c);
                i++;
            }
        }

        return new SqlParseResult(sql.toString(), values);
    }

    private static Connection findConnection(Map<String, Object> localVarValues,
                                             String methodName) {
        Object candidate = localVarValues.get("conn");
        if (candidate instanceof Connection) {
            return (Connection) candidate;
        }
        candidate = localVarValues.get("connection");
        if (candidate instanceof Connection) {
            return (Connection) candidate;
        }
        for (Map.Entry<String, Object> entry : localVarValues.entrySet()) {
            if (entry.getValue() instanceof Connection) {
                return (Connection) entry.getValue();
            }
        }

        StringBuilder available = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : localVarValues.entrySet()) {
            if (!first) available.append(", ");
            String typeName = entry.getValue() == null ? "null" :
                entry.getValue().getClass().getSimpleName();
            available.append(entry.getKey()).append(" (").append(typeName).append(")");
            first = false;
        }
        throw new TemplateException(
            methodName + "() requires a java.sql.Connection in scope. " +
            "No local variable of type Connection found. Available locals: " +
            (first ? "(none)" : available.toString()) +
            ". Either declare a Connection local, or use the explicit overload: " +
            methodName + "(conn, template).");
    }

    private static PreparedStatement prepareSql(Connection conn, String template,
                                                Map<String, Object> localVarValues,
                                                boolean returnGeneratedKeys) {
        SqlParseResult parsed = parseSqlTemplate(template, localVarValues);
        try {
            PreparedStatement ps;
            if (returnGeneratedKeys) {
                ps = conn.prepareStatement(parsed.sql, Statement.RETURN_GENERATED_KEYS);
            } else {
                ps = conn.prepareStatement(parsed.sql);
            }
            try {
                for (int i = 0; i < parsed.values.size(); i++) {
                    ps.setObject(i + 1, parsed.values.get(i));
                }
                return ps;
            } catch (SQLException e) {
                try { ps.close(); } catch (SQLException ignore) { }
                throw e;
            }
        } catch (SQLException e) {
            throw new TemplateException("SQL error preparing statement: " + e.getMessage(), e);
        }
    }

    /**
     * Looks up a variable name in the map, throwing TemplateException if the
     * name is invalid or not found.
     */
    private static Object lookupName(String name, String placeholderContent,
                                     Map<String, Object> localVarValues,
                                     String template) {
        if (name.isEmpty() || !isValidJavaIdentifier(name)) {
            throw new TemplateException(
                "Malformed placeholder: '${" + placeholderContent +
                "}' is not a valid Java identifier in template: " + template);
        }
        if (!localVarValues.containsKey(name)) {
            StringBuilder available = new StringBuilder();
            available.append("Available names: ");
            if (localVarValues.isEmpty()) {
                available.append("(none)");
            } else {
                boolean first = true;
                for (String key : localVarValues.keySet()) {
                    if (!first) available.append(", ");
                    available.append(key);
                    first = false;
                }
            }
            throw new TemplateException(
                "Name '${" + name + "}' not found in caller's local variables. " +
                available.toString() +
                ". The variable may not exist at this call site, or it may have " +
                "been optimized away before the agent saw the class.");
        }
        return localVarValues.get(name);
    }

    private static boolean isValidJavaIdentifier(String s) {
        if (s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }
}
