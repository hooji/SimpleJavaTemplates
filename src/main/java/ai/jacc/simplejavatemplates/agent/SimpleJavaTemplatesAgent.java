package ai.jacc.simplejavatemplates.agent;

import java.lang.instrument.Instrumentation;

public class SimpleJavaTemplatesAgent {

    /**
     * Set to {@code true} the first time {@link #premain(String, Instrumentation)}
     * runs. Other agents (for example DurableJavaThreads, which can auto-chain
     * this agent when it finds SimpleJavaTemplates on the classpath) can read
     * this flag reflectively to avoid double-registering the transformer.
     *
     * <p>Volatile because {@code premain} may be entered from more than one
     * thread across agents, even though in practice it runs on the main
     * thread during JVM startup.</p>
     */
    public static volatile boolean loaded = false;

    public static void premain(String agentArgs, Instrumentation inst) {
        // Guard against double-invocation (e.g. user passes -javaagent:SJT
        // AND another agent reflectively calls this premain). Registering the
        // transformer twice would produce two independent transformer
        // instances and have unpredictable effects.
        if (loaded) {
            return;
        }
        loaded = true;

        inst.addTransformer(new CallerLocalVariableTransformer(), false);
        // Eagerly load Template so the transformer discovers all
        // @RequiresCallerLocalVariableDetails methods before any user
        // class is loaded.  This replaces manual pre-registration and
        // automatically covers any future annotated methods.
        try {
            Class.forName("ai.jacc.simplejavatemplates.Template");
        } catch (ClassNotFoundException ignore) {
            // Template is in the agent jar so this should not happen, but
            // fall back to manual registration just in case.
            CallerLocalVariableTransformer.annotatedMethods.add(
                "ai/jacc/simplejavatemplates/Template.f.(Ljava/lang/String;)Ljava/lang/String;");
        }
    }
}
