package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import ai.jacc.simplejavatemplates.RequiresCallerLocalVariableDetails;
import ai.jacc.simplejavatemplates.Template;
import ai.jacc.simplejavatemplates.TemplateException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Paranoid coverage of variable types, type-shapes, and slot widths. Focuses
 * on category-2 slots (long / double — they occupy two adjacent JVM slots),
 * adjacency patterns that have historically tripped up bytecode rewriters,
 * generics (post-erasure), arrays, nulls under various declared types,
 * boxed-vs-primitive mixing, and method overload resolution.
 *
 * Compiles at Java 8 so it runs across the entire JDK matrix.
 *
 * Run with: java -javaagent:SimpleJavaTemplates.jar
 *   ai.jacc.simplejavatemplates.smoketest.ParanoidTypesTest
 */
public class ParanoidTypesTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        Template.getGlobalTemplateExpanderInstance().setRequireLeadingDollar(false);

        section("All eight primitives in one frame");
        allPrimitivesAtOnce();

        section("Category-2 (long / double) slot widths");
        singleLong();
        singleDouble();
        twoLongsAdjacent();
        twoDoublesAdjacent();
        longDoubleLongDouble();
        longBetweenInts();
        doubleBetweenObjects();
        primitivesAroundCat2InNarrowScopes();

        section("char specifically");
        charPrimitive();
        charBoxed();

        section("byte / short");
        byteAndShort();

        section("Boxed types alongside primitives");
        boxedPrimitivesAtOnce();

        section("Arrays of every primitive");
        arraysOfEachPrimitive();

        section("Multi-dimensional arrays");
        multiDimensionalArrays();

        section("Generics survive erasure");
        genericListMap();
        nestedGenerics();

        section("Variables holding null with various declared types");
        nullsWithDeclaredTypes();

        section("Variables holding subtype (declared as supertype)");
        subtypePolymorphism();

        section("Method overloads with same annotated name");
        overloadResolution();

        section("Map collisions: key conflict between caller method args and locals");
        argShadowing();

        section("Very many locals in one frame");
        manyLocalsAtOnce();

        section("Stack-map after wide stores");
        stackMapWideStores();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // =================================================================
    // All eight primitives in one frame
    // =================================================================

    static void allPrimitivesAtOnce() {
        try {
            boolean bo = true;
            byte    by = 7;
            short   sh = 200;
            char    ch = 'Q';
            int     in = 12345;
            long    lo = 9_876_543_210L;
            float   fl = 1.5f;
            double  du = 2.25;
            String  result = f("bo={bo} by={by} sh={sh} ch={ch} in={in} lo={lo} fl={fl} du={du}");
            check("all primitives in one frame",
                "bo=true by=7 sh=200 ch=Q in=12345 lo=9876543210 fl=1.5 du=2.25",
                result);
        } catch (Throwable t) { fail("all primitives", t); }
    }

    // =================================================================
    // Category-2 slot widths
    // =================================================================

    static void singleLong() {
        try {
            long n = Long.MIN_VALUE + 7;
            check("min long",
                "n=" + n, f("n={n}"));
        } catch (Throwable t) { fail("single long", t); }
    }

    static void singleDouble() {
        try {
            double d = Double.MAX_VALUE / 3.0;
            check("near-max double",
                "d=" + d, f("d={d}"));
        } catch (Throwable t) { fail("single double", t); }
    }

    static void twoLongsAdjacent() {
        try {
            long a = 1_000_000_000_000L;
            long b = 2_000_000_000_000L;
            check("two longs adjacent",
                "a=1000000000000 b=2000000000000",
                f("a={a} b={b}"));
        } catch (Throwable t) { fail("two longs adjacent", t); }
    }

    static void twoDoublesAdjacent() {
        try {
            double a = 1.5;
            double b = 0.25;
            double sum = a + b;
            check("two doubles + sum",
                "a=1.5 b=0.25 sum=1.75",
                f("a={a} b={b} sum={sum}"));
        } catch (Throwable t) { fail("two doubles adjacent", t); }
    }

    static void longDoubleLongDouble() {
        try {
            // Tests mixed cat-2 placement: each pair occupies 2 slots, so
            // adjacency matters for both slot allocation in the original
            // and the agent's renumbering.
            long   l1 = 11L;
            double d1 = 1.5;
            long   l2 = 22L;
            double d2 = 2.5;
            check("L D L D layout",
                "l1=11 d1=1.5 l2=22 d2=2.5",
                f("l1={l1} d1={d1} l2={l2} d2={d2}"));
        } catch (Throwable t) { fail("L D L D", t); }
    }

    static void longBetweenInts() {
        try {
            int  i1 = 1;
            long ln = 99L;
            int  i2 = 2;
            check("int / long / int",
                "i1=1 ln=99 i2=2",
                f("i1={i1} ln={ln} i2={i2}"));
        } catch (Throwable t) { fail("long between ints", t); }
    }

    static void doubleBetweenObjects() {
        try {
            String first  = "alpha";
            double middle = 3.14;
            String last   = "omega";
            check("Object / double / Object",
                "first=alpha middle=3.14 last=omega",
                f("first={first} middle={middle} last={last}"));
        } catch (Throwable t) { fail("double between objects", t); }
    }

    static void primitivesAroundCat2InNarrowScopes() {
        try {
            // Narrow scopes around a cat-2 variable — exercises the slot
            // sharing logic for split LVT entries while a category-2
            // neighbour sits next to them.
            String[] sink = new String[3];
            int outer = 1;
            {
                long widePart = 100L;
                int  follow   = 7;
                sink[0] = f("outer={outer} widePart={widePart} follow={follow}");
            }
            {
                double wideAlt = 0.5;
                int    after   = 8;
                sink[1] = f("outer={outer} wideAlt={wideAlt} after={after}");
            }
            sink[2] = f("outer={outer}");
            check("narrow scope w/ long",
                "outer=1 widePart=100 follow=7", sink[0]);
            check("narrow scope w/ double",
                "outer=1 wideAlt=0.5 after=8", sink[1]);
            check("after both narrow scopes",
                "outer=1", sink[2]);
        } catch (Throwable t) { fail("cat-2 narrow scopes", t); }
    }

    // =================================================================
    // char
    // =================================================================

    static void charPrimitive() {
        try {
            char c = 'A';
            check("char prints unboxed", "c=A", f("c={c}"));
            char nl = '\n';
            check("char newline literal", "nl=\n", f("nl={nl}"));
        } catch (Throwable t) { fail("char primitive", t); }
    }

    static void charBoxed() {
        try {
            Character c = 'Z';
            check("Character boxed", "c=Z", f("c={c}"));
        } catch (Throwable t) { fail("char boxed", t); }
    }

    // =================================================================
    // byte / short
    // =================================================================

    static void byteAndShort() {
        try {
            byte  b = -7;
            short s = -3000;
            check("byte and short",
                "b=-7 s=-3000",
                f("b={b} s={s}"));
            byte  bMax = Byte.MAX_VALUE;
            short sMax = Short.MAX_VALUE;
            check("byte/short max",
                "bMax=127 sMax=32767",
                f("bMax={bMax} sMax={sMax}"));
        } catch (Throwable t) { fail("byte/short", t); }
    }

    // =================================================================
    // Boxed primitives alongside primitives
    // =================================================================

    static void boxedPrimitivesAtOnce() {
        try {
            Boolean   bO = true;
            Byte      bY = (byte) 1;
            Short     sH = (short) 2;
            Character cH = 'X';
            Integer   iN = 3;
            Long      lO = 4L;
            Float     fL = 5.5f;
            Double    dU = 6.5;
            check("all boxed",
                "bO=true bY=1 sH=2 cH=X iN=3 lO=4 fL=5.5 dU=6.5",
                f("bO={bO} bY={bY} sH={sH} cH={cH} iN={iN} lO={lO} fL={fL} dU={dU}"));
        } catch (Throwable t) { fail("all boxed", t); }
    }

    // =================================================================
    // Arrays of each primitive
    // =================================================================

    static void arraysOfEachPrimitive() {
        try {
            boolean[] bo = {true, false};
            byte[]    by = {1, 2, 3};
            short[]   sh = {10, 20, 30};
            char[]    ch = {'a', 'b'};
            int[]     in = {100, 200};
            long[]    lo = {1L, 2L, 3L};
            float[]   fl = {1.5f, 2.5f};
            double[]  du = {0.5, 0.25, 0.125};
            check("boolean[]", "bo=[true, false]",      f("bo={bo}"));
            check("byte[]",    "by=[1, 2, 3]",           f("by={by}"));
            check("short[]",   "sh=[10, 20, 30]",        f("sh={sh}"));
            check("char[]",    "ch=[a, b]",              f("ch={ch}"));
            check("int[]",     "in=[100, 200]",          f("in={in}"));
            check("long[]",    "lo=[1, 2, 3]",           f("lo={lo}"));
            check("float[]",   "fl=[1.5, 2.5]",          f("fl={fl}"));
            check("double[]",  "du=[0.5, 0.25, 0.125]",  f("du={du}"));
        } catch (Throwable t) { fail("arrays of each primitive", t); }
    }

    // =================================================================
    // Multi-dimensional arrays
    // =================================================================

    static void multiDimensionalArrays() {
        try {
            int[][] grid = {{1, 2}, {3, 4}};
            // A 2-D array is a 1-D array whose elements are 1-D arrays;
            // container expansion only descends one level.
            String res = f("grid={grid}");
            check("2D int array — outer container expanded",
                true, res.startsWith("grid=[") && res.contains("[I@"));

            String[][] words = {{"a", "b"}, {"c"}};
            String wr = f("words={words}");
            check("2D String array — outer container expanded",
                true, wr.startsWith("words=[") && wr.contains("[Ljava.lang.String"));
        } catch (Throwable t) { fail("multi-dim arrays", t); }
    }

    // =================================================================
    // Generics survive erasure
    // =================================================================

    static void genericListMap() {
        try {
            List<Integer> list = Arrays.asList(1, 2, 3);
            Map<String, Integer> map = new LinkedHashMap<String, Integer>();
            map.put("a", 1);
            map.put("b", 2);
            check("List<Integer>",
                "list=[1, 2, 3]", f("list={list}"));
            check("LinkedHashMap default toString preserved",
                "map={a=1, b=2}", f("map={map}"));
        } catch (Throwable t) { fail("List/Map generics", t); }
    }

    static void nestedGenerics() {
        try {
            Map<String, List<Integer>> nested = new LinkedHashMap<String, List<Integer>>();
            nested.put("evens", Arrays.asList(2, 4));
            nested.put("odds",  Arrays.asList(1, 3, 5));
            check("Map<String, List<Integer>>",
                "nested={evens=[2, 4], odds=[1, 3, 5]}", f("nested={nested}"));
        } catch (Throwable t) { fail("nested generics", t); }
    }

    // =================================================================
    // Variables holding null
    // =================================================================

    static void nullsWithDeclaredTypes() {
        try {
            String       s   = null;
            Integer      i   = null;
            Object       o   = null;
            int[]        arr = null;
            List<String> lst = null;
            check("null String",       "s=null",   f("s={s}"));
            check("null Integer",      "i=null",   f("i={i}"));
            check("null Object",       "o=null",   f("o={o}"));
            check("null int[]",        "arr=null", f("arr={arr}"));
            check("null List",         "lst=null", f("lst={lst}"));
            // Optional-placeholder syntax should suppress null entirely.
            check("null with ?",       "lst=",    f("lst={?lst}"));
        } catch (Throwable t) { fail("nulls", t); }
    }

    // =================================================================
    // Subtype polymorphism — declared as supertype, holds subtype
    // =================================================================

    static void subtypePolymorphism() {
        try {
            Object  obj    = "actually a string";
            Number  n      = Integer.valueOf(42);
            CharSequence cs = new StringBuilder("sb");
            check("Object holding String",
                "obj=actually a string", f("obj={obj}"));
            check("Number holding Integer",
                "n=42", f("n={n}"));
            check("CharSequence holding StringBuilder",
                "cs=sb", f("cs={cs}"));
        } catch (Throwable t) { fail("subtype polymorphism", t); }
    }

    // =================================================================
    // Method overloads (same annotated name)
    // =================================================================

    static void overloadResolution() {
        try {
            // Each overload has its own companion. The agent rewrites every
            // matching call site individually based on the resolved
            // signature.
            int n = 5;
            String s = "hello";
            check("overload (int)",    "got int 5",        callerInt(n));
            check("overload (String)", "got string hello", callerString(s));
            check("overload (int,String)",
                "got int+string: 5,hello", callerIntString(n, s));
        } catch (Throwable t) { fail("overloads", t); }
    }

    static String callerInt(int n)               { return Overloaded.go(); }
    static String callerString(String s)         { return Overloaded.go("ignored"); }
    static String callerIntString(int n, String s) { return Overloaded.go(0, "ignored"); }

    public static final class Overloaded {
        @RequiresCallerLocalVariableDetails
        public static String go()                          { throw new UnsupportedOperationException(); }
        @RequiresCallerLocalVariableDetails
        public static String go(String ignored)            { throw new UnsupportedOperationException(); }
        @RequiresCallerLocalVariableDetails
        public static String go(int ignoredInt, String ignoredStr) { throw new UnsupportedOperationException(); }

        public static String $___go_____(Map<String, Object> locals) {
            Object n = locals.get("n");
            return "got int " + n;
        }
        public static String $___go__Ljava_lang_String_2___(
                Map<String, Object> locals, String ignored) {
            return "got string " + locals.get("s");
        }
        public static String $___go__ILjava_lang_String_2___(
                Map<String, Object> locals, int ignoredInt, String ignoredStr) {
            return "got int+string: " + locals.get("n") + "," + locals.get("s");
        }
    }

    // =================================================================
    // Map collision — caller declares a local with the same name as a
    // method parameter that shadows nothing (Java doesn't allow shadowing
    // in inner blocks) — but a parameter and a non-overlapping post-block
    // local can share names. Confirm both resolve correctly.
    // =================================================================

    static void argShadowing() {
        try {
            check("param then post-block local",
                "p=42 q=p outer", argShadowingHelper(42));
        } catch (Throwable t) { fail("arg shadowing", t); }
    }

    private static String argShadowingHelper(int p) {
        // Same name reuse across sibling block then parameter scope is the
        // ScopeResolutionTest's bread and butter; this just adds a
        // parameter into the mix.
        String q = "p outer";
        return f("p={p} q={q}");
    }

    // =================================================================
    // Many locals in one frame
    // =================================================================

    static void manyLocalsAtOnce() {
        try {
            // 20 locals; the agent must allocate that many new slots and
            // build a 20-entry boxed array per call site.
            int v00 = 0,  v01 = 1,  v02 = 2,  v03 = 3,  v04 = 4;
            int v05 = 5,  v06 = 6,  v07 = 7,  v08 = 8,  v09 = 9;
            int v10 = 10, v11 = 11, v12 = 12, v13 = 13, v14 = 14;
            int v15 = 15, v16 = 16, v17 = 17, v18 = 18, v19 = 19;
            String r = f("{v00},{v01},{v02},{v03},{v04},{v05},{v06},{v07},{v08},{v09}," +
                         "{v10},{v11},{v12},{v13},{v14},{v15},{v16},{v17},{v18},{v19}");
            check("20 locals one site",
                "0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19", r);
        } catch (Throwable t) { fail("many locals", t); }
    }

    // =================================================================
    // Stack map after wide stores — exercise the verifier's handling of
    // the agent's slot initializers when long/double slots immediately
    // follow narrow ones.
    // =================================================================

    static void stackMapWideStores() {
        try {
            // The structure here intentionally produces an LVT with a mix
            // of cat-1 and cat-2 entries, slots numbered close together.
            int x = 1;
            long y = 2L;
            int z = 3;
            double w = 4.0;
            int v = 5;
            check("mixed widths",
                "x=1 y=2 z=3 w=4.0 v=5",
                f("x={x} y={y} z={z} w={w} v={v}"));

            // Branching forces stack-map frames at the merge point. All
            // five locals must remain valid through the branch.
            int side;
            if (System.currentTimeMillis() > 0) {
                side = 100;
            } else {
                side = -100;
            }
            check("after branch",
                "x=1 y=2 z=3 w=4.0 v=5 side=100",
                f("x={x} y={y} z={z} w={w} v={v} side={side}"));
        } catch (Throwable t) { fail("stack map wide stores", t); }
    }

    // =================================================================
    // Mini test framework
    // =================================================================

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
        return new HashMap<String, Object>();
    }

    @SuppressWarnings("unused")
    private static List<TreeMap<String, Object>> unused2() {
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private static List<ArrayList<Object>> unused3() {
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private static TemplateException unused4() { return new TemplateException("x"); }
}
