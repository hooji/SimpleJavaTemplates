package ai.jacc.simplejavatemplates.agent;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Performs local variable preservation (slot renumbering) and call-site
 * rewriting for a single method.
 */
final class MethodRewriter {

    private static final String MAP_BUILDER_INTERNAL =
        "ai/jacc/simplejavatemplates/agent/MapBuilder";
    private static final String METHOD_LVD_DESC =
        "Lai/jacc/simplejavatemplates/MethodLocalVariableDetails;";

    private MethodRewriter() {}

    // ========== Logical variable building ==========

    /**
     * Builds the list of all logical variables for a method. Parameters keep
     * their original slots; non-parameters get new unique slots starting at
     * originalMaxLocals to avoid conflicts with unlisted temporaries.
     */
    static List<LogicalVariable> buildAllLogicals(
            List<RawBytecodeScanner.RawLVTEntry> rawLvt,
            String methodDesc,
            boolean isStatic,
            String className,
            int codeLength,
            int originalMaxLocals) {

        int paramSlotCount = computeParamSlotCount(methodDesc, isStatic);
        List<LogicalVariable> logicals = new ArrayList<LogicalVariable>();
        int nextSlot = originalMaxLocals;

        // Synthesize "this" for instance methods
        if (!isStatic) {
            logicals.add(new LogicalVariable("this", "L" + className + ";",
                0, 0, 0, codeLength));
        }

        for (RawBytecodeScanner.RawLVTEntry e : rawLvt) {
            // Skip existing "this" entries (we synthesized our own)
            if (!isStatic && e.slot == 0 && "this".equals(e.name)) {
                continue;
            }

            int newSlot;
            if (e.slot < paramSlotCount) {
                newSlot = e.slot; // Parameter: keep original slot
            } else {
                newSlot = nextSlot;
                boolean cat2 = "J".equals(e.descriptor) || "D".equals(e.descriptor);
                nextSlot += cat2 ? 2 : 1;
            }

            logicals.add(new LogicalVariable(e.name, e.descriptor,
                e.slot, newSlot, e.startPc, e.length));
        }

        return logicals;
    }

    static int computeParamSlotCount(String methodDesc, boolean isStatic) {
        int count = isStatic ? 0 : 1; // 'this' for instance methods
        Type[] paramTypes = Type.getArgumentTypes(methodDesc);
        for (Type t : paramTypes) {
            count += t.getSize();
        }
        return count;
    }

    static int computeNewMaxLocals(List<LogicalVariable> logicals) {
        int max = 0;
        for (LogicalVariable lv : logicals) {
            int end = lv.newSlot + (lv.isCategory2() ? 2 : 1);
            if (end > max) max = end;
        }
        return max;
    }

    // ========== Slot renumbering ==========

    /**
     * Retargets every load/store/iinc instruction to use the logical variable's
     * new slot, determined by matching (oldSlot, originalPc) against the logicals.
     */
    static void applySlotRenumbering(InsnList insns,
                                     Map<AbstractInsnNode, Integer> nodeToPc,
                                     List<LogicalVariable> logicals) {
        for (AbstractInsnNode node = insns.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof VarInsnNode) {
                VarInsnNode vin = (VarInsnNode) node;
                Integer pc = nodeToPc.get(node);
                if (pc != null) {
                    boolean isStore = isStoreOpcode(vin.getOpcode());
                    LogicalVariable lv = findLogical(logicals, vin.var, pc, isStore);
                    if (lv != null) {
                        vin.var = lv.newSlot;
                    }
                }
            } else if (node instanceof IincInsnNode) {
                IincInsnNode iinc = (IincInsnNode) node;
                Integer pc = nodeToPc.get(node);
                if (pc != null) {
                    // IINC is both a read and write; treat as store for matching
                    LogicalVariable lv = findLogical(logicals, iinc.var, pc, true);
                    if (lv != null) {
                        iinc.var = lv.newSlot;
                    }
                }
            }
        }
    }

    private static boolean isStoreOpcode(int opcode) {
        return opcode == Opcodes.ISTORE || opcode == Opcodes.LSTORE
            || opcode == Opcodes.FSTORE || opcode == Opcodes.DSTORE
            || opcode == Opcodes.ASTORE;
    }

    /**
     * Finds the logical variable that owns the given slot at the given original PC.
     * For store instructions, also matches the "initialization store" that javac
     * places just before the LVT range start (the LVT start_pc is typically the
     * instruction AFTER the initial store).
     */
    private static LogicalVariable findLogical(List<LogicalVariable> logicals,
                                               int slot, int pc, boolean isStore) {
        // Exact range match: pc in [originalStartPc, originalStartPc + originalLength)
        for (LogicalVariable lv : logicals) {
            if (lv.originalSlot == slot
                    && pc >= lv.originalStartPc
                    && pc < lv.originalStartPc + lv.originalLength) {
                return lv;
            }
        }

        // For stores: the initialization store sits just before the LVT range.
        // Match the logical whose range starts closest after this PC (within 4 bytes,
        // which covers astore_N(1), astore(2), wide astore(4)).
        if (isStore) {
            LogicalVariable best = null;
            for (LogicalVariable lv : logicals) {
                if (lv.originalSlot == slot
                        && lv.originalStartPc > pc
                        && lv.originalStartPc <= pc + 4) {
                    if (best == null || lv.originalStartPc < best.originalStartPc) {
                        best = lv;
                    }
                }
            }
            return best;
        }

        return null;
    }

    // ========== Call-site rewriting ==========

    /**
     * Rewrites a single annotated call site: saves existing arguments to temp slots,
     * builds the map via MapBuilder.buildMap, reloads the arguments, and changes
     * the invoke target to the synthetic implementation.
     */
    static void rewriteCallSite(InsnList insns, MethodInsnNode callNode,
                                int originalPc, List<LogicalVariable> metadataLogicals,
                                String className, String fieldName, int tempSlotBase) {

        Type[] stubParams = Type.getArgumentTypes(callNode.desc);

        InsnList pre = new InsnList();

        // 1. Save stub arguments to temp slots (popped in reverse stack order)
        int tempOff = 0;
        int[] argTempSlots = new int[stubParams.length];
        for (int i = 0; i < stubParams.length; i++) {
            argTempSlots[i] = tempSlotBase + tempOff;
            tempOff += stubParams[i].getSize();
        }
        for (int i = stubParams.length - 1; i >= 0; i--) {
            pre.add(new VarInsnNode(stubParams[i].getOpcode(Opcodes.ISTORE), argTempSlots[i]));
        }

        // 2. Load metadata field
        pre.add(new FieldInsnNode(Opcodes.GETSTATIC, className, fieldName, METHOD_LVD_DESC));

        // 3. Push original PC constant
        pre.add(pushInt(originalPc));

        // 4. Create and populate Object[] for boxed slot values
        pre.add(pushInt(metadataLogicals.size()));
        pre.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

        for (int i = 0; i < metadataLogicals.size(); i++) {
            LogicalVariable lv = metadataLogicals.get(i);
            pre.add(new InsnNode(Opcodes.DUP));
            pre.add(pushInt(i));
            emitLoadAndBox(pre, lv);
            pre.add(new InsnNode(Opcodes.AASTORE));
        }

        // 5. Invoke MapBuilder.buildMap
        pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MAP_BUILDER_INTERNAL, "buildMap",
            "(Lai/jacc/simplejavatemplates/MethodLocalVariableDetails;I[Ljava/lang/Object;)" +
            "Ljava/util/Map;", false));

        // 6. Reload stub arguments from temp slots
        for (int i = 0; i < stubParams.length; i++) {
            pre.add(new VarInsnNode(stubParams[i].getOpcode(Opcodes.ILOAD), argTempSlots[i]));
        }

        // Insert all pre-instructions before the call
        insns.insertBefore(callNode, pre);

        // 7. Retarget the call to the synthetic implementation
        String syntheticName = computeSyntheticName(callNode.name, callNode.desc);
        String syntheticDesc = computeSyntheticDesc(callNode.desc);
        callNode.name = syntheticName;
        callNode.desc = syntheticDesc;
    }

    /**
     * Replaces an annotated call site whose companion ($___name__params___) is
     * missing with code that pops the existing arguments and throws a
     * {@link ai.jacc.simplejavatemplates.TemplateException} carrying a
     * diagnostic that names the exact companion signature the user must add.
     */
    static void rewriteCallSiteAsMissingCompanion(InsnList insns,
                                                  MethodInsnNode callNode,
                                                  String message) {
        Type[] stubParams = Type.getArgumentTypes(callNode.desc);
        InsnList replacement = new InsnList();

        // Pop the already-pushed stub arguments (rightmost-first to match stack order).
        for (int i = stubParams.length - 1; i >= 0; i--) {
            replacement.add(new InsnNode(
                stubParams[i].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        }

        replacement.add(new TypeInsnNode(Opcodes.NEW,
            "ai/jacc/simplejavatemplates/TemplateException"));
        replacement.add(new InsnNode(Opcodes.DUP));
        replacement.add(new LdcInsnNode(message));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
            "ai/jacc/simplejavatemplates/TemplateException",
            "<init>", "(Ljava/lang/String;)V", false));
        replacement.add(new InsnNode(Opcodes.ATHROW));

        insns.insertBefore(callNode, replacement);
        insns.remove(callNode);
    }

    // ========== Slot initialization for verifier ==========

    /**
     * Emits default-value stores for all non-parameter logicals at the method start.
     * After slot renumbering, each logical owns a unique slot for the entire method,
     * but COMPUTE_FRAMES types a slot as 'top' until its first store. Since call-site
     * rewriting may read any logical's slot (the helper filters by scope at runtime),
     * we must ensure every slot has a valid type from the beginning.
     */
    static void emitSlotInitializers(MethodNode mn, List<LogicalVariable> allLogicals,
                                     int paramSlotCount) {
        InsnList init = new InsnList();
        for (LogicalVariable lv : allLogicals) {
            if (lv.newSlot < paramSlotCount) continue; // parameters already initialized
            char tc = lv.descriptor.charAt(0);
            switch (tc) {
                case 'Z': case 'B': case 'C': case 'S': case 'I':
                    init.add(new InsnNode(Opcodes.ICONST_0));
                    init.add(new VarInsnNode(Opcodes.ISTORE, lv.newSlot));
                    break;
                case 'J':
                    init.add(new InsnNode(Opcodes.LCONST_0));
                    init.add(new VarInsnNode(Opcodes.LSTORE, lv.newSlot));
                    break;
                case 'F':
                    init.add(new InsnNode(Opcodes.FCONST_0));
                    init.add(new VarInsnNode(Opcodes.FSTORE, lv.newSlot));
                    break;
                case 'D':
                    init.add(new InsnNode(Opcodes.DCONST_0));
                    init.add(new VarInsnNode(Opcodes.DSTORE, lv.newSlot));
                    break;
                case 'L': case '[':
                    init.add(new InsnNode(Opcodes.ACONST_NULL));
                    init.add(new VarInsnNode(Opcodes.ASTORE, lv.newSlot));
                    break;
            }
        }
        if (init.size() > 0) {
            mn.instructions.insert(init);
        }
    }

    // ========== LVT update for verifier ==========

    /**
     * Replaces the method's LocalVariableTable with extended-liveness entries
     * covering the entire method body on the new slots.
     */
    static void updateLocalVariablesForVerifier(MethodNode mn, List<LogicalVariable> allLogicals) {
        LabelNode startLabel = new LabelNode();
        LabelNode endLabel = new LabelNode();
        mn.instructions.insert(startLabel);
        mn.instructions.add(endLabel);

        mn.localVariables = new ArrayList<LocalVariableNode>();
        for (LogicalVariable lv : allLogicals) {
            mn.localVariables.add(new LocalVariableNode(
                lv.name, lv.descriptor, null, startLabel, endLabel, lv.newSlot));
        }
    }

    // ========== Naming conventions ==========

    static String computeSyntheticName(String stubName, String stubDesc) {
        int closeParen = stubDesc.indexOf(')');
        String paramPart = stubDesc.substring(1, closeParen);
        String encoded = encodeDescriptorForName(paramPart);
        return "$___" + stubName + "__" + encoded + "___";
    }

    static String computeSyntheticDesc(String stubDesc) {
        return "(Ljava/util/Map;" + stubDesc.substring(1);
    }

    static String computeFieldName(String methodName, String methodDesc) {
        Type returnType = Type.getReturnType(methodDesc);
        String encoded = encodeDescriptorForName(returnType.getDescriptor());
        return "$___localVariableDetails___" + encoded + "___" + methodName + "___";
    }

    /**
     * Encodes a JVM descriptor so it is legal in a field/method name.
     * JVMS §4.2.2 forbids . ; [ / in unqualified names.
     */
    static String encodeDescriptorForName(String desc) {
        return desc.replace('/', '_').replace(";", "_2").replace("[", "_3");
    }

    // ========== Bytecode emission helpers ==========

    static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, value);
        } else {
            return new LdcInsnNode(value);
        }
    }

    private static void emitLoadAndBox(InsnList insns, LogicalVariable lv) {
        int slot = lv.newSlot;
        char typeChar = lv.descriptor.charAt(0);
        switch (typeChar) {
            case 'Z':
                insns.add(new VarInsnNode(Opcodes.ILOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
                break;
            case 'B':
                insns.add(new VarInsnNode(Opcodes.ILOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
                break;
            case 'C':
                insns.add(new VarInsnNode(Opcodes.ILOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
                break;
            case 'S':
                insns.add(new VarInsnNode(Opcodes.ILOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
                break;
            case 'I':
                insns.add(new VarInsnNode(Opcodes.ILOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
                break;
            case 'J':
                insns.add(new VarInsnNode(Opcodes.LLOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
                break;
            case 'F':
                insns.add(new VarInsnNode(Opcodes.FLOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
                break;
            case 'D':
                insns.add(new VarInsnNode(Opcodes.DLOAD, slot));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
                break;
            case 'L':
            case '[':
                insns.add(new VarInsnNode(Opcodes.ALOAD, slot));
                break;
            default:
                throw new IllegalStateException("Unknown descriptor: " + lv.descriptor);
        }
    }
}
