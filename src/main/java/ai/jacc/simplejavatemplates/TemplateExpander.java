package ai.jacc.simplejavatemplates;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TemplateExpander {

    // === Options (volatile for thread-safe global instance) ===

    private volatile boolean requireLeadingDollar = false;
    private volatile boolean optionalPlaceholders = true;
    private volatile boolean expandContainers = true;
    private volatile boolean memberAccess = true;

    // === Constructors ===

    public TemplateExpander() {}

    // === Fluent setters ===

    public TemplateExpander setRequireLeadingDollar(boolean v) {
        this.requireLeadingDollar = v; return this;
    }
    public TemplateExpander setOptionalPlaceholders(boolean v) {
        this.optionalPlaceholders = v; return this;
    }
    public TemplateExpander setExpandContainers(boolean v) {
        this.expandContainers = v; return this;
    }
    public TemplateExpander setMemberAccess(boolean v) {
        this.memberAccess = v; return this;
    }

    // === Getters ===

    public boolean isRequireLeadingDollar() { return requireLeadingDollar; }
    public boolean isOptionalPlaceholders() { return optionalPlaceholders; }
    public boolean isExpandContainers() { return expandContainers; }
    public boolean isMemberAccess() { return memberAccess; }

    // === Instance template method ===

    @RequiresCallerLocalVariableDetails
    public String f(String template) {
        throw new AgentNotLoadedException();
    }

    public String $___f__Ljava_lang_String_2___(
            Map<String, Object> localVarValues, String template) {
        return expand(localVarValues, template);
    }

    // === Core expansion ===

    String expand(Map<String, Object> locals, String template) {
        if (template == null) {
            throw new TemplateException("Template string must not be null");
        }

        boolean dollarReq = this.requireLeadingDollar;
        StringBuilder result = new StringBuilder(template.length());
        int len = template.length();
        int i = 0;

        while (i < len) {
            char c = template.charAt(i);

            if (c == '$') {
                if (i + 1 < len && template.charAt(i + 1) == '$') {
                    result.append('$');
                    i += 2;
                } else if (i + 2 < len && template.charAt(i + 1) == '{'
                           && template.charAt(i + 2) == '{') {
                    i = expandNestedTemplate(template, i, locals, result);
                } else if (i + 1 < len && template.charAt(i + 1) == '{') {
                    i = expandPlaceholder(template, i + 2, i, locals, result);
                } else {
                    result.append(c);
                    i++;
                }
            } else if (c == '{' && !dollarReq) {
                if (i + 1 < len && template.charAt(i + 1) == '{') {
                    result.append('{');
                    i += 2;
                } else {
                    i = expandPlaceholder(template, i + 1, i, locals, result);
                }
            } else if (c == '}' && !dollarReq) {
                if (i + 1 < len && template.charAt(i + 1) == '}') {
                    result.append('}');
                    i += 2;
                } else {
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

    // === Placeholder expansion ===

    /**
     * @param contentStart index of first char of placeholder content (after { or ${)
     * @param openIdx      index of the opening { or $ (for error messages)
     * @return index after the closing }
     */
    private int expandPlaceholder(String template, int contentStart, int openIdx,
                                  Map<String, Object> locals, StringBuilder result) {
        int closeBrace = template.indexOf('}', contentStart);
        if (closeBrace == -1) {
            throw new TemplateException(
                "Malformed placeholder: unclosed '{' at index " + openIdx +
                " in template: " + template);
        }

        String content = template.substring(contentStart, closeBrace);

        boolean optional = false;
        if (content.startsWith("?") && this.optionalPlaceholders) {
            optional = true;
            content = content.substring(1);
        }

        int colonIdx = content.indexOf(':');
        String expr;
        String formatSpec;
        if (colonIdx >= 0) {
            expr = content.substring(0, colonIdx);
            formatSpec = content.substring(colonIdx + 1);
        } else {
            expr = content;
            formatSpec = null;
        }

        Object value = evaluateExpression(expr, locals, template);

        if (optional && value == null) {
            int afterClose = closeBrace + 1;
            if (afterClose < template.length()) {
                char next = template.charAt(afterClose);
                if (next == '\r' && afterClose + 1 < template.length()
                        && template.charAt(afterClose + 1) == '\n') {
                    return afterClose + 2;
                } else if (next == '\n') {
                    return afterClose + 1;
                }
            }
            return afterClose;
        }

        boolean isContainer = this.expandContainers && value != null && isContainer(value);

        if (formatSpec != null) {
            try {
                if (isContainer) {
                    result.append(String.format("%" + formatSpec, renderContainer(value)));
                } else {
                    result.append(String.format("%" + formatSpec, value));
                }
            } catch (java.util.IllegalFormatException e) {
                throw new TemplateException(
                    "Format error for '{" + content + "}': " + e.getMessage(), e);
            }
        } else {
            if (isContainer) {
                result.append(renderContainer(value));
            } else {
                result.append(String.valueOf(value));
            }
        }

        return closeBrace + 1;
    }

    // === Nested template expansion (${{...}}) ===

    private int expandNestedTemplate(String template, int dollarIdx,
                                     Map<String, Object> locals, StringBuilder result) {
        int contentStart = dollarIdx + 3;
        int closeDouble = template.indexOf("}}", contentStart);
        if (closeDouble == -1) {
            throw new TemplateException(
                "Malformed nested placeholder: unclosed '${{' at index " + dollarIdx +
                " in template: " + template);
        }

        String content = template.substring(contentStart, closeDouble);
        int colonIdx = content.indexOf(':');
        String expr;
        String formatSpec;
        if (colonIdx >= 0) {
            expr = content.substring(0, colonIdx);
            formatSpec = content.substring(colonIdx + 1);
        } else {
            expr = content;
            formatSpec = null;
        }

        Object value = evaluateExpression(expr, locals, template);
        String innerTemplate = String.valueOf(value);
        String expanded = expand(locals, innerTemplate);

        if (formatSpec != null) {
            try {
                result.append(String.format("%" + formatSpec, expanded));
            } catch (java.util.IllegalFormatException e) {
                throw new TemplateException(
                    "Format error for '${{" + content + "}}': " + e.getMessage(), e);
            }
        } else {
            result.append(expanded);
        }

        return closeDouble + 2;
    }

    // === Expression evaluation ===

    Object evaluateExpression(String expr, Map<String, Object> locals, String template) {
        if (this.memberAccess && expr.indexOf('.') >= 0) {
            return evaluateDottedExpression(expr, locals, template);
        }

        return lookupVariable(expr, locals, template);
    }

    private Object evaluateDottedExpression(String expr, Map<String, Object> locals,
                                            String template) {
        List<String> parts = splitOnDot(expr);
        String varName = parts.get(0);
        if (varName.endsWith("()")) {
            throw new TemplateException(
                "First element of expression must be a variable name, not a method call: '"
                + expr + "' in template: " + template);
        }

        Object current = lookupVariable(varName, locals, template);

        for (int j = 1; j < parts.size(); j++) {
            if (current == null) return null;
            current = resolveProperty(current, parts.get(j));
        }

        return current;
    }

    private Object lookupVariable(String name, Map<String, Object> locals, String template) {
        if (name.isEmpty() || !isValidJavaIdentifier(name)) {
            throw new TemplateException(
                "'" + name + "' is not a valid Java identifier in template: " + template);
        }
        if (!locals.containsKey(name)) {
            StringBuilder available = new StringBuilder("Available names: ");
            if (locals.isEmpty()) {
                available.append("(none)");
            } else {
                boolean first = true;
                for (String key : locals.keySet()) {
                    if (!first) available.append(", ");
                    available.append(key);
                    first = false;
                }
            }
            throw new TemplateException(
                "Name '" + name + "' not found in caller's local variables. " +
                available.toString() +
                ". The variable may not exist at this call site, or it may have " +
                "been optimized away before the agent saw the class.");
        }
        return locals.get(name);
    }

    // === Property / member resolution ===

    static Object resolveProperty(Object obj, String member) {
        if (obj == null) return null;

        boolean isMethodCall = member.endsWith("()");
        String name = isMethodCall ? member.substring(0, member.length() - 2) : member;

        if (name.isEmpty()) {
            throw new TemplateException("Empty member name in expression");
        }

        Class<?> clazz = obj.getClass();

        if (isMethodCall) {
            return invokeMethod(obj, clazz, name);
        }

        // Property access: public field → name() → getName()/isName()

        try {
            Field f = clazz.getField(name);
            return f.get(obj);
        } catch (NoSuchFieldException e) {
            // fall through
        } catch (IllegalAccessException e) {
            // fall through
        }

        try {
            Method m = clazz.getMethod(name);
            return m.invoke(obj);
        } catch (NoSuchMethodException e) {
            // fall through
        } catch (InvocationTargetException e) {
            throw new TemplateException(
                "Error calling '" + name + "()' on " + clazz.getSimpleName() +
                ": " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            // fall through
        }

        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method m = clazz.getMethod("get" + cap);
            return m.invoke(obj);
        } catch (NoSuchMethodException e) {
            // fall through
        } catch (InvocationTargetException e) {
            throw new TemplateException(
                "Error calling 'get" + cap + "()' on " + clazz.getSimpleName() +
                ": " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            // fall through
        }

        try {
            Method m = clazz.getMethod("is" + cap);
            return m.invoke(obj);
        } catch (NoSuchMethodException e) {
            throw new TemplateException(
                "Cannot resolve '" + name + "' on " + clazz.getSimpleName() +
                ". No public field, no '" + name + "()' method, " +
                "no 'get" + cap + "()' method.");
        } catch (InvocationTargetException e) {
            throw new TemplateException(
                "Error calling 'is" + cap + "()' on " + clazz.getSimpleName() +
                ": " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new TemplateException(
                "Cannot access 'is" + cap + "()' on " + clazz.getSimpleName(), e);
        }
    }

    private static Object invokeMethod(Object obj, Class<?> clazz, String name) {
        try {
            Method m = clazz.getMethod(name);
            return m.invoke(obj);
        } catch (NoSuchMethodException e) {
            throw new TemplateException(
                "No public method '" + name + "()' on " + clazz.getSimpleName());
        } catch (InvocationTargetException e) {
            throw new TemplateException(
                "Error calling '" + name + "()' on " + clazz.getSimpleName() +
                ": " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new TemplateException(
                "Cannot access '" + name + "()' on " + clazz.getSimpleName(), e);
        }
    }

    // === Container utilities ===

    static boolean isContainer(Object value) {
        return value.getClass().isArray() || value instanceof Collection;
    }

    static List<Object> flattenContainer(Object value) {
        List<Object> result = new ArrayList<Object>();
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(Array.get(value, i));
            }
        } else if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                result.add(item);
            }
        }
        return result;
    }

    String renderContainer(Object value) {
        StringBuilder sb = new StringBuilder("[");
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.valueOf(Array.get(value, i)));
            }
        } else if (value instanceof Collection) {
            boolean first = true;
            for (Object item : (Collection<?>) value) {
                if (!first) sb.append(", ");
                sb.append(String.valueOf(item));
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // === String utilities ===

    static boolean isValidJavaIdentifier(String s) {
        if (s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    static List<String> splitOnDot(String expr) {
        List<String> parts = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '.') {
                parts.add(expr.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(expr.substring(start));
        return parts;
    }
}
