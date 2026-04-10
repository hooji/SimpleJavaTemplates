package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import ai.jacc.simplejavatemplates.TemplateException;

/**
 * Exhaustive scope-resolution tests for SimpleJavaTemplates.
 *
 * In standard Java, you cannot shadow a local variable inside a nested block
 * (compile error). But you CAN reuse the same name in sibling scopes — javac
 * then reuses the same slot, producing multiple LVT entries with the same name
 * and non-overlapping PC ranges. The agent must resolve ${name} to whichever
 * entry is live at the call site's original PC, and must NOT see entries that
 * are out of scope.
 *
 * Must be run with: java -javaagent:SimpleJavaTemplates.jar ...
 */
public class ScopeResolutionTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        testSiblingScopes_sameNameDifferentValues();
        testSiblingScopes_sameNameDifferentTypes();
        testSiblingScopes_nameGoneAfterScope();
        testDeepNesting_allVisibleAtBottom();
        testDeepNesting_callsAtEveryDepthOnTheWayDown();
        testDeepNesting_callsAtEveryDepthOnTheWayUp();
        testOuterSurvivesInnerChurn();
        testMixedVisibility_someNamesAtSomeSites();
        testFiveWaySiblings_sameNameFiveTimes();
        testSequentialForLoops();
        testMultipleCatchBlocks();
        testOuterPlusForLoopBody();
        testDiamondBranching();
        testSixLevelNesting_partialUnwind();
        testManyVariablesManyCallSites();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ====================================================================
    // 1. Sibling scopes: same name "x", different int values, t() in each
    //    Verifies the agent picks the right "x" at each call site.
    // ====================================================================
    static void testSiblingScopes_sameNameDifferentValues() {
        section("Sibling scopes: same name, different values");
        try {
            String[] results = siblingValues();
            check("first scope x", "x=10", results[0]);
            check("second scope x", "x=20", results[1]);
            check("third scope x", "x=30", results[2]);
        } catch (Throwable e) { fail("sibling values", e); }
    }

    static String[] siblingValues() {
        String[] out = new String[3];
        {
            int x = 10;
            out[0] = f("x=${x}");
        }
        {
            int x = 20;
            out[1] = f("x=${x}");
        }
        {
            int x = 30;
            out[2] = f("x=${x}");
        }
        return out;
    }

    // ====================================================================
    // 2. Sibling scopes: same name "x", different TYPES each time
    //    (int, String, long) — slot reuse with type change.
    // ====================================================================
    static void testSiblingScopes_sameNameDifferentTypes() {
        section("Sibling scopes: same name, different types");
        try {
            String[] results = siblingTypes();
            check("x as int", "x=42", results[0]);
            check("x as String", "x=hello", results[1]);
            check("x as long", "x=9999999999", results[2]);
        } catch (Throwable e) { fail("sibling types", e); }
    }

    static String[] siblingTypes() {
        String[] out = new String[3];
        {
            int x = 42;
            out[0] = f("x=${x}");
        }
        {
            String x = "hello";
            out[1] = f("x=${x}");
        }
        {
            long x = 9999999999L;
            out[2] = f("x=${x}");
        }
        return out;
    }

    // ====================================================================
    // 3. After a scoped variable ends, it must NOT be visible.
    //    t() call between sibling scopes should not see "x".
    // ====================================================================
    static void testSiblingScopes_nameGoneAfterScope() {
        section("Name gone after scope ends");
        try {
            String result = nameGoneAfterScope();
            check("marker visible, x gone", "yes", result);
        } catch (Throwable e) { fail("name gone after scope", e); }
    }

    static String nameGoneAfterScope() {
        String marker = "yes";
        {
            int x = 999;
            String ignored = f("${x}"); // x visible here
        }
        // x should NOT be in scope here
        try {
            f("${x}");
            return "BUG: x should not be visible";
        } catch (TemplateException e) {
            // Correct: x is out of scope
            return f("${marker}");
        }
    }

    // ====================================================================
    // 4. Deep nesting: 5 levels, t() at the very bottom sees ALL vars.
    // ====================================================================
    static void testDeepNesting_allVisibleAtBottom() {
        section("Deep nesting: all visible at bottom");
        try {
            String r = deepNestAllVisible();
            check("5 vars at bottom", "a=1,b=2,c=3,d=4,e=5", r);
        } catch (Throwable e) { fail("deep nesting all visible", e); }
    }

    static String deepNestAllVisible() {
        int a = 1;
        {
            int b = 2;
            {
                int c = 3;
                {
                    int d = 4;
                    {
                        int e = 5;
                        return f("a=${a},b=${b},c=${c},d=${d},e=${e}");
                    }
                }
            }
        }
    }

    // ====================================================================
    // 5. Deep nesting: t() calls at each depth on the way DOWN.
    //    Each successive call should see one MORE variable.
    // ====================================================================
    static void testDeepNesting_callsAtEveryDepthOnTheWayDown() {
        section("Deep nesting: calls on the way down");
        try {
            String[] r = deepNestDown();
            check("depth 0", "a=1", r[0]);
            check("depth 1", "a=1,b=2", r[1]);
            check("depth 2", "a=1,b=2,c=3", r[2]);
            check("depth 3", "a=1,b=2,c=3,d=4", r[3]);
        } catch (Throwable e) { fail("deep nesting down", e); }
    }

    static String[] deepNestDown() {
        String[] out = new String[4];
        int a = 1;
        out[0] = f("a=${a}");
        {
            int b = 2;
            out[1] = f("a=${a},b=${b}");
            {
                int c = 3;
                out[2] = f("a=${a},b=${b},c=${c}");
                {
                    int d = 4;
                    out[3] = f("a=${a},b=${b},c=${c},d=${d}");
                }
            }
        }
        return out;
    }

    // ====================================================================
    // 6. Deep nesting: t() calls at each depth on the way UP.
    //    As we unwind, inner vars disappear one by one.
    // ====================================================================
    static void testDeepNesting_callsAtEveryDepthOnTheWayUp() {
        section("Deep nesting: calls on the way up");
        try {
            String[] r = deepNestUp();
            check("all 4 visible", "a=1,b=2,c=3,d=4", r[0]);
            check("d gone, 3 visible", "a=1,b=2,c=3", r[1]);
            check("c+d gone, 2 visible", "a=1,b=2", r[2]);
            check("only a left", "a=1", r[3]);
        } catch (Throwable e) { fail("deep nesting up", e); }
    }

    static String[] deepNestUp() {
        String[] out = new String[4];
        int a = 1;
        {
            int b = 2;
            {
                int c = 3;
                {
                    int d = 4;
                    out[0] = f("a=${a},b=${b},c=${c},d=${d}");
                }
                out[1] = f("a=${a},b=${b},c=${c}");
            }
            out[2] = f("a=${a},b=${b}");
        }
        out[3] = f("a=${a}");
        return out;
    }

    // ====================================================================
    // 7. Outer variable "root" survives while inner names churn through
    //    5 sibling scopes — each introducing and losing "x".
    //    t() in each inner scope sees BOTH root and the current x.
    //    t() between scopes sees ONLY root.
    // ====================================================================
    static void testOuterSurvivesInnerChurn() {
        section("Outer survives inner churn");
        try {
            String[] r = outerSurvivesChurn();
            check("scope A", "root=R,x=A", r[0]);
            check("between A-B", "root=R", r[1]);
            check("scope B", "root=R,x=B", r[2]);
            check("between B-C", "root=R", r[3]);
            check("scope C", "root=R,x=C", r[4]);
            check("after all", "root=R", r[5]);
        } catch (Throwable e) { fail("outer survives churn", e); }
    }

    static String[] outerSurvivesChurn() {
        String[] out = new String[6];
        String root = "R";
        {
            String x = "A";
            out[0] = f("root=${root},x=${x}");
        }
        out[1] = f("root=${root}");
        {
            String x = "B";
            out[2] = f("root=${root},x=${x}");
        }
        out[3] = f("root=${root}");
        {
            String x = "C";
            out[4] = f("root=${root},x=${x}");
        }
        out[5] = f("root=${root}");
        return out;
    }

    // ====================================================================
    // 8. Mixed visibility: at each call site a DIFFERENT subset of names
    //    is visible. For some, resolve innermost; for others, go out 1
    //    or 2 levels because the inner name doesn't exist yet or is gone.
    // ====================================================================
    static void testMixedVisibility_someNamesAtSomeSites() {
        section("Mixed visibility across call sites");
        try {
            String[] r = mixedVisibility();
            check("only outer", "outer=100", r[0]);
            check("outer + mid", "outer=100,mid=200", r[1]);
            check("outer + mid + inner", "outer=100,mid=200,inner=300", r[2]);
            check("inner gone", "outer=100,mid=200", r[3]);
            check("mid gone too", "outer=100", r[4]);
        } catch (Throwable e) { fail("mixed visibility", e); }
    }

    static String[] mixedVisibility() {
        String[] out = new String[5];
        int outer = 100;
        out[0] = f("outer=${outer}");
        {
            int mid = 200;
            out[1] = f("outer=${outer},mid=${mid}");
            {
                int inner = 300;
                out[2] = f("outer=${outer},mid=${mid},inner=${inner}");
            }
            out[3] = f("outer=${outer},mid=${mid}");
        }
        out[4] = f("outer=${outer}");
        return out;
    }

    // ====================================================================
    // 9. Five-way sibling scopes: name "v" reused 5 times with distinct
    //    values (1,2,3,4,5). Slot is reused each time, 5 LVT entries.
    //    t() in each scope should resolve to the local "v".
    // ====================================================================
    static void testFiveWaySiblings_sameNameFiveTimes() {
        section("Five-way siblings: same name 5 times");
        try {
            String[] r = fiveWaySiblings();
            for (int i = 0; i < 5; i++) {
                check("v in scope " + (i + 1), "v=" + (i + 1), r[i]);
            }
        } catch (Throwable e) { fail("five-way siblings", e); }
    }

    static String[] fiveWaySiblings() {
        String[] out = new String[5];
        {
            int v = 1;
            out[0] = f("v=${v}");
        }
        {
            int v = 2;
            out[1] = f("v=${v}");
        }
        {
            int v = 3;
            out[2] = f("v=${v}");
        }
        {
            int v = 4;
            out[3] = f("v=${v}");
        }
        {
            int v = 5;
            out[4] = f("v=${v}");
        }
        return out;
    }

    // ====================================================================
    // 10. Sequential for-loops with same loop variable name "i".
    //     Each loop has its own LVT entry for "i" on the same slot.
    // ====================================================================
    static void testSequentialForLoops() {
        section("Sequential for-loops with same variable");
        try {
            String[] r = sequentialForLoops();
            check("first loop i=0", "i=0", r[0]);
            check("first loop i=2", "i=2", r[1]);
            check("second loop i=10", "i=10", r[2]);
            check("second loop i=12", "i=12", r[3]);
        } catch (Throwable e) { fail("sequential for loops", e); }
    }

    static String[] sequentialForLoops() {
        String[] out = new String[4];
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            if (i == 0 || i == 2) {
                out[idx++] = f("i=${i}");
            }
        }
        for (int i = 10; i < 13; i++) {
            if (i == 10 || i == 12) {
                out[idx++] = f("i=${i}");
            }
        }
        return out;
    }

    // ====================================================================
    // 11. Multiple catch blocks: each declares "e" on the same slot.
    //     t() inside each catch must resolve to the correct exception.
    // ====================================================================
    static void testMultipleCatchBlocks() {
        section("Multiple catch blocks with same var name");
        try {
            String[] r = multipleCatches();
            check("first catch e", true, r[0].contains("first"));
            check("second catch e", true, r[1].contains("second"));
            check("third catch e", true, r[2].contains("third"));
        } catch (Throwable e) { fail("multiple catches", e); }
    }

    static String[] multipleCatches() {
        String[] out = new String[3];
        try {
            throw new RuntimeException("first");
        } catch (RuntimeException e) {
            out[0] = f("${e}");
        }
        try {
            throw new RuntimeException("second");
        } catch (RuntimeException e) {
            out[1] = f("${e}");
        }
        try {
            throw new RuntimeException("third");
        } catch (RuntimeException e) {
            out[2] = f("${e}");
        }
        return out;
    }

    // ====================================================================
    // 12. Outer variable persists through a for-loop body.
    //     Inside the loop: both "prefix" (outer) and "i" (loop) visible.
    //     After the loop: only "prefix" visible, "i" is gone.
    // ====================================================================
    static void testOuterPlusForLoopBody() {
        section("Outer variable + for-loop body");
        try {
            String[] r = outerPlusLoop();
            check("inside loop i=0", "prefix=P,i=0", r[0]);
            check("inside loop i=1", "prefix=P,i=1", r[1]);
            check("inside loop i=2", "prefix=P,i=2", r[2]);
            check("after loop", "prefix=P", r[3]);
        } catch (Throwable e) { fail("outer plus loop", e); }
    }

    static String[] outerPlusLoop() {
        String[] out = new String[4];
        String prefix = "P";
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            out[idx++] = f("prefix=${prefix},i=${i}");
        }
        out[idx] = f("prefix=${prefix}");
        return out;
    }

    // ====================================================================
    // 13. Diamond-like control flow: if/else branches each introduce a
    //     variable with the same name "result". t() in each branch sees
    //     its own "result"; t() after the if/else does not see either.
    // ====================================================================
    static void testDiamondBranching() {
        section("Diamond branching: if/else same name");
        try {
            String[] r = diamondBranch(true);
            check("true branch", "result=YES", r[0]);
            check("after if (true)", "flag=true", r[1]);

            String[] r2 = diamondBranch(false);
            check("false branch", "result=NO", r2[0]);
            check("after if (false)", "flag=false", r2[1]);
        } catch (Throwable e) { fail("diamond branching", e); }
    }

    static String[] diamondBranch(boolean flag) {
        String[] out = new String[2];
        if (flag) {
            String result = "YES";
            out[0] = f("result=${result}");
        } else {
            String result = "NO";
            out[0] = f("result=${result}");
        }
        out[1] = f("flag=${flag}");
        return out;
    }

    // ====================================================================
    // 14. Six-level nesting, t() calls at levels 6, 4, and 2.
    //     At level 6: vars a..f all visible (6 vars).
    //     At level 4 (after 5+6 ended): a,b,c,d visible (4 vars).
    //     At level 2 (after 3-6 ended): a,b visible (2 vars).
    // ====================================================================
    static void testSixLevelNesting_partialUnwind() {
        section("6-level nesting, partial unwind");
        try {
            String[] r = sixLevelPartialUnwind();
            check("level 6: all 6", "a=1,b=2,c=3,d=4,e=5,f=6", r[0]);
            check("level 4: 4 vars", "a=1,b=2,c=3,d=4", r[1]);
            check("level 2: 2 vars", "a=1,b=2", r[2]);
        } catch (Throwable e) { fail("six level partial unwind", e); }
    }

    static String[] sixLevelPartialUnwind() {
        String[] out = new String[3];
        int a = 1;
        {
            int b = 2;
            {
                int c = 3;
                {
                    int d = 4;
                    {
                        int e = 5;
                        {
                            int f = 6;
                            out[0] = f("a=${a},b=${b},c=${c},d=${d},e=${e},f=${f}");
                        }
                    }
                    out[1] = f("a=${a},b=${b},c=${c},d=${d}");
                }
            }
            out[2] = f("a=${a},b=${b}");
        }
        return out;
    }

    // ====================================================================
    // 15. Stress test: 8 variables, 6 call sites, complex scope overlaps.
    //     This is the "kitchen sink" test — a single method with many
    //     variables entering and leaving scope at different points, and
    //     t() calls scattered throughout. Each call site sees a different
    //     set of variables. Variables with the same name ("tag") appear
    //     in multiple sibling scopes.
    // ====================================================================
    static void testManyVariablesManyCallSites() {
        section("Stress test: 8 vars, 6 call sites");
        try {
            String[] r = kitchenSink();
            check("site 0: only base", "base=B", r[0]);
            check("site 1: base+alpha+tag=T1", "base=B,alpha=A,tag=T1", r[1]);
            check("site 2: base+alpha (tag gone)", "base=B,alpha=A", r[2]);
            check("site 3: base+alpha+beta+tag=T2", "base=B,alpha=A,beta=BB,tag=T2", r[3]);
            check("site 4: base+alpha+beta (tag gone)", "base=B,alpha=A,beta=BB", r[4]);
            check("site 5: base only (rest gone)", "base=B", r[5]);
        } catch (Throwable e) { fail("kitchen sink", e); }
    }

    static String[] kitchenSink() {
        String[] out = new String[6];
        String base = "B";
        out[0] = f("base=${base}");
        {
            String alpha = "A";
            {
                String tag = "T1";
                out[1] = f("base=${base},alpha=${alpha},tag=${tag}");
            }
            // tag "T1" is gone
            out[2] = f("base=${base},alpha=${alpha}");
            {
                String beta = "BB";
                {
                    String tag = "T2";
                    out[3] = f("base=${base},alpha=${alpha},beta=${beta},tag=${tag}");
                }
                // tag "T2" is gone
                out[4] = f("base=${base},alpha=${alpha},beta=${beta}");
            }
        }
        // alpha, beta, tag all gone
        out[5] = f("base=${base}");
        return out;
    }

    // ====================================================================
    // Test harness
    // ====================================================================

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
