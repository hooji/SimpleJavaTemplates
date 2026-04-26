package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import java.util.List;

/**
 * Verifies the agent's PC-based scope resolution against Java 21+ pattern
 * variable scoping. Pattern variables ({@code if (obj instanceof String s)},
 * switch pattern bindings, etc.) appear in the LocalVariableTable as entries
 * with narrow PC ranges covering only the scope where the binding is in scope.
 *
 * The agent's existing scope-resolution machinery filters logicals by
 * {@code originalPc in [start, start+length)} and applies inner-scope-wins
 * shadowing, so pattern variables should "just work" — these tests pin that
 * down with concrete scenarios.
 *
 * Compiled with {@code testSource=21 / testTarget=21} (see pom.xml). Run with:
 *   java -javaagent:SimpleJavaTemplates.jar
 *     ai.jacc.simplejavatemplates.smoketest.PatternMatchingTest
 */
public class PatternMatchingTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        section("instanceof pattern: bind in if-body");
        try {
            check("string -> bound", "got string: hello",
                instanceofBindIf("hello"));
            check("non-string -> not bound", "no string here",
                instanceofBindIf(42));
        } catch (Throwable t) { fail("bind in if", t); }

        section("instanceof pattern: flow scoping after !instanceof");
        try {
            check("flow-scoped binding", "len=5",
                instanceofFlowScope("hello"));
            check("early return when not string", "not a string",
                instanceofFlowScope(42));
        } catch (Throwable t) { fail("flow scope", t); }

        section("instanceof chain: same binding name in sibling branches");
        try {
            check("String arm", "s=alpha (str)", instanceofChain("alpha"));
            check("Integer arm", "s=42 (int)", instanceofChain(42));
            check("Boolean arm", "s=true (bool)", instanceofChain(Boolean.TRUE));
            check("default arm (regular local)", "s=other",
                instanceofChain(3.14));
        } catch (Throwable t) { fail("instanceof chain", t); }

        section("switch pattern");
        try {
            check("String pattern", "got string of length 5: hello",
                switchPattern("hello"));
            check("Integer pattern", "got int doubled: 14",
                switchPattern(7));
            check("null pattern", "null",
                switchPattern(null));
            check("default arm", "other: 3.14",
                switchPattern(3.14));
        } catch (Throwable t) { fail("switch pattern", t); }

        section("switch pattern: same binding name across arms (slot reuse)");
        try {
            check("String arm", "n=5",   switchSameName("hello"));
            check("Integer arm", "n=7",  switchSameName(7));
            check("List arm", "n=2",
                switchSameName(List.of("a", "b")));
            check("default", "other",    switchSameName(3.14));
        } catch (Throwable t) { fail("switch same name", t); }

        section("multiple call sites with overlapping/non-overlapping bindings");
        try {
            check("complex method", "before=Z|inIf=X|inLoop[0]=A|inLoop[1]=B|after=Z",
                multiSiteWithPatterns(true, "X", new String[]{"A", "B"}));
        } catch (Throwable t) { fail("multi-site patterns", t); }

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---- instanceof patterns ----

    static String instanceofBindIf(Object obj) {
        if (obj instanceof String s) {
            // s is in the LVT with a range covering only this branch
            return f("got string: {s}");
        }
        return f("no string here");
    }

    static String instanceofFlowScope(Object obj) {
        if (!(obj instanceof String s)) {
            // s is NOT in scope here (negated pattern)
            return f("not a string");
        }
        // Flow scoping: s is in scope from here on
        int len = s.length();
        return f("len={len}");
    }

    static String instanceofChain(Object o) {
        if (o instanceof String s) {
            return f("s={s} (str)");
        } else if (o instanceof Integer s) {
            // Reuses binding name "s" in a sibling scope. javac may emit a
            // separate LVT entry (potentially on the same slot as the prior
            // s) with a non-overlapping PC range. Inner-scope-wins shadowing
            // must select THIS s at this call site.
            return f("s={s} (int)");
        } else if (o instanceof Boolean s) {
            return f("s={s} (bool)");
        }
        // Plain local with the same name in yet another sibling scope.
        String s = "other";
        return f("s={s}");
    }

    // ---- switch patterns ----

    static String switchPattern(Object o) {
        return switch (o) {
            case String s -> {
                int len = s.length();
                yield f("got string of length {len}: {s}");
            }
            case Integer n -> {
                int doubled = n * 2;
                yield f("got int doubled: {doubled}");
            }
            case null -> f("null");
            default -> f("other: {o}");
        };
    }

    static String switchSameName(Object o) {
        // Same binding name "s" in three arms; n is recomputed per arm.
        // Each arm's LVT entry for s/n covers only that arm's PC range.
        return switch (o) {
            case String s -> {
                int n = s.length();
                yield f("n={n}");
            }
            case Integer s -> {
                int n = s;
                yield f("n={n}");
            }
            case List<?> s -> {
                int n = s.size();
                yield f("n={n}");
            }
            default -> "other";
        };
    }

    // ---- mixed: many call sites, mixing patterns with regular locals ----

    static String multiSiteWithPatterns(boolean cond, Object payload, Object[] arr) {
        String before = "Z";
        String beforeStr = f("before={before}");

        String inIfStr = "<unset>";
        if (cond && payload instanceof String inIf) {
            // inIf is only in scope here
            inIfStr = f("inIf={inIf}");
        }

        StringBuilder loop = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            // The for-loop body is a fresh scope each iteration; each value
            // of i is captured at this call site's PC.
            if (arr[i] instanceof String inLoop) {
                loop.append(f("inLoop[{i}]={inLoop}"));
                if (i + 1 < arr.length) loop.append('|');
            }
        }

        String afterStr = f("after={before}");

        return beforeStr + "|" + inIfStr + "|" + loop + "|" + afterStr;
    }

    // ---- mini test framework ----

    static void section(String t) { System.out.println("\n--- " + t + " ---"); }

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
