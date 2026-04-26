package ai.jacc.simplejavatemplates.smoketest;

import ai.jacc.simplejavatemplates.AgentNotLoadedException;
import ai.jacc.simplejavatemplates.smoketest.loadorderlib.AnnotatedLibrary;

/**
 * Regression test for the load-order bug: when an entry-point class
 * calls an annotated method declared in a class that has not yet been
 * loaded at transform time, the agent must still rewrite the call site.
 *
 * Run with: java -javaagent:SimpleJavaTemplates.jar
 *   ai.jacc.simplejavatemplates.smoketest.LoadOrderTest
 */
public class LoadOrderTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        section("Caller loaded before callee");
        try {
            int answer = 42;
            String name = "Alice";
            String result = AnnotatedLibrary.captureLocals("L: ");
            // The map also contains "args" (String[] from main); we only
            // care that the locals declared in this method appear.
            check("starts with prefix", true, result.startsWith("L: "));
            check("captures answer", true, result.contains("answer=42"));
            check("captures name", true, result.contains("name=Alice"));
        } catch (AgentNotLoadedException e) {
            // The diagnostic the user actually sees in the wild — surface it
            // as a clear FAIL rather than silently letting it propagate.
            fail("captures locals from main()", e);
        } catch (Throwable t) {
            fail("captures locals from main()", t);
        }

        section("Indirect caller loaded before callee");
        try {
            String result = indirectCaller();
            check("captures locals from helper",
                "I: greeting=hi,n=7", result);
        } catch (Throwable t) {
            fail("captures locals from helper", t);
        }

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static String indirectCaller() {
        String greeting = "hi";
        long n = 7L;
        return AnnotatedLibrary.captureLocals("I: ");
    }

    static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    static void check(String label, Object expected, Object actual) {
        boolean eq = expected == null ? actual == null : expected.equals(actual);
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
