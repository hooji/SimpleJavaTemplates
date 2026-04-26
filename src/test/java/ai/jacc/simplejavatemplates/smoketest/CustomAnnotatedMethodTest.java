package ai.jacc.simplejavatemplates.smoketest;

import ai.jacc.simplejavatemplates.RequiresCallerLocalVariableDetails;
import ai.jacc.simplejavatemplates.TemplateException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates that user-defined methods can use
 * {@link RequiresCallerLocalVariableDetails} by providing a companion
 * implementation that follows the {@code $___name__params___} naming
 * convention. Also verifies that omitting the companion produces a
 * {@link TemplateException} whose message tells the developer exactly
 * which method to add.
 *
 * Run with: java -javaagent:SimpleJavaTemplates.jar ...
 */
public class CustomAnnotatedMethodTest {

    static int passed = 0;
    static int failed = 0;

    // ===== Annotated method WITH a companion: works end-to-end =====

    @RequiresCallerLocalVariableDetails
    public static String describeLocals(String prefix) {
        // Without the agent the call would land here. With the agent the
        // bytecode redirects to the companion below.
        throw new IllegalStateException(
            "agent did not rewrite call to describeLocals");
    }

    public static String $___describeLocals__Ljava_lang_String_2___(
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

    // ===== Annotated method WITHOUT a companion: should throw with a
    // diagnostic naming the missing method =====

    @RequiresCallerLocalVariableDetails
    public static String missingCompanion(String x) {
        return "unreachable";
    }

    // ===== Tests =====

    public static void main(String[] args) {
        section("Companion method works");
        try {
            int answer = 42;
            String name = "Alice";
            String result = callDescribeLocals(answer, name);
            check("describeLocals captures int + String locals",
                "L: answer=42,name=Alice", result);
        } catch (Throwable t) {
            fail("describeLocals captures int + String locals", t);
        }

        try {
            String greeting = "hi";
            boolean flag = true;
            long count = 7L;
            String result = callDescribeLocalsMixed(greeting, flag, count);
            check("describeLocals captures mixed types",
                "M: count=7,flag=true,greeting=hi", result);
        } catch (Throwable t) {
            fail("describeLocals captures mixed types", t);
        }

        section("Missing companion raises diagnostic");
        try {
            String x = "hello";
            missingCompanion(x);
            fail("missingCompanion should throw", null);
        } catch (TemplateException e) {
            String m = e.getMessage();
            check("message names original method",
                true, m.contains("missingCompanion"));
            check("message gives expected companion name",
                true, m.contains("$___missingCompanion__Ljava_lang_String_2___"));
            check("message mentions Map<String, Object>",
                true, m.contains("java.util.Map<String, Object>"));
            check("message mentions owner class",
                true, m.contains(
                    "ai.jacc.simplejavatemplates.smoketest.CustomAnnotatedMethodTest"));
            check("message mentions return type",
                true, m.contains("public static java.lang.String"));
        } catch (Throwable t) {
            fail("missingCompanion throws TemplateException", t);
        }

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // Isolate the locals visible to the call site so the test is deterministic.
    private static String callDescribeLocals(int answer, String name) {
        return describeLocals("L: ");
    }

    private static String callDescribeLocalsMixed(String greeting, boolean flag, long count) {
        return describeLocals("M: ");
    }

    // ===== Mini test framework =====

    static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    static void check(String label, Object expected, Object actual) {
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (eq) {
            System.out.println("  PASS " + label + ": " + actual);
            passed++;
        } else {
            System.out.println("  FAIL " + label + ": expected " + expected + ", got " + actual);
            failed++;
        }
    }

    static void fail(String label, Throwable t) {
        System.out.println("  FAIL " + label + (t == null ? "" : ": " + t));
        if (t != null) t.printStackTrace(System.out);
        failed++;
    }
}
