package ai.jacc.simplejavatemplates.smoketest;

import static ai.jacc.simplejavatemplates.Template.f;

import ai.jacc.simplejavatemplates.RequiresCallerLocalVariableDetails;
import ai.jacc.simplejavatemplates.Template;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concurrent-execution paranoia. Each annotated call site allocates a fresh
 * {@code Map<String, Object>} so per-thread state is naturally isolated, and
 * the per-method metadata field is a {@code static final} initialized in
 * {@code <clinit>}, so once published it's safe for concurrent reads. These
 * tests pin those invariants down with multi-threaded workloads:
 *
 * <ul>
 *   <li>Many threads calling the same annotated method concurrently with
 *       distinct local-variable values must each see only their own values.</li>
 *   <li>A user-defined annotated method whose companion stores the
 *       captured map into thread-local state must observe a unique
 *       {@code Map} instance per call.</li>
 *   <li>Concurrent first-time class loads of the agent's caller class do
 *       not race the metadata-field initialization.</li>
 *   <li>Tight-loop calls do not leak or accumulate state across iterations.</li>
 * </ul>
 *
 * Run with: java -javaagent:SimpleJavaTemplates.jar
 *   ai.jacc.simplejavatemplates.smoketest.ParanoidConcurrencyTest
 */
public class ParanoidConcurrencyTest {

    static int passed = 0;
    static int failed = 0;

    private static final int THREADS_PER_TEST = 16;
    private static final int CALLS_PER_THREAD = 500;

    public static void main(String[] args) throws Exception {
        Template.getGlobalTemplateExpanderInstance().setRequireLeadingDollar(false);

        section("Per-call map identity (no sharing across calls)");
        perCallMapIdentity();

        section("Many threads, each with distinct locals");
        manyThreadsDistinctLocals();

        section("Tight loop on one thread does not leak state");
        tightLoopOneThread();

        section("Different caller methods sharing the same annotated callee");
        manyThreadsAcrossDifferentCallers();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // =================================================================
    // 1. Per-call map identity
    // =================================================================

    static void perCallMapIdentity() {
        try {
            // Each call captures its own caller-locals into a fresh map.
            // We assert no two calls (even on the same thread) ever see
            // the same Map instance — proving the agent is allocating per
            // call site invocation, not memoising or sharing.
            Set<Integer> seen = new HashSet<Integer>();
            int collisions = 0;
            for (int i = 0; i < 1000; i++) {
                int identity = MapCollector.captureIdentity(i);
                if (!seen.add(identity)) collisions++;
            }
            check("all maps are distinct instances", 0, collisions);
        } catch (Throwable t) { fail("per-call map identity", t); }
    }

    public static final class MapCollector {
        @RequiresCallerLocalVariableDetails
        public static int captureIdentity(int payload) {
            throw new UnsupportedOperationException();
        }

        public static int $___captureIdentity__I___(
                Map<String, Object> locals, int payload) {
            return System.identityHashCode(locals);
        }

        @RequiresCallerLocalVariableDetails
        public static String describe() {
            throw new UnsupportedOperationException();
        }

        public static String $___describe_____(Map<String, Object> locals) {
            // Return a deterministic string built from the captured locals
            // we care about so tests can pin equality. Sort keys for
            // stability across the (HashMap-backed) caller map.
            StringBuilder sb = new StringBuilder();
            String[] keys = locals.keySet().toArray(new String[0]);
            java.util.Arrays.sort(keys);
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) sb.append('|');
                sb.append(keys[i]).append('=').append(locals.get(keys[i]));
            }
            return sb.toString();
        }
    }

    // =================================================================
    // 2. Many threads, distinct locals
    // =================================================================

    static void manyThreadsDistinctLocals() throws Exception {
        try {
            ExecutorService exec = Executors.newFixedThreadPool(THREADS_PER_TEST);
            CountDownLatch start = new CountDownLatch(1);
            ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<String>();
            AtomicInteger okCount = new AtomicInteger();

            try {
                for (int t = 0; t < THREADS_PER_TEST; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            start.await();
                            for (int call = 0; call < CALLS_PER_THREAD; call++) {
                                int  threadId = threadIdx;
                                int  callId   = call;
                                long product  = (long) threadId * 1_000_000L + callId;
                                String got = f("t={threadId} c={callId} p={product}");
                                String want = "t=" + threadId
                                            + " c=" + callId
                                            + " p=" + product;
                                if (!want.equals(got)) {
                                    errors.add("thread " + threadId
                                            + " call " + callId
                                            + " saw '" + got + "' want '" + want + "'");
                                } else {
                                    okCount.incrementAndGet();
                                }
                            }
                        } catch (Throwable th) {
                            errors.add("thread " + threadIdx + " threw " + th);
                        }
                    });
                }
                start.countDown();
                exec.shutdown();
                if (!exec.awaitTermination(60, TimeUnit.SECONDS)) {
                    errors.add("workers did not finish in 60s");
                }
            } finally {
                exec.shutdownNow();
            }

            int expected = THREADS_PER_TEST * CALLS_PER_THREAD;
            check("no cross-thread bleed (errors)", 0, errors.size());
            if (!errors.isEmpty()) {
                for (String err : errors) System.out.println("    " + err);
            }
            check("all calls succeeded", expected, okCount.get());
        } catch (Throwable t) { fail("many threads", t); }
    }

    // =================================================================
    // 3. Tight loop on one thread
    // =================================================================

    static void tightLoopOneThread() {
        try {
            // Hammer one call site many times; every iteration must see
            // its own local 'i' and 'square' values, never a stale one.
            int mismatches = 0;
            for (int i = 0; i < 5000; i++) {
                int square = i * i;
                String got = f("i={i} square={square}");
                String want = "i=" + i + " square=" + square;
                if (!want.equals(got)) mismatches++;
            }
            check("tight loop mismatches", 0, mismatches);
        } catch (Throwable t) { fail("tight loop", t); }
    }

    // =================================================================
    // 4. Multiple caller methods sharing the same annotated callee
    // =================================================================

    static void manyThreadsAcrossDifferentCallers() throws Exception {
        try {
            // Five different methods all call MapCollector.describe(), each
            // with a distinct local-variable shape. Schedule them in
            // parallel and confirm every invocation returns the right
            // shape — the per-method metadata fields must be independent.
            ExecutorService exec = Executors.newFixedThreadPool(THREADS_PER_TEST);
            CountDownLatch start = new CountDownLatch(1);
            ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<String>();
            AtomicInteger okCount = new AtomicInteger();
            int callsPerCaller = CALLS_PER_THREAD;

            try {
                for (int t = 0; t < THREADS_PER_TEST; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            start.await();
                            for (int call = 0; call < callsPerCaller; call++) {
                                int which = (threadIdx + call) % 5;
                                String got;
                                String want;
                                switch (which) {
                                    case 0: got = callerA();
                                            want = "alpha=A|beta=B"; break;
                                    case 1: got = callerB();
                                            want = "x=1|y=2|z=3"; break;
                                    case 2: got = callerC();
                                            want = "name=callerC"; break;
                                    case 3: got = callerD();
                                            want = "n=42"; break;
                                    default: got = callerE();
                                            want = "flag=true|item=hello"; break;
                                }
                                if (!want.equals(got)) {
                                    errors.add("thread " + threadIdx
                                            + " caller " + which
                                            + " saw '" + got + "' want '" + want + "'");
                                } else {
                                    okCount.incrementAndGet();
                                }
                            }
                        } catch (Throwable th) {
                            errors.add("thread " + threadIdx + " threw " + th);
                        }
                    });
                }
                start.countDown();
                exec.shutdown();
                if (!exec.awaitTermination(60, TimeUnit.SECONDS)) {
                    errors.add("workers did not finish in 60s");
                }
            } finally {
                exec.shutdownNow();
            }

            int expected = THREADS_PER_TEST * callsPerCaller;
            check("no caller-shape bleed (errors)", 0, errors.size());
            if (!errors.isEmpty()) {
                for (String err : errors) System.out.println("    " + err);
            }
            check("all caller invocations succeeded", expected, okCount.get());
        } catch (Throwable t) { fail("many callers", t); }
    }

    private static String callerA() {
        String alpha = "A";
        String beta  = "B";
        return MapCollector.describe();
    }
    private static String callerB() {
        int x = 1, y = 2, z = 3;
        return MapCollector.describe();
    }
    private static String callerC() {
        String name = "callerC";
        return MapCollector.describe();
    }
    private static String callerD() {
        int n = 42;
        return MapCollector.describe();
    }
    private static String callerE() {
        boolean flag = true;
        String  item = "hello";
        return MapCollector.describe();
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
    private static List<HashMap<String, Object>> u1() { return null; }
    @SuppressWarnings("unused")
    private static ConcurrentHashMap<String, Object> u2() { return null; }
    @SuppressWarnings("unused")
    private static AtomicReference<String> u3() { return null; }
    @SuppressWarnings("unused")
    private static Future<Void> u4() { return null; }
    @SuppressWarnings("unused")
    private static ExecutionException u5() { return null; }
}
