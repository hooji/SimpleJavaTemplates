package ai.jacc.simplejavatemplates;

import java.util.Map;

public final class Template {

    private Template() { }

    @RequiresCallerLocalVariableDetails
    public static String t(String template) {
        throw new AgentNotLoadedException(
            "SimpleJavaTemplates agent is not loaded. Add " +
            "-javaagent:SimpleJavaTemplates.jar to your JVM startup."
        );
    }

    public static String $___t__Ljava_lang_String_2___(
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
                } else if (i + 1 < len && template.charAt(i + 1) == '{') {
                    // ${name} placeholder
                    int closeBrace = template.indexOf('}', i + 2);
                    if (closeBrace == -1) {
                        throw new TemplateException(
                            "Malformed placeholder: unclosed '${' at index " + i +
                            " in template: " + template);
                    }
                    String name = template.substring(i + 2, closeBrace);
                    if (name.isEmpty() || !isValidJavaIdentifier(name)) {
                        throw new TemplateException(
                            "Malformed placeholder: '${" + name +
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
                    result.append(String.valueOf(localVarValues.get(name)));
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

    private static boolean isValidJavaIdentifier(String s) {
        if (s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }
}
