package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import ai.jacc.simplejavatemplates.Template;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Paranoid coverage of control-flow shapes that javac may compile in surprising
 * ways: lambdas, anonymous and local classes, constructors with super/this
 * chains, static and instance initializers, try/catch/finally,
 * try-with-resources (its synthetic locals like {@code $primaryExc} must be
 * filtered), enhanced-for over collections AND arrays (the synthetic iterator,
 * array-temp, length and index locals), and synchronized blocks.
 *
 * Compiles at Java 8 so it runs across the entire JDK matrix.
 *
 * Run with: java -javaagent:SimpleJavaTemplates.jar
 *   ai.jacc.simplejavatemplates.smoketest.ParanoidControlFlowTest
 */
public class ParanoidControlFlowTest {

    static int passed = 0;
    static int failed = 0;

    // Suppress optional placeholders / member access surprises by using simple
    // direct interpolation everywhere in this test.
    public static void main(String[] args) {
        Template.getGlobalTemplateExpanderInstance().setRequireLeadingDollar(false);

        section("Lambda bodies");
        lambdaCapturingPrimitive();
        lambdaCapturingObject();
        lambdaCaptureOnlyMentionedInTemplateIsElided();
        lambdaModifyingFinalArray();
        lambdaInsideLambda();
        methodReferenceCallSite();

        section("Anonymous and local classes");
        anonymousInnerClassCallSite();
        localClassCallSite();
        innerClassWithThis0();
        staticNestedClassCallSite();

        section("Constructors / initializers");
        constructorAfterSuper();
        constructorWithThisChain();
        // Verifies static initializer + instance initializer both work
        initializerSideEffectCheck();

        section("try / catch / finally");
        tryCatchFinallyCallEverywhere();
        tryWithResourcesSyntheticLocals();
        tryWithResourcesMultipleResources();
        multiCatchUnion();

        section("Enhanced for over collection / array");
        enhancedForCollection();
        enhancedForArrayPrimitive();
        enhancedForArrayObject();
        enhancedForNestedNoCollision();

        section("synchronized");
        synchronizedBlockCallSite();

        section("switch (statement, no patterns)");
        switchStatementArms();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ===================================================================
    // Lambdas
    // ===================================================================

    // The agent's contract is the LVT of the synthetic method that javac
    // generates for the lambda body — it can only see what's actually a
    // local variable there. javac compiles a lambda by emitting a synthetic
    // private static method (or instance method, if `this` is captured).
    // Captured outer-scope locals become PARAMETERS of that synthetic
    // method, BUT only if the lambda body actually uses them; if a name
    // only appears inside a string literal that the lambda passes to f(),
    // javac sees no real use and elides the capture entirely. That is
    // realistic compiler behavior, not a bug — the tests below assert what
    // actually works in practice.

    static void lambdaCapturingPrimitive() {
        try {
            int outer = 7;
            // Force capture by referencing 'outer' in code. javac then
            // passes outer as a parameter to the synthetic lambda method,
            // and it appears in the LVT under that name.
            Supplier<String> s = () -> {
                int inner = outer + 4;          // real use forces capture
                return f("outer={outer}, inner={inner}");
            };
            check("lambda with forced primitive capture",
                "outer=7, inner=11", s.get());
        } catch (Throwable t) { fail("lambda captures primitive", t); }
    }

    static void lambdaCapturingObject() {
        try {
            String name = "Alice";
            Supplier<String> s = () -> {
                String greeting = "Hi, " + name;  // real use of name
                return f("{greeting}");
            };
            check("lambda with forced object capture",
                "Hi, Alice", s.get());
        } catch (Throwable t) { fail("lambda captures object", t); }
    }

    static void lambdaCaptureOnlyMentionedInTemplateIsElided() {
        try {
            int outer = 7;
            Supplier<String> s = () -> {
                int inner = 11;
                // 'outer' appears only inside a string literal — javac sees
                // no real use, so the lambda body has no captured 'outer'
                // local. The agent correctly reports the variable as
                // not-found at this call site.
                return f("inner={inner}, outer={outer}");
            };
            try {
                s.get();
                fail("expected TemplateException for elided capture", null);
            } catch (ai.jacc.simplejavatemplates.TemplateException te) {
                String m = te.getMessage();
                check("elided-capture diagnostic mentions name",
                    true, m.contains("'outer'"));
                check("elided-capture diagnostic mentions inner is available",
                    true, m.contains("inner"));
            }
        } catch (Throwable t) { fail("elided capture", t); }
    }

    static void lambdaModifyingFinalArray() {
        try {
            // Effectively-final container; lambda mutates contents.
            String[] holder = {"first"};
            Supplier<String> s = () -> {
                String inside = holder[0];        // real use captures holder
                return f("got: {inside}");
            };
            String r1 = s.get();
            holder[0] = "second";
            String r2 = s.get();
            check("lambda first call", "got: first", r1);
            check("lambda second call sees mutated capture",
                "got: second", r2);
        } catch (Throwable t) { fail("lambda modifying final array", t); }
    }

    static void lambdaInsideLambda() {
        try {
            int a = 1;
            Supplier<Supplier<String>> outer = () -> {
                int b = 2 + a;                  // forces capture of a
                return () -> {
                    int c = 3 + a + b;          // forces capture of a, b
                    return f("a={a}, b={b}, c={c}");
                };
            };
            check("nested lambda forced captures",
                "a=1, b=3, c=7", outer.get().get());
        } catch (Throwable t) { fail("lambda inside lambda", t); }
    }

    static void methodReferenceCallSite() {
        try {
            // A method reference does not contain a call to f() itself; the
            // call lives in the referenced method. We invoke via a Supplier
            // to make sure the indirection works end-to-end.
            int marker = 42;
            String result = withMarker(marker);
            check("method called from regular path", "marker=42", result);
            // Indirect via Supplier (analogous to passing a method reference).
            Supplier<String> s = () -> withMarker(99);
            check("method called via supplier", "marker=99", s.get());
        } catch (Throwable t) { fail("method reference path", t); }
    }

    private static String withMarker(int marker) {
        return f("marker={marker}");
    }

    // ===================================================================
    // Anonymous, local, inner, static-nested classes
    // ===================================================================

    interface Producer { String produce(); }

    // For anonymous and local classes, javac stores captured outer locals
    // as private synthetic FIELDS on the inner class (named like
    // val$outer). At the call site they are not locals — they are field
    // accesses — so the agent does not surface them in the local-variable
    // map. Only locals declared inside the inner method are visible.

    static void anonymousInnerClassCallSite() {
        try {
            final int outer = 3;
            Producer p = new Producer() {
                @Override
                public String produce() {
                    int inner = 4;
                    // Captured outer is in the enclosing method's scope
                    // and reachable here by ordinary Java rules, but the
                    // agent only sees inner-method LOCALS — it cannot see
                    // outer (which lives in a synthetic field).
                    return f("inner={inner}");
                }
            };
            check("anonymous class — locals declared inside",
                "inner=4", p.produce());

            // Negative assertion: confirm the captured outer is genuinely
            // not present in the local map.
            Producer p2 = new Producer() {
                @Override
                public String produce() {
                    try {
                        return f("inner={outer}");
                    } catch (ai.jacc.simplejavatemplates.TemplateException te) {
                        return te.getMessage();
                    }
                }
            };
            check("anonymous class — captured outer NOT visible",
                true, p2.produce().contains("'outer'"));
        } catch (Throwable t) { fail("anonymous class", t); }
    }

    static void localClassCallSite() {
        try {
            final int outer = 8;
            class Local {
                String go() {
                    int inner = 9;
                    // Same story as anonymous classes: captured outer is a
                    // synthetic field, not a local — agent cannot see it.
                    return f("inner={inner}");
                }
                String tryOuter() {
                    try {
                        return f("inner={outer}");
                    } catch (ai.jacc.simplejavatemplates.TemplateException te) {
                        return te.getMessage();
                    }
                }
            }
            Local l = new Local();
            check("local class — locals declared inside",
                "inner=9", l.go());
            check("local class — captured outer NOT visible",
                true, l.tryOuter().contains("'outer'"));
        } catch (Throwable t) { fail("local class", t); }
    }

    static void innerClassWithThis0() {
        try {
            // Outer -> Inner instance; Inner.method calls f() with its own
            // locals. The 'this' of Inner appears in the map; the synthetic
            // this$0 outer reference is named with $ and so is filtered.
            Outer o = new Outer("OUTER_FIELD_VAL");
            check("inner class non-static method",
                "innerLocal=hello", o.makeInner().run());
        } catch (Throwable t) { fail("inner class with this$0", t); }
    }

    static void staticNestedClassCallSite() {
        try {
            String r = StaticNested.run("hi");
            check("static nested class", "got=hi, n=2", r);
        } catch (Throwable t) { fail("static nested class", t); }
    }

    static class Outer {
        @SuppressWarnings("unused")
        private final String label;
        Outer(String label) { this.label = label; }

        Inner makeInner() { return new Inner(); }

        class Inner {
            String run() {
                String innerLocal = "hello";
                return f("innerLocal={innerLocal}");
            }
        }
    }

    static class StaticNested {
        static String run(String got) {
            int n = got.length();
            return f("got={got}, n={n}");
        }
    }

    // ===================================================================
    // Constructors / initializers
    // ===================================================================

    static void constructorAfterSuper() {
        try {
            // Constructor body has explicit super() then a call to f()
            // using both the constructor's parameter and a local declared
            // after super().
            CtorAfterSuper c = new CtorAfterSuper(5);
            check("ctor after super()", "x=5, computed=10", c.captured);
        } catch (Throwable t) { fail("ctor after super", t); }
    }

    static void constructorWithThisChain() {
        try {
            CtorThisChain c = new CtorThisChain("inputArg");
            check("ctor that chains via this(...)", "via=delegated:inputArg", c.captured);
        } catch (Throwable t) { fail("ctor with this chain", t); }
    }

    static void initializerSideEffectCheck() {
        try {
            // Touching the class triggers <clinit>, which captures locals
            // declared inside the static initializer. Constructing an
            // instance triggers the instance initializer, which captures
            // locals declared inside it.
            String fromStatic = WithInitializers.STATIC_RESULT;
            check("static initializer call site",
                "staticLocal=2026", fromStatic);
            WithInitializers w = new WithInitializers();
            check("instance initializer call site",
                "instanceLocal=present", w.instanceResult);
        } catch (Throwable t) { fail("initializers", t); }
    }

    static class CtorAfterSuper {
        final String captured;
        CtorAfterSuper(int x) {
            super();
            int computed = x * 2;
            this.captured = f("x={x}, computed={computed}");
        }
    }

    static class CtorThisChain {
        final String captured;
        CtorThisChain(String input) {
            this("delegated:" + input, true);
        }
        private CtorThisChain(String via, boolean unused) {
            this.captured = f("via={via}");
        }
    }

    static class WithInitializers {
        static final String STATIC_RESULT;
        final String instanceResult;
        static {
            int staticLocal = 2026;
            STATIC_RESULT = f("staticLocal={staticLocal}");
        }
        {
            String instanceLocal = "present";
            this.instanceResult = f("instanceLocal={instanceLocal}");
        }
    }

    // ===================================================================
    // try / catch / finally / try-with-resources
    // ===================================================================

    static void tryCatchFinallyCallEverywhere() {
        try {
            String[] sink = new String[3];
            int outerVar = 100;
            try {
                int tryVar = 1;
                sink[0] = f("try outerVar={outerVar}, tryVar={tryVar}");
                if (tryVar == 1) throw new RuntimeException("boom");
            } catch (RuntimeException e) {
                int catchVar = 2;
                sink[1] = f("catch outerVar={outerVar}, catchVar={catchVar}");
            } finally {
                int finallyVar = 3;
                sink[2] = f("finally outerVar={outerVar}, finallyVar={finallyVar}");
            }
            check("try block",     "try outerVar=100, tryVar=1",         sink[0]);
            check("catch block",   "catch outerVar=100, catchVar=2",     sink[1]);
            check("finally block", "finally outerVar=100, finallyVar=3", sink[2]);
        } catch (Throwable t) { fail("try/catch/finally", t); }
    }

    static void tryWithResourcesSyntheticLocals() {
        try {
            String userVar = "userValue";
            String inside;
            try (InputStream is = new ByteArrayInputStream(new byte[]{42})) {
                int b = is.read();
                inside = f("userVar={userVar}, b={b}");
            }
            check("try-with-resources body",
                "userVar=userValue, b=42", inside);
        } catch (Throwable t) { fail("try-with-resources", t); }
    }

    static void tryWithResourcesMultipleResources() {
        try {
            int budget = 5;
            String inside;
            try (InputStream a = new ByteArrayInputStream(new byte[]{1});
                 InputStream b = new ByteArrayInputStream(new byte[]{2})) {
                int aVal = a.read();
                int bVal = b.read();
                inside = f("budget={budget}, aVal={aVal}, bVal={bVal}");
            }
            check("try-with-resources two resources",
                "budget=5, aVal=1, bVal=2", inside);
        } catch (Throwable t) { fail("try-with-resources two", t); }
    }

    static void multiCatchUnion() {
        try {
            String[] sink = new String[2];
            int marker = 10;
            for (int i = 0; i < 2; i++) {
                try {
                    if (i == 0) throw new IOException("io");
                    else        throw new IllegalStateException("ise");
                } catch (IOException | IllegalStateException e) {
                    String exClass = e.getClass().getSimpleName();
                    sink[i] = f("marker={marker}, exClass={exClass}");
                }
            }
            check("multi-catch IOException",
                "marker=10, exClass=IOException", sink[0]);
            check("multi-catch IllegalStateException",
                "marker=10, exClass=IllegalStateException", sink[1]);
        } catch (Throwable t) { fail("multi-catch", t); }
    }

    // ===================================================================
    // Enhanced for over collection / array
    // ===================================================================

    static void enhancedForCollection() {
        try {
            List<String> items = Arrays.asList("a", "b", "c");
            List<String> out = new ArrayList<String>();
            int prefix = 9;
            for (String item : items) {
                // The synthetic Iterator local that javac generates is named
                // with a $ and is filtered from the map by MapBuilder.
                out.add(f("prefix={prefix}, item={item}"));
            }
            check("for-each elem 0", "prefix=9, item=a", out.get(0));
            check("for-each elem 1", "prefix=9, item=b", out.get(1));
            check("for-each elem 2", "prefix=9, item=c", out.get(2));
        } catch (Throwable t) { fail("for-each collection", t); }
    }

    static void enhancedForArrayPrimitive() {
        try {
            int[] xs = {10, 20, 30};
            List<String> out = new ArrayList<String>();
            int prefix = 0;
            for (int x : xs) {
                // javac emits synthetic $arr (the array), $len, and $i locals
                // for primitive-array for-each; all $-named, all filtered.
                out.add(f("prefix={prefix}, x={x}"));
            }
            check("primitive array elem 0", "prefix=0, x=10", out.get(0));
            check("primitive array elem 1", "prefix=0, x=20", out.get(1));
            check("primitive array elem 2", "prefix=0, x=30", out.get(2));
        } catch (Throwable t) { fail("for-each int[]", t); }
    }

    static void enhancedForArrayObject() {
        try {
            String[] xs = {"x", "y"};
            List<String> out = new ArrayList<String>();
            int prefix = 1;
            for (String x : xs) {
                out.add(f("prefix={prefix}, x={x}"));
            }
            check("object array elem 0", "prefix=1, x=x", out.get(0));
            check("object array elem 1", "prefix=1, x=y", out.get(1));
        } catch (Throwable t) { fail("for-each String[]", t); }
    }

    static void enhancedForNestedNoCollision() {
        try {
            int[] outer = {1, 2};
            int[] inner = {3, 4};
            List<String> out = new ArrayList<String>();
            for (int o : outer) {
                for (int i : inner) {
                    out.add(f("o={o}, i={i}"));
                }
            }
            check("nested for-each pair 0", "o=1, i=3", out.get(0));
            check("nested for-each pair 1", "o=1, i=4", out.get(1));
            check("nested for-each pair 2", "o=2, i=3", out.get(2));
            check("nested for-each pair 3", "o=2, i=4", out.get(3));
        } catch (Throwable t) { fail("nested for-each", t); }
    }

    // ===================================================================
    // synchronized
    // ===================================================================

    static void synchronizedBlockCallSite() {
        try {
            Object lock = new Object();
            int count = 7;
            String r;
            synchronized (lock) {
                int innerCount = count + 1;
                r = f("count={count}, innerCount={innerCount}");
            }
            check("synchronized block", "count=7, innerCount=8", r);
        } catch (Throwable t) { fail("synchronized", t); }
    }

    // ===================================================================
    // switch (regular, no patterns — patterns are tested in PatternMatchingTest)
    // ===================================================================

    static void switchStatementArms() {
        try {
            check("switch arm 0", "key=zero, n=0", switchOver(0));
            check("switch arm 1", "key=one, n=1",  switchOver(1));
            check("switch arm 2", "key=two, n=2",  switchOver(2));
            check("switch default", "key=other, n=99", switchOver(99));
        } catch (Throwable t) { fail("switch arms", t); }
    }

    private static String switchOver(int n) {
        String key;
        switch (n) {
            case 0:  key = "zero"; break;
            case 1:  key = "one";  break;
            case 2:  key = "two";  break;
            default: key = "other";
        }
        return f("key={key}, n={n}");
    }

    // ===================================================================
    // Mini test framework
    // ===================================================================

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

    @SuppressWarnings("unused")
    private static Map<String, Object> unused() {
        // Avoid unused-import complaints when running the test framework.
        return new HashMap<String, Object>();
    }

    @SuppressWarnings("unused")
    private static void unused2(AtomicReference<String> ar) { /* placeholder */ }
}
