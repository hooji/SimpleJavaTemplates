package ai.jacc.simplejavatemplates;

public final class MethodLocalVariableDetails {

    private final LocalVariableDetails[] localVariableDetails;

    public MethodLocalVariableDetails(LocalVariableDetails[] localVariableDetails) {
        this.localVariableDetails = localVariableDetails;
    }

    public LocalVariableDetails[] localVariableDetails() {
        return localVariableDetails;
    }
}
