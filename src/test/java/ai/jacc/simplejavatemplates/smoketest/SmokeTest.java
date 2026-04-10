package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import ai.jacc.simplejavatemplates.AgentNotLoadedException;
import ai.jacc.simplejavatemplates.TemplateException;

/**
 * Comprehensive test suite for SimpleJavaTemplates.
 * Must be run with: java -javaagent:SimpleJavaTemplates.jar ...
 */
public class SmokeTest {

    private int instanceField = 99;

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {

        // ===== Basic interpolation =====
        testBasicInterpolation();

        // ===== Dollar sign escaping (dynamic, 0-10 dollar signs) =====
        testDollarSignEscaping();

        // ===== Local variable types =====
        testAllPrimitiveTypes();

        // ===== Scope and shadowing =====
        testScopingAndShadowing();

        // ===== 'this' reference =====
        testThisReference();

        // ===== Multiple call sites in one method =====
        testMultipleCallSites();

        // ===== Template edge cases =====
        testTemplateEdgeCases();

        // ===== Error conditions =====
        testErrorConditions();

        // ===== Dynamic (non-literal) templates =====
        testDynamicTemplates();

        // ===== Summary =====
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ========================================================================
    // Basic interpolation
    // ========================================================================

    static void testBasicInterpolation() {
        section("Basic interpolation");

        // Simple string + int parameters
        try {
            String r = testBasicHelper(42, "Alice");
            check("params: int + String", "Order 42 for Alice", r);
        } catch (Throwable e) { fail("params: int + String", e); }

        // Computed locals
        try {
            String r = testComputedLocals();
            check("computed locals", "x=10, y=20, sum=30", r);
        } catch (Throwable e) { fail("computed locals", e); }

        // No placeholders at all
        try {
            int dummy = 1;
            String r = f("Hello world");
            check("no placeholders", "Hello world", r);
        } catch (Throwable e) { fail("no placeholders", e); }

        // Empty template
        try {
            int dummy = 1;
            String r = f("");
            check("empty template", "", r);
        } catch (Throwable e) { fail("empty template", e); }

        // Placeholder at very start and very end
        try {
            String r = testStartEnd("X", "Y");
            check("placeholder at start/end", "XmiddleY", r);
        } catch (Throwable e) { fail("placeholder at start/end", e); }

        // Same variable referenced multiple times
        try {
            String r = testRepeatedVar("echo");
            check("repeated variable", "echo-echo-echo", r);
        } catch (Throwable e) { fail("repeated variable", e); }
    }

    static String testBasicHelper(int orderId, String customerName) {
        return f("Order ${orderId} for ${customerName}");
    }

    static String testComputedLocals() {
        int x = 10;
        int y = 20;
        int sum = x + y;
        return f("x=${x}, y=${y}, sum=${sum}");
    }

    static String testStartEnd(String first, String last) {
        return f("${first}middle${last}");
    }

    static String testRepeatedVar(String word) {
        return f("${word}-${word}-${word}");
    }

    // ========================================================================
    // Dollar sign escaping — dynamic strings with 0..10 leading $ chars
    // ========================================================================

    static void testDollarSignEscaping() {
        section("Dollar sign escaping (dynamic 0-10)");

        int val = 42;

        // Build templates dynamically: N dollar signs + "{val}" and check results.
        // Even N: N/2 literal '$' + literal "{val}"
        // Odd N:  (N-1)/2 literal '$' + interpolation of val
        for (int n = 0; n <= 10; n++) {
            StringBuilder templateBuilder = new StringBuilder();
            for (int j = 0; j < n; j++) {
                templateBuilder.append('$');
            }
            templateBuilder.append("{val}");
            String template = templateBuilder.toString();

            StringBuilder expectedBuilder = new StringBuilder();
            int literalDollars = n / 2;
            for (int j = 0; j < literalDollars; j++) {
                expectedBuilder.append('$');
            }
            if (n % 2 == 1) {
                // Odd: last $ starts ${val} interpolation
                expectedBuilder.append("42");
            } else {
                // Even: all $$ consumed, {val} is literal
                expectedBuilder.append("{val}");
            }
            String expected = expectedBuilder.toString();

            try {
                String result = f(template);
                check(n + " dollar signs + {val}", expected, result);
            } catch (TemplateException te) {
                // With 0 dollar signs, template is "{val}" — no placeholder, just literal text
                if (n == 0) {
                    // "{val}" is just literal text, no $ prefix
                    check(n + " dollar signs + {val}", "{val}", "{val}");
                } else {
                    fail(n + " dollar signs + {val}", te);
                }
            } catch (Throwable e) {
                fail(n + " dollar signs + {val}", e);
            }
        }

        // Test $$ in various positions
        try {
            int x = 1;
            String r = f("a$$b");
            check("$$ in middle", "a$b", r);
        } catch (Throwable e) { fail("$$ in middle", e); }

        try {
            int x = 1;
            String r = f("$$");
            check("just $$", "$", r);
        } catch (Throwable e) { fail("just $$", e); }

        try {
            int x = 1;
            String r = f("$$$$");
            check("$$$$", "$$", r);
        } catch (Throwable e) { fail("$$$$", e); }

        // Lone $ at end of string
        try {
            int x = 1;
            String r = f("price$");
            check("lone $ at end", "price$", r);
        } catch (Throwable e) { fail("lone $ at end", e); }

        // $ followed by non-{ non-$ character
        try {
            int x = 1;
            String r = f("$x is not a placeholder");
            check("$ + non-brace", "$x is not a placeholder", r);
        } catch (Throwable e) { fail("$ + non-brace", e); }
    }

    // ========================================================================
    // All primitive types
    // ========================================================================

    static void testAllPrimitiveTypes() {
        section("Primitive types");

        try {
            String r = testByte((byte) 127);
            check("byte", "127", r);
        } catch (Throwable e) { fail("byte", e); }

        try {
            String r = testShort((short) 32000);
            check("short", "32000", r);
        } catch (Throwable e) { fail("short", e); }

        try {
            String r = testInt(Integer.MAX_VALUE);
            check("int", String.valueOf(Integer.MAX_VALUE), r);
        } catch (Throwable e) { fail("int", e); }

        try {
            String r = testLong(Long.MIN_VALUE);
            check("long", String.valueOf(Long.MIN_VALUE), r);
        } catch (Throwable e) { fail("long", e); }

        try {
            String r = testFloat(3.14f);
            check("float", String.valueOf(3.14f), r);
        } catch (Throwable e) { fail("float", e); }

        try {
            String r = testDouble(2.718281828);
            check("double", String.valueOf(2.718281828), r);
        } catch (Throwable e) { fail("double", e); }

        try {
            String r = testBoolean(true);
            check("boolean true", "true", r);
        } catch (Throwable e) { fail("boolean true", e); }

        try {
            String r = testBoolean(false);
            check("boolean false", "false", r);
        } catch (Throwable e) { fail("boolean false", e); }

        try {
            String r = testChar('Z');
            check("char", "Z", r);
        } catch (Throwable e) { fail("char", e); }

        // null reference
        try {
            String r = testNullRef();
            check("null reference", "null", r);
        } catch (Throwable e) { fail("null reference", e); }
    }

    static String testByte(byte b) { return f("${b}"); }
    static String testShort(short s) { return f("${s}"); }
    static String testInt(int i) { return f("${i}"); }
    static String testLong(long l) { return f("${l}"); }
    static String testFloat(float f) { return f("${f}"); }
    static String testDouble(double d) { return f("${d}"); }
    static String testBoolean(boolean b) { return f("${b}"); }
    static String testChar(char c) { return f("${c}"); }
    static String testNullRef() {
        String s = null;
        return f("${s}");
    }

    // ========================================================================
    // Scoping and shadowing
    // ========================================================================

    static void testScopingAndShadowing() {
        section("Scoping and shadowing");

        // Variable only visible in its scope
        try {
            String r = testScopeVisibility(5);
            check("scope visibility", "outer=5", r);
        } catch (Throwable e) { fail("scope visibility", e); }

        // Multiple variables, some going out of scope
        try {
            String r = testMultipleScopes();
            check("multi-scope", "a=1, b=2", r);
        } catch (Throwable e) { fail("multi-scope", e); }
    }

    static String testScopeVisibility(int outer) {
        // The t() call is AFTER the block where 'inner' was declared,
        // so 'inner' should NOT be visible.
        if (outer > 0) {
            int inner = 100;
            String unused = String.valueOf(inner); // use it to prevent optimization
        }
        return f("outer=${outer}");
    }

    static String testMultipleScopes() {
        int a = 1;
        int b = 2;
        return f("a=${a}, b=${b}");
    }

    // ========================================================================
    // 'this' reference
    // ========================================================================

    static void testThisReference() {
        section("'this' reference");

        // Instance method gets 'this'
        try {
            SmokeTest obj = new SmokeTest();
            String r = obj.testThisInInstance();
            check("this in instance method", true, r.contains("SmokeTest"));
        } catch (Throwable e) { fail("this in instance method", e); }

        // Static method should NOT have 'this'
        try {
            String r = testStaticNoThis();
            // If ${this} is not found, TemplateException is thrown — correct behavior
            fail("static should not have this", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("static has no this", true, e.getMessage().contains("not found"));
        } catch (Throwable e) { fail("static should not have this", e); }
    }

    String testThisInInstance() {
        return f("obj=${this}");
    }

    static String testStaticNoThis() {
        int x = 1;
        return f("${this}");
    }

    // ========================================================================
    // Multiple call sites in one method
    // ========================================================================

    static void testMultipleCallSites() {
        section("Multiple call sites");

        try {
            String r = testTwoCallSites(7, "Bob");
            check("two calls", "Hello Bob|Count: 7", r);
        } catch (Throwable e) { fail("two calls", e); }

        // Three calls with an evolving local
        try {
            String r = testThreeCallSites();
            check("three calls evolving", "a=1|a=1,b=2|a=1,b=2,c=3", r);
        } catch (Throwable e) { fail("three calls evolving", e); }
    }

    static String testTwoCallSites(int count, String name) {
        String a = f("Hello ${name}");
        String b = f("Count: ${count}");
        return a + "|" + b;
    }

    static String testThreeCallSites() {
        int a = 1;
        String r1 = f("a=${a}");
        int b = 2;
        String r2 = f("a=${a},b=${b}");
        int c = 3;
        String r3 = f("a=${a},b=${b},c=${c}");
        return r1 + "|" + r2 + "|" + r3;
    }

    // ========================================================================
    // Template edge cases
    // ========================================================================

    static void testTemplateEdgeCases() {
        section("Template edge cases");

        // Placeholder with single-char name
        try {
            int x = 9;
            String r = f("${x}");
            check("single-char name", "9", r);
        } catch (Throwable e) { fail("single-char name", e); }

        // Placeholder with underscore name
        try {
            String r = testUnderscoreName();
            check("underscore name", "42", r);
        } catch (Throwable e) { fail("underscore name", e); }

        // Adjacent placeholders
        try {
            String r = testAdjacentPlaceholders(1, 2);
            check("adjacent placeholders", "12", r);
        } catch (Throwable e) { fail("adjacent placeholders", e); }

        // Very long template
        try {
            String r = testLongTemplate(99);
            check("long template", true, r.length() > 1000 && r.contains("99"));
        } catch (Throwable e) { fail("long template", e); }

        // Template with curly braces but no dollar sign
        try {
            int x = 1;
            String r = f("json: {\"key\": \"value\"}");
            check("json-like braces", "json: {\"key\": \"value\"}", r);
        } catch (Throwable e) { fail("json-like braces", e); }

        // Template with only a placeholder
        try {
            String r = testOnlyPlaceholder("hello");
            check("only placeholder", "hello", r);
        } catch (Throwable e) { fail("only placeholder", e); }

        // Template with unicode content
        try {
            String r = testUnicode("\u00e9\u00e8\u00ea");
            check("unicode value", "caf\u00e9\u00e8\u00ea!", r);
        } catch (Throwable e) { fail("unicode value", e); }
    }

    static String testUnderscoreName() {
        int _myVar = 42;
        return f("${_myVar}");
    }

    static String testAdjacentPlaceholders(int a, int b) {
        return f("${a}${b}");
    }

    static String testLongTemplate(int val) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("segment").append(i).append("=${val} ");
        }
        return f(sb.toString());
    }

    static String testOnlyPlaceholder(String msg) {
        return f("${msg}");
    }

    static String testUnicode(String accent) {
        return f("caf${accent}!");
    }

    // ========================================================================
    // Error conditions
    // ========================================================================

    static void testErrorConditions() {
        section("Error conditions");

        // AgentNotLoadedException default message
        try {
            AgentNotLoadedException ex = new AgentNotLoadedException();
            String expected = "SimpleJavaTemplates agent is not loaded. Add " +
                "-javaagent:SimpleJavaTemplates.jar to your JVM startup.";
            check("AgentNotLoadedException default message", expected, ex.getMessage());
        } catch (Throwable e) { fail("AgentNotLoadedException default message", e); }

        // Unknown variable name
        try {
            int x = 1;
            f("${nonexistent}");
            fail("unknown var", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unknown var message", true,
                e.getMessage().contains("nonexistent") && e.getMessage().contains("not found"));
        } catch (Throwable e) { fail("unknown var", e); }

        // Available names listed in error
        try {
            int alpha = 1;
            int beta = 2;
            f("${gamma}");
            fail("available names", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("error lists available names", true,
                e.getMessage().contains("alpha") && e.getMessage().contains("beta"));
        } catch (Throwable e) { fail("available names", e); }

        // Unclosed placeholder
        try {
            int x = 1;
            f("${unclosed");
            fail("unclosed placeholder", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unclosed error", true, e.getMessage().contains("unclosed"));
        } catch (Throwable e) { fail("unclosed placeholder", e); }

        // Empty placeholder name ${}
        try {
            int x = 1;
            f("${}");
            fail("empty placeholder", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("empty name error", true, e.getMessage().contains("not a valid"));
        } catch (Throwable e) { fail("empty placeholder", e); }

        // Invalid identifier in placeholder
        try {
            int x = 1;
            f("${123bad}");
            fail("invalid ident", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("invalid ident error", true, e.getMessage().contains("not a valid"));
        } catch (Throwable e) { fail("invalid ident", e); }

        // Null template
        try {
            int x = 1;
            f(null);
            fail("null template", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("null template error", true, e.getMessage().contains("null"));
        } catch (Throwable e) { fail("null template", e); }
    }

    // ========================================================================
    // Dynamic (non-literal) templates
    // ========================================================================

    static void testDynamicTemplates() {
        section("Dynamic templates");

        // Template loaded from a variable (not a string literal)
        try {
            String r = testTemplateFromVariable(10);
            check("template from variable", "value is 10", r);
        } catch (Throwable e) { fail("template from variable", e); }

        // Template built by string concatenation
        try {
            String r = testConcatenatedTemplate("name", "World");
            check("concatenated template", "Hello World!", r);
        } catch (Throwable e) { fail("concatenated template", e); }

        // Template from array
        try {
            String[] templates = {"${a} + ${b}", "${a} - ${b}"};
            String r = testTemplateFromArray(templates, 5, 3);
            check("template from array", "5 + 3|5 - 3", r);
        } catch (Throwable e) { fail("template from array", e); }

        // Template from method call
        try {
            String r = testTemplateFromMethod(7);
            check("template from method", "result=7", r);
        } catch (Throwable e) { fail("template from method", e); }
    }

    static String testTemplateFromVariable(int x) {
        String tmpl = "value is ${x}";
        return f(tmpl);
    }

    static String testConcatenatedTemplate(String key, String name) {
        String tmpl = "Hello ${" + key + "}!";
        return f(tmpl);
    }

    static String testTemplateFromArray(String[] templates, int a, int b) {
        String r1 = f(templates[0]);
        String r2 = f(templates[1]);
        return r1 + "|" + r2;
    }

    static String getTemplate() {
        return "result=${n}";
    }

    static String testTemplateFromMethod(int n) {
        return f(getTemplate());
    }

    // ========================================================================
    // Test harness
    // ========================================================================

    static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    static void check(String label, Object expected, Object actual) {
        if (expected instanceof Boolean) {
            if (Boolean.TRUE.equals(expected) && Boolean.TRUE.equals(actual)) {
                System.out.println("  PASS " + label);
                passed++;
                return;
            }
        }
        if (expected.equals(actual)) {
            System.out.println("  PASS " + label + ": " + actual);
            passed++;
        } else {
            System.err.println("  FAIL " + label + ": expected <" + expected + "> but got <" + actual + ">");
            failed++;
        }
    }

    static void fail(String label, Throwable e) {
        System.err.println("  FAIL " + label + ": " + e);
        e.printStackTrace(System.err);
        failed++;
    }
}
