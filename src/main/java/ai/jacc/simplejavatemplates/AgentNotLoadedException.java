package ai.jacc.simplejavatemplates;

public class AgentNotLoadedException extends RuntimeException {

    public AgentNotLoadedException(String message) {
        super(message);
    }

    public AgentNotLoadedException(String message, Throwable cause) {
        super(message, cause);
    }
}
