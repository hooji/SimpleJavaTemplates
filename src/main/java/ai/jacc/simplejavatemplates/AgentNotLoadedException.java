package ai.jacc.simplejavatemplates;

public class AgentNotLoadedException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
        "SimpleJavaTemplates agent is not loaded. Add " +
        "-javaagent:SimpleJavaTemplates.jar to your JVM startup.";

    public AgentNotLoadedException() {
        super(DEFAULT_MESSAGE);
    }

    public AgentNotLoadedException(String message) {
        super(message);
    }

    public AgentNotLoadedException(String message, Throwable cause) {
        super(message, cause);
    }
}
