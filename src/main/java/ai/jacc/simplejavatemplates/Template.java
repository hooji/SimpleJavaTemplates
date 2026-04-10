package ai.jacc.simplejavatemplates;

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
