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

    private static final TemplateExpander GLOBAL_EXPANDER = new TemplateExpander();

    public static TemplateExpander getGlobalTemplateExpanderInstance() {
        return GLOBAL_EXPANDER;
    }

    @RequiresCallerLocalVariableDetails
    public static String f(String template) {
        throw new AgentNotLoadedException();
    }

    public static String $___f__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        return GLOBAL_EXPANDER.expand(localVarValues, template);
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
    public static List<LinkedHashMap<String, Object>> queryRows(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static LinkedHashMap<String, Object> queryFirst(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static int update(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static void insert(Connection conn, String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static long insertAndReturnLongKey(Connection conn, String template) {
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
    public static List<LinkedHashMap<String, Object>> queryRows(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static LinkedHashMap<String, Object> queryFirst(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static int update(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static void insert(String template) {
        throw new AgentNotLoadedException();
    }

    @RequiresCallerLocalVariableDetails
    public static long insertAndReturnLongKey(String template) {
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

    public static List<LinkedHashMap<String, Object>> $___queryRows__Ljava_sql_Connection_2Ljava_lang_String_2___(
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
            List<LinkedHashMap<String, Object>> rows = new ArrayList<LinkedHashMap<String, Object>>();
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

    public static LinkedHashMap<String, Object> $___queryFirst__Ljava_sql_Connection_2Ljava_lang_String_2___(
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

    public static void $___insert__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, false);
        try {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new TemplateException("SQL error executing insert: " + e.getMessage(), e);
        } finally {
            try { ps.close(); } catch (SQLException ignore) { }
        }
    }

    public static long $___insertAndReturnLongKey__Ljava_sql_Connection_2Ljava_lang_String_2___(
            Map<String, Object> localVarValues, Connection conn, String template) {
        PreparedStatement ps = prepareSql(conn, template, localVarValues, true);
        ResultSet keys = null;
        try {
            ps.executeUpdate();
            keys = ps.getGeneratedKeys();
            if (!keys.next()) {
                throw new TemplateException(
                    "insertAndReturnLongKey() expected a generated key but none was returned. " +
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

    public static List<LinkedHashMap<String, Object>> $___queryRows__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "queryRows");
        return $___queryRows__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static LinkedHashMap<String, Object> $___queryFirst__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "queryFirst");
        return $___queryFirst__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static int $___update__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "update");
        return $___update__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static void $___insert__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "insert");
        $___insert__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
    }

    public static long $___insertAndReturnLongKey__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        Connection conn = findConnection(localVarValues, "insertAndReturnLongKey");
        return $___insertAndReturnLongKey__Ljava_sql_Connection_2Ljava_lang_String_2___(localVarValues, conn, template);
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

        boolean dollarReq = GLOBAL_EXPANDER.isRequireLeadingDollar();
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
                    String expr = template.substring(i + 2, closeBrace);
                    bindSqlValue(expr, localVarValues, sql, values, template);
                    i = closeBrace + 1;
                } else {
                    sql.append(c);
                    i++;
                }
            } else if (c == '{' && !dollarReq) {
                if (i + 1 < len && template.charAt(i + 1) == '{') {
                    sql.append('{');
                    i += 2;
                } else {
                    int closeBrace = template.indexOf('}', i + 1);
                    if (closeBrace == -1) {
                        throw new TemplateException(
                            "Malformed placeholder: unclosed '{' at index " + i +
                            " in SQL template: " + template);
                    }
                    String expr = template.substring(i + 1, closeBrace);
                    bindSqlValue(expr, localVarValues, sql, values, template);
                    i = closeBrace + 1;
                }
            } else if (c == '}' && !dollarReq) {
                if (i + 1 < len && template.charAt(i + 1) == '}') {
                    sql.append('}');
                    i += 2;
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

    private static void bindSqlValue(String expr, Map<String, Object> localVarValues,
                                     StringBuilder sql, List<Object> values,
                                     String template) {
        Object value = GLOBAL_EXPANDER.evaluateExpression(expr, localVarValues, template);

        if (value != null && TemplateExpander.isContainer(value)) {
            List<Object> elements = TemplateExpander.flattenContainer(value);
            if (elements.isEmpty()) {
                sql.append("(NULL)");
            } else {
                sql.append('(');
                for (int j = 0; j < elements.size(); j++) {
                    if (j > 0) sql.append(", ");
                    sql.append('?');
                    values.add(elements.get(j));
                }
                sql.append(')');
            }
        } else {
            sql.append('?');
            values.add(value);
        }
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

    private static boolean isValidJavaIdentifier(String s) {
        return TemplateExpander.isValidJavaIdentifier(s);
    }
}
