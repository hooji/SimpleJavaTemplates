package ai.jacc.simplejavatemplates.agent;

/**
 * Represents one source-level local variable after slot renumbering.
 * Each original LVT entry becomes one LogicalVariable with its own permanent slot.
 */
final class LogicalVariable {
    final String name;
    final String descriptor;
    final int originalSlot;
    int newSlot;
    final int originalStartPc;
    final int originalLength;

    LogicalVariable(String name, String descriptor, int originalSlot, int newSlot,
                    int originalStartPc, int originalLength) {
        this.name = name;
        this.descriptor = descriptor;
        this.originalSlot = originalSlot;
        this.newSlot = newSlot;
        this.originalStartPc = originalStartPc;
        this.originalLength = originalLength;
    }

    boolean isCategory2() {
        return "J".equals(descriptor) || "D".equals(descriptor);
    }
}
