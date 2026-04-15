package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import ai.jacc.simplejavatemplates.Template;
import ai.jacc.simplejavatemplates.TemplateExpander;
import ai.jacc.simplejavatemplates.TemplateException;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for TemplateExpander features: simple mode, optional placeholders,
 * container expansion, and member/method access.
 * Must be run with: java -javaagent:SimpleJavaTemplates.jar ...
 */
public class TemplateExpanderTest {

    static int passed = 0;
    static int failed = 0;

    // --- Test helper types ---

    public static class User {
        public String name;
        private int age;
        private boolean active;

        public User(String name, int age, boolean active) {
            this.name = name; this.age = age; this.active = active;
        }
        public int getAge() { return age; }
        public boolean isActive() { return active; }
        public String greeting() { return "Hello, " + name; }
    }

    // Record-style class (accessor methods named after fields)
    public static class Point {
        private final int x;
        private final int y;

        public Point(int x, int y) { this.x = x; this.y = y; }
        public int x() { return x; }
        public int y() { return y; }
    }

    public static class Address {
        private String city;
        public Address(String city) { this.city = city; }
        public String getCity() { return city; }
    }

    public static class Person {
        private Address address;
        public Person(Address address) { this.address = address; }
        public Address getAddress() { return address; }
    }

    public static void main(String[] args) {
        // Default: requireLeadingDollar = false (simple mode)

        testSimpleMode();
        testSimpleModeEscaping();
        testDollarPrefixStillWorks();
        testOptionalPlaceholders();
        testContainerExpansion();
        testMemberAccess();
        testRecordStyleAccess();
        testChainedAccess();
        testMethodCalls();
        testInstanceExpander();
        testErrorConditions();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ========================================================================
    // Simple mode: {name} without $
    // ========================================================================

    static void testSimpleMode() {
        section("Simple mode ({name} without $)");

        try {
            String r = testSimpleBasic("Alice", 30);
            check("simple basic", "Alice is 30", r);
        } catch (Throwable e) { fail("simple basic", e); }

        try {
            String r = testSimpleMultiple(1, 2, 3);
            check("simple multiple", "1, 2, 3", r);
        } catch (Throwable e) { fail("simple multiple", e); }

        try {
            String r = testSimpleNoPlaceholders();
            check("simple no placeholders", "Hello world", r);
        } catch (Throwable e) { fail("simple no placeholders", e); }

        try {
            String r = testSimpleEmpty();
            check("simple empty template", "", r);
        } catch (Throwable e) { fail("simple empty", e); }
    }

    static String testSimpleBasic(String name, int age) {
        return f("{name} is {age}");
    }
    static String testSimpleMultiple(int a, int b, int c) {
        return f("{a}, {b}, {c}");
    }
    static String testSimpleNoPlaceholders() {
        int dummy = 1;
        return f("Hello world");
    }
    static String testSimpleEmpty() {
        int dummy = 1;
        return f("");
    }

    // ========================================================================
    // Brace escaping
    // ========================================================================

    static void testSimpleModeEscaping() {
        section("Brace escaping ({{ and }})");

        try {
            int x = 1;
            String r = f("a {{literal}} brace");
            check("{{ escape", "a {literal} brace", r);
        } catch (Throwable e) { fail("{{ escape", e); }

        try {
            int x = 42;
            String r = f("val={x}, json={{\"key\": 1}}");
            check("mixed braces", "val=42, json={\"key\": 1}", r);
        } catch (Throwable e) { fail("mixed braces", e); }

        try {
            int x = 1;
            String r = f("$$100");
            check("$$ still works", "$100", r);
        } catch (Throwable e) { fail("$$ still works", e); }
    }

    // ========================================================================
    // $ prefix still works
    // ========================================================================

    static void testDollarPrefixStillWorks() {
        section("$ prefix still works");

        try {
            String r = testDollarPrefix("World");
            check("${} works", "Hello World", r);
        } catch (Throwable e) { fail("${} works", e); }

        try {
            String r = testDollarFormat(3.14159);
            check("${:fmt} works", "pi=3.14", r);
        } catch (Throwable e) { fail("${:fmt} works", e); }

        try {
            String r = testNestedTemplate();
            check("${{}} works", "Hello Alice!", r);
        } catch (Throwable e) { fail("${{}} works", e); }
    }

    static String testDollarPrefix(String name) {
        return f("Hello ${name}");
    }
    static String testDollarFormat(double pi) {
        return f("pi=${pi:.2f}");
    }
    static String testNestedTemplate() {
        String name = "Alice";
        String tmpl = "Hello {name}!";
        return f("${{tmpl}}");
    }

    // ========================================================================
    // Optional placeholders ({?name})
    // ========================================================================

    static void testOptionalPlaceholders() {
        section("Optional placeholders ({?name})");

        // Non-null: renders normally
        try {
            String r = testOptionalPresent("Alice");
            check("optional present", "Hello Alice!", r);
        } catch (Throwable e) { fail("optional present", e); }

        // Null: suppressed
        try {
            String r = testOptionalNull();
            check("optional null", "Hello !", r);
        } catch (Throwable e) { fail("optional null", e); }

        // Null with newline consumption
        try {
            String r = testOptionalNewline("line1", null, "line3");
            check("optional newline", "line1\nline3\n", r);
        } catch (Throwable e) { fail("optional newline", e); }

        // Null with \r\n consumption
        try {
            String r = testOptionalCrLf(null, "end");
            check("optional crlf", "end\r\n", r);
        } catch (Throwable e) { fail("optional crlf", e); }

        // All present — no suppression
        try {
            String r = testOptionalAllPresent("a", "b", "c");
            check("optional all present", "a\nb\nc\n", r);
        } catch (Throwable e) { fail("optional all present", e); }
    }

    static String testOptionalPresent(String name) {
        return f("Hello {?name}!");
    }
    static String testOptionalNull() {
        String name = null;
        return f("Hello {?name}!");
    }
    static String testOptionalNewline(String a, String b, String c) {
        return f("{a}\n{?b}\n{c}\n");
    }
    static String testOptionalCrLf(String a, String b) {
        return f("{?a}\r\n{b}\r\n");
    }
    static String testOptionalAllPresent(String a, String b, String c) {
        return f("{?a}\n{?b}\n{?c}\n");
    }

    // ========================================================================
    // Container expansion
    // ========================================================================

    static void testContainerExpansion() {
        section("Container expansion");

        // String array
        try {
            String[] arr = {"a", "b", "c"};
            String r = f("{arr}");
            check("String[]", "[a, b, c]", r);
        } catch (Throwable e) { fail("String[]", e); }

        // int array
        try {
            int[] nums = {1, 2, 3};
            String r = f("{nums}");
            check("int[]", "[1, 2, 3]", r);
        } catch (Throwable e) { fail("int[]", e); }

        // List
        try {
            List<String> items = Arrays.asList("x", "y", "z");
            String r = f("{items}");
            check("List", "[x, y, z]", r);
        } catch (Throwable e) { fail("List", e); }

        // Empty array
        try {
            String[] empty = {};
            String r = f("{empty}");
            check("empty array", "[]", r);
        } catch (Throwable e) { fail("empty array", e); }

        // Non-container still uses toString
        try {
            String s = "hello";
            String r = f("{s}");
            check("non-container", "hello", r);
        } catch (Throwable e) { fail("non-container", e); }
    }

    // ========================================================================
    // Member access
    // ========================================================================

    static void testMemberAccess() {
        section("Member access");

        // Public field
        try {
            User user = new User("Alice", 30, true);
            String r = f("{user.name}");
            check("public field", "Alice", r);
        } catch (Throwable e) { fail("public field", e); }

        // JavaBean getter (getAge)
        try {
            User user = new User("Bob", 25, false);
            String r = f("{user.age}");
            check("getter", "25", r);
        } catch (Throwable e) { fail("getter", e); }

        // Boolean isXxx getter
        try {
            User user = new User("Alice", 30, true);
            String r = f("{user.active}");
            check("isXxx getter", "true", r);
        } catch (Throwable e) { fail("isXxx getter", e); }
    }

    // ========================================================================
    // Record-style accessors (name() method)
    // ========================================================================

    static void testRecordStyleAccess() {
        section("Record-style accessors");

        try {
            Point p = new Point(10, 20);
            String r = f("({p.x}, {p.y})");
            check("record-style", "(10, 20)", r);
        } catch (Throwable e) { fail("record-style", e); }

        // Also works with explicit () syntax
        try {
            Point p = new Point(3, 4);
            String r = f("{p.x()} and {p.y()}");
            check("record explicit ()", "3 and 4", r);
        } catch (Throwable e) { fail("record explicit ()", e); }
    }

    // ========================================================================
    // Chained member access
    // ========================================================================

    static void testChainedAccess() {
        section("Chained member access");

        try {
            Person person = new Person(new Address("Springfield"));
            String r = f("{person.address.city}");
            check("chained", "Springfield", r);
        } catch (Throwable e) { fail("chained", e); }

        // Null in chain
        try {
            Person person = new Person(null);
            String r = f("{person.address.city}");
            check("null chain", "null", r);
        } catch (Throwable e) { fail("null chain", e); }

        // Optional + null chain = suppressed
        try {
            Person person = new Person(null);
            String r = f("city: {?person.address.city}!");
            check("optional null chain", "city: !", r);
        } catch (Throwable e) { fail("optional null chain", e); }
    }

    // ========================================================================
    // Explicit method calls
    // ========================================================================

    static void testMethodCalls() {
        section("Explicit method calls");

        try {
            User user = new User("Alice", 30, true);
            String r = f("{user.greeting()}");
            check("method call", "Hello, Alice", r);
        } catch (Throwable e) { fail("method call", e); }

        // String.length()
        try {
            String msg = "hello";
            String r = f("{msg.length()}");
            check("String.length()", "5", r);
        } catch (Throwable e) { fail("String.length()", e); }

        // toString()
        try {
            int[] arr = {1, 2};
            Point p = new Point(7, 8);
            String r = f("{p.x()},{p.y()}");
            check("chained methods", "7,8", r);
        } catch (Throwable e) { fail("chained methods", e); }
    }

    // ========================================================================
    // Instance TemplateExpander
    // ========================================================================

    static void testInstanceExpander() {
        section("Instance TemplateExpander");

        // Instance with requireLeadingDollar=true
        try {
            String r = testInstanceLegacy("Alice");
            check("instance legacy mode", "Hello {name}! Alice", r);
        } catch (Throwable e) { fail("instance legacy mode", e); }

        // Instance with expandContainers=false
        try {
            String r = testInstanceNoContainers();
            check("instance no containers", true, r.startsWith("[L") || r.equals("[I@") || r.contains("@"));
            // Without container expansion, arrays use Object.toString()
        } catch (Throwable e) { fail("instance no containers", e); }
    }

    static String testInstanceLegacy(String name) {
        TemplateExpander legacy = new TemplateExpander().setRequireLeadingDollar(true);
        return legacy.f("Hello {name}! ${name}");
    }

    static String testInstanceNoContainers() {
        TemplateExpander noContainers = new TemplateExpander().setExpandContainers(false);
        String[] arr = {"a", "b"};
        return noContainers.f("{arr}");
    }

    // ========================================================================
    // Error conditions
    // ========================================================================

    static void testErrorConditions() {
        section("Error conditions");

        // Unclosed brace
        try {
            int x = 1;
            f("{unclosed");
            fail("unclosed brace", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unclosed brace error", true, e.getMessage().contains("unclosed"));
        } catch (Throwable e) { fail("unclosed brace", e); }

        // Empty placeholder
        try {
            int x = 1;
            f("{}");
            fail("empty placeholder", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("empty placeholder error", true, e.getMessage().contains("Empty") || e.getMessage().contains("not a valid"));
        } catch (Throwable e) { fail("empty placeholder", e); }

        // Unknown variable
        try {
            int x = 1;
            f("{nonexistent}");
            fail("unknown var", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("unknown var error", true, e.getMessage().contains("not found"));
        } catch (Throwable e) { fail("unknown var", e); }

        // No such method
        try {
            String s = "hello";
            f("{s.nonexistent()}");
            fail("no such method", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("no such method error", true, e.getMessage().contains("No public method"));
        } catch (Throwable e) { fail("no such method", e); }

        // No such property
        try {
            Point p = new Point(1, 2);
            f("{p.z}");
            fail("no such property", new AssertionError("expected TemplateException"));
        } catch (TemplateException e) {
            check("no such property error", true, e.getMessage().contains("Cannot resolve"));
        } catch (Throwable e) { fail("no such property", e); }
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
        if (expected == null && actual == null) {
            System.out.println("  PASS " + label + ": null");
            passed++;
        } else if (expected != null && expected.equals(actual)) {
            System.out.println("  PASS " + label + ": " + actual);
            passed++;
        } else {
            System.err.println("  FAIL " + label + ": expected <" + expected +
                "> but got <" + actual + ">");
            failed++;
        }
    }

    static void fail(String label, Throwable e) {
        System.err.println("  FAIL " + label + ": " + e);
        e.printStackTrace(System.err);
        failed++;
    }
}
