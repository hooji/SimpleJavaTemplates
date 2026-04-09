package ai.jacc.simplejavatemplates;

public final class LocalVariableDetails {

    private final String name;
    private final int slotNumber;
    private final int originalStartPc;
    private final int originalLength;

    public LocalVariableDetails(String name, int slotNumber,
                                int originalStartPc, int originalLength) {
        this.name = name;
        this.slotNumber = slotNumber;
        this.originalStartPc = originalStartPc;
        this.originalLength = originalLength;
    }

    public String name() {
        return name;
    }

    public int slotNumber() {
        return slotNumber;
    }

    public int originalStartPc() {
        return originalStartPc;
    }

    public int originalLength() {
        return originalLength;
    }
}
