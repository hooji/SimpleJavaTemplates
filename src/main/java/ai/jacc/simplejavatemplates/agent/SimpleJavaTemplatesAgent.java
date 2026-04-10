package ai.jacc.simplejavatemplates.agent;

import java.lang.instrument.Instrumentation;

public class SimpleJavaTemplatesAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        // Pre-register the built-in Template.f() annotated method so that
        // caller classes loaded before Template.class are still transformed.
        CallerLocalVariableTransformer.annotatedMethods.add(
            "ai/jacc/simplejavatemplates/Template.f.(Ljava/lang/String;)Ljava/lang/String;");
        inst.addTransformer(new CallerLocalVariableTransformer(), false);
    }
}
