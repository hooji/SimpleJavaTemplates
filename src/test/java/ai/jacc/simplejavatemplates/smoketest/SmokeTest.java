package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.t;

/**
 * Standalone smoke test that exercises template interpolation end-to-end.
 * Must be run with: java -javaagent:SimpleJavaTemplates.jar ...
 */
public class SmokeTest {

    private int instanceField = 99;

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;

        // Test 1: basic interpolation with primitives and strings
        try {
            String result = testBasic(42, "Alice");
            assertEquals("Test 1 (basic)", "Order 42 for Alice", result);
            passed++;
        } catch (Throwable e) {
            System.err.println("FAIL Test 1: " + e);
            e.printStackTrace();
            failed++;
        }

        // Test 2: local variables computed inside the method
        try {
            String result = testLocals();
            assertEquals("Test 2 (locals)", "x=10, y=20, sum=30", result);
            passed++;
        } catch (Throwable e) {
            System.err.println("FAIL Test 2: " + e);
            e.printStackTrace();
            failed++;
        }

        // Test 3: 'this' reference in instance method
        try {
            SmokeTest obj = new SmokeTest();
            String result = obj.testThis();
            assertEquals("Test 3 (this)", true, result.contains("SmokeTest"));
            passed++;
        } catch (Throwable e) {
            System.err.println("FAIL Test 3: " + e);
            e.printStackTrace();
            failed++;
        }

        // Test 4: dollar-sign escaping
        try {
            int price = 5;
            String result = t("Price: $$${price}");
            assertEquals("Test 4 (escape)", "Price: $5", result);
            passed++;
        } catch (Throwable e) {
            System.err.println("FAIL Test 4: " + e);
            e.printStackTrace();
            failed++;
        }

        // Test 5: unknown variable throws TemplateException
        try {
            int x = 1;
            try {
                String result = t("${nonexistent}");
                System.err.println("FAIL Test 5: expected TemplateException, got: " + result);
                failed++;
            } catch (ai.jacc.simplejavatemplates.TemplateException e) {
                passed++;
            }
        } catch (Throwable e) {
            System.err.println("FAIL Test 5: " + e);
            failed++;
        }

        // Test 6: multiple calls in same method
        try {
            String result = testMultipleCalls(7, "Bob");
            assertEquals("Test 6 (multi)", "Hello Bob|Count: 7", result);
            passed++;
        } catch (Throwable e) {
            System.err.println("FAIL Test 6: " + e);
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static String testBasic(int orderId, String customerName) {
        return t("Order ${orderId} for ${customerName}");
    }

    static String testLocals() {
        int x = 10;
        int y = 20;
        int sum = x + y;
        return t("x=${x}, y=${y}, sum=${sum}");
    }

    String testThis() {
        return t("obj=${this}");
    }

    static String testMultipleCalls(int count, String name) {
        String a = t("Hello ${name}");
        String b = t("Count: ${count}");
        return a + "|" + b;
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected + "> but got <" + actual + ">");
        }
        System.out.println("PASS " + label + ": " + actual);
    }
}
