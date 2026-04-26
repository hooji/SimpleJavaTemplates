package ai.jacc.simplejavatemplates.smoketest.loadorderlib;

import ai.jacc.simplejavatemplates.AgentNotLoadedException;
import ai.jacc.simplejavatemplates.RequiresCallerLocalVariableDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds an annotated method in a different package from
 * {@code LoadOrderTest}. Because the entry-point class is loaded first
 * by the JVM, this class is only loaded later — at the moment the call
 * site executes. The agent must therefore pre-register the annotation
 * on this class while transforming the caller, otherwise the call site
 * is left unrewritten and falls into the stub body.
 */
public final class AnnotatedLibrary {

    private AnnotatedLibrary() {}

    @RequiresCallerLocalVariableDetails
    public static String captureLocals(String prefix) {
        throw new AgentNotLoadedException();
    }

    public static String $___captureLocals__Ljava_lang_String_2___(
            Map<String, Object> localVariableValues, String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        List<String> keys = new ArrayList<String>(localVariableValues.keySet());
        Collections.sort(keys);
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i)).append('=').append(localVariableValues.get(keys.get(i)));
        }
        return sb.toString();
    }
}
