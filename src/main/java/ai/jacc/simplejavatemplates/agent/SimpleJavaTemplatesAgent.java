package ai.jacc.simplejavatemplates.agent;

import java.lang.instrument.Instrumentation;

public class SimpleJavaTemplatesAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
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
