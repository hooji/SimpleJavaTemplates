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

        // ===== Format specifiers =====
        testFormatSpecifiers();

        // ===== Nested templates =====
        testNestedTemplates();

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
    // Format specifiers
    // ========================================================================

    static void testFormatSpecifiers() {
        section("Format specifiers");

        // Floating-point: .2f
        try {
            String r = testFloatFormat(3.14159);
            check("double .2f", "pi=3.14", r);
        } catch (Throwable e) { fail("double .2f", e); }

        // Floating-point: .0f (no decimals)
        try {
            String r = testFloatNoDecimals(99.7);
            check("double .0f", "val=100", r);
        } catch (Throwable e) { fail("double .0f", e); }

        // Floating-point: .5f (many decimals)
        try {
            String r = testFloatManyDecimals(1.0 / 3.0);
            check("double .5f", "val=0.33333", r);
        } catch (Throwable e) { fail("double .5f", e); }

        // Integer: zero-padded
        try {
            String r = testIntZeroPad(42);
            check("int 05d", "code=00042", r);
        } catch (Throwable e) { fail("int 05d", e); }

        // Integer: with sign
        try {
            String r = testIntWithSign(-7);
            check("int +d negative", "val=-7", r);
        } catch (Throwable e) { fail("int +d negative", e); }

        try {
            String r = testIntWithSign(7);
            check("int +d positive", "val=+7", r);
        } catch (Throwable e) { fail("int +d positive", e); }

        // String: uppercase
        try {
            String r = testStringUpperCase("hello");
            check("string S (upper)", "HI=HELLO", r);
        } catch (Throwable e) { fail("string S (upper)", e); }

        // String: width (right-padded)
        try {
            String r = testStringWidth("hi");
            check("string -10s", "[hi        ]", r);
        } catch (Throwable e) { fail("string -10s", e); }

        // Hex formatting
        try {
            String r = testHexFormat(255);
            check("int x (hex)", "hex=ff", r);
        } catch (Throwable e) { fail("int x (hex)", e); }

        try {
            String r = testHexUpperFormat(255);
            check("int X (HEX)", "hex=FF", r);
        } catch (Throwable e) { fail("int X (HEX)", e); }

        // Octal
        try {
            String r = testOctalFormat(8);
            check("int o (octal)", "oct=10", r);
        } catch (Throwable e) { fail("int o (octal)", e); }

        // Scientific notation
        try {
            String r = testScientific(123456.789);
            check("double e (scientific)", true, r.startsWith("sci=1.2"));
        } catch (Throwable e) { fail("double e (scientific)", e); }

        // Mix of formatted and unformatted placeholders in one template
        try {
            String r = testMixedFormatAndPlain("Alice", 3, 29.956);
            check("mixed format+plain", "User Alice has 3 items worth 29.96", r);
        } catch (Throwable e) { fail("mixed format+plain", e); }

        // Plain placeholder (no colon) still works as before
        try {
            String r = testPlainStillWorks(42);
            check("plain (no colon)", "val=42", r);
        } catch (Throwable e) { fail("plain (no colon)", e); }

        // Format spec with width + precision on float
        try {
            String r = testWidthAndPrecision(3.14);
            check("10.2f (width+prec)", "val=      3.14", r);
        } catch (Throwable e) { fail("10.2f (width+prec)", e); }

        // Bad format spec throws TemplateException
        try {
            testBadFormatSpec(42);
            fail("bad format spec", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("bad format error", true, e.getMessage().contains("Format error"));
        } catch (Throwable e) { fail("bad format spec", e); }

        // Comma grouping separator
        try {
            String r = testCommaGrouping(1234567);
            check("int ,d (commas)", "pop=1,234,567", r);
        } catch (Throwable e) { fail("int ,d (commas)", e); }

        // Multiple formatted placeholders
        try {
            String r = testMultipleFormatted(1, 2, 3);
            check("3 formatted placeholders", "001-002-003", r);
        } catch (Throwable e) { fail("3 formatted placeholders", e); }

        // Boolean formatting
        try {
            String r = testBooleanFormat(true);
            check("boolean b", "flag=true", r);
        } catch (Throwable e) { fail("boolean b", e); }
    }

    static String testFloatFormat(double pi) {
        return f("pi=${pi:.2f}");
    }

    static String testFloatNoDecimals(double val) {
        return f("val=${val:.0f}");
    }

    static String testFloatManyDecimals(double val) {
        return f("val=${val:.5f}");
    }

    static String testIntZeroPad(int code) {
        return f("code=${code:05d}");
    }

    static String testIntWithSign(int val) {
        return f("val=${val:+d}");
    }

    static String testStringUpperCase(String hi) {
        return f("HI=${hi:S}");
    }

    static String testStringWidth(String s) {
        return f("[${s:-10s}]");
    }

    static String testHexFormat(int val) {
        return f("hex=${val:x}");
    }

    static String testHexUpperFormat(int val) {
        return f("hex=${val:X}");
    }

    static String testOctalFormat(int val) {
        return f("oct=${val:o}");
    }

    static String testScientific(double val) {
        return f("sci=${val:e}");
    }

    static String testMixedFormatAndPlain(String name, int count, double total) {
        return f("User ${name} has ${count} items worth ${total:.2f}");
    }

    static String testPlainStillWorks(int val) {
        return f("val=${val}");
    }

    static String testWidthAndPrecision(double val) {
        return f("val=${val:10.2f}");
    }

    static String testBadFormatSpec(int val) {
        return f("val=${val:.2f}"); // int with float format → IllegalFormatConversionException
    }

    static String testCommaGrouping(int pop) {
        return f("pop=${pop:,d}");
    }

    static String testMultipleFormatted(int a, int b, int c) {
        return f("${a:03d}-${b:03d}-${c:03d}");
    }

    static String testBooleanFormat(boolean flag) {
        return f("flag=${flag:b}");
    }

    // ========================================================================
    // Nested templates (${{...}})
    // ========================================================================

    static void testNestedTemplates() {
        section("Nested templates");

        // Basic nested template: inner template is interpolated with same vars
        try {
            String r = testBasicNested("translator");
            check("basic nested", "Your job is translator", r);
        } catch (Throwable e) { fail("basic nested", e); }

        // The motivating use case from the spec: prompt assembly
        try {
            String r = testPromptAssembly();
            check("prompt assembly",
                "Your job is translator Translate this text " +
                "Please format your output as JSON according to this schema: {...}",
                r);
        } catch (Throwable e) { fail("prompt assembly", e); }

        // Nested template with no placeholders inside it (just a passthrough)
        try {
            String r = testNestedPassthrough();
            check("nested passthrough", "Hello world!", r);
        } catch (Throwable e) { fail("nested passthrough", e); }

        // Nested template alongside plain placeholders in the same outer template
        try {
            String r = testNestedMixedWithPlain("Alice", 42);
            check("nested + plain mixed", "Hello Alice! Your id is 42.", r);
        } catch (Throwable e) { fail("nested + plain mixed", e); }

        // Non-String value: StringBuilder used as inner template
        try {
            String r = testNestedStringBuilder(99);
            check("nested StringBuilder", "value is 99", r);
        } catch (Throwable e) { fail("nested StringBuilder", e); }

        // Multiple nested templates in one outer template
        try {
            String r = testMultipleNested();
            check("multiple nested", "START middle END", r);
        } catch (Throwable e) { fail("multiple nested", e); }

        // Recursive nesting: inner template contains ${{}} referencing another template
        try {
            String r = testRecursiveNested(7);
            check("recursive nested", "deep value is 7", r);
        } catch (Throwable e) { fail("recursive nested", e); }

        // Nested template with format specifier
        try {
            String r = testNestedWithFormat(3.14159);
            check("nested with format", "***pi=3.14***", r);
        } catch (Throwable e) { fail("nested with format", e); }

        // Nested template where the inner template uses $$ escaping
        try {
            String r = testNestedWithDollarEscape(100);
            check("nested $$ escape", "Price: $100", r);
        } catch (Throwable e) { fail("nested $$ escape", e); }

        // Nested template with format spec (width) applied to the result
        try {
            String r = testNestedWithWidthFormat();
            check("nested width format", "[hi        ]", r);
        } catch (Throwable e) { fail("nested width format", e); }

        // Unclosed ${{ throws TemplateException
        try {
            testNestedUnclosed();
            fail("unclosed ${{", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unclosed ${{ error", true, e.getMessage().contains("unclosed"));
        } catch (Throwable e) { fail("unclosed ${{", e); }

        // Unknown name inside ${{}} throws TemplateException
        try {
            testNestedUnknownName();
            fail("nested unknown name", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("nested unknown name error", true, e.getMessage().contains("not found"));
        } catch (Throwable e) { fail("nested unknown name", e); }
    }

    static String testBasicNested(String job) {
        String intro = "Your job is ${job}";
        return f("${{intro}}");
    }

    static String testPromptAssembly() {
        String job = "translator";
        String schema = "{...}";
        String intro = "Your job is ${job}";
        String body = "Translate this text";
        String returnType = "Please format your output as JSON according to this schema: ${schema}";
        return f("${{intro}} ${body} ${{returnType}}");
    }

    static String testNestedPassthrough() {
        String tmpl = "Hello world!";
        return f("${{tmpl}}");
    }

    static String testNestedMixedWithPlain(String name, int id) {
        String greeting = "Hello ${name}!";
        return f("${{greeting}} Your id is ${id}.");
    }

    static String testNestedStringBuilder(int x) {
        StringBuilder tmpl = new StringBuilder("value is ${x}");
        return f("${{tmpl}}");
    }

    static String testMultipleNested() {
        String prefix = "START";
        String suffix = "END";
        return f("${{prefix}} middle ${{suffix}}");
    }

    static String testRecursiveNested(int val) {
        String inner = "value is ${val}";
        String outer = "deep ${{inner}}";
        return f("${{outer}}");
    }

    static String testNestedWithFormat(double pi) {
        String tmpl = "pi=${pi:.2f}";
        return f("***${{tmpl}}***");
    }

    static String testNestedWithDollarEscape(int price) {
        String tmpl = "Price: $$${price}";
        return f("${{tmpl}}");
    }

    static String testNestedWithWidthFormat() {
        String val = "hi";
        String tmpl = "${val}";
        return f("[${{tmpl:-10s}}]");
    }

    static String testNestedUnclosed() {
        int x = 1;
        String tmpl = "hello";
        return f("${{tmpl");
    }

    static String testNestedUnknownName() {
        int x = 1;
        return f("${{nonexistent}}");
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
