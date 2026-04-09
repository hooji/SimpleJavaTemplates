package ai.jacc.simplejavatemplates.agent;

import ai.jacc.simplejavatemplates.LocalVariableDetails;
import ai.jacc.simplejavatemplates.MethodLocalVariableDetails;

import java.util.HashMap;
import java.util.Map;

public final class MapBuilder {

    private MapBuilder() { }

    /**
     * Builds the local variable map for a call site.
     *
     * @param metadata         the per-method metadata (logicals with original PC ranges)
     * @param originalPc       the original pre-rewrite PC of the call site
     * @param boxedSlotValues  array parallel to metadata.localVariableDetails(), each entry
     *                         is the boxed value of the corresponding logical's slot
     * @return a Map from variable name to boxed value
     */
    public static Map<String, Object> buildMap(
            MethodLocalVariableDetails metadata,
            int originalPc,
            Object[] boxedSlotValues) {

        LocalVariableDetails[] locals = metadata.localVariableDetails();
        // First pass: find the winning logical for each name (inner-scope-wins shadowing).
        // We use a HashMap mapping name -> index of the winning logical.
        HashMap<String, Integer> winnerIndex = new HashMap<String, Integer>();

        for (int i = 0; i < locals.length; i++) {
            LocalVariableDetails lvd = locals[i];

            // Check if this logical is in scope at the original PC
            int start = lvd.originalStartPc();
            int end = start + lvd.originalLength();
            if (originalPc < start || originalPc >= end) {
                continue;
            }

            String name = lvd.name();

            // Defense in depth: exclude compiler-synthetic locals (names containing $)
            if (name.contains("$")) {
                continue;
            }

            Integer existing = winnerIndex.get(name);
            if (existing == null) {
                winnerIndex.put(name, i);
            } else {
                // Inner-scope-wins: the one with the later originalStartPc wins
                if (lvd.originalStartPc() > locals[existing].originalStartPc()) {
                    winnerIndex.put(name, i);
                }
            }
        }

        // Second pass: build the result map
        HashMap<String, Object> result = new HashMap<String, Object>(winnerIndex.size());
        for (Map.Entry<String, Integer> entry : winnerIndex.entrySet()) {
            result.put(entry.getKey(), boxedSlotValues[entry.getValue()]);
        }

        return result;
    }
}
