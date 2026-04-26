package ai.jacc.simplejavatemplates.agent;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * Transforms caller classes so that calls to methods annotated with
 * {@code @RequiresCallerLocalVariableDetails} are rewritten to pass
 * a map of the caller's local variables via the MapBuilder helper.
 *
 * <p>Strategy (B) from the spec: original PCs are captured once before any
 * rewriting and baked into each call site as constants. Metadata ranges
 * are never adjusted for PC shifts.</p>
 */
public class CallerLocalVariableTransformer implements ClassFileTransformer {

    private static final String ANNOTATION_DESC =
        "Lai/jacc/simplejavatemplates/RequiresCallerLocalVariableDetails;";
    private static final String METHOD_LVD_INTERNAL =
        "ai/jacc/simplejavatemplates/MethodLocalVariableDetails";
    private static final String METHOD_LVD_DESC =
        "Lai/jacc/simplejavatemplates/MethodLocalVariableDetails;";
    private static final String LOCAL_VD_INTERNAL =
        "ai/jacc/simplejavatemplates/LocalVariableDetails";

    /** Cache of methods known to carry @RequiresCallerLocalVariableDetails. */
    static final Set<String> annotatedMethods =
        Collections.synchronizedSet(new HashSet<String>());

    /**
     * Maps an annotated method (key: {@code owner.name.desc}) to a
     * pre-built diagnostic message describing the companion implementation
     * the user must add. Populated when an annotated method is registered
     * but its companion ($___name__params___) is absent from the same class.
     */
    static final Map<String, String> missingCompanionMessages =
        Collections.synchronizedMap(new HashMap<String, String>());

    /**
     * Internal names of classes whose annotations have already been registered.
     * Guards the load-order race where a caller is transformed before the
     * callee's class has been loaded: without this, the caller's call site
     * to an annotated method would not be recognized, leaving the original
     * stub body to throw {@link ai.jacc.simplejavatemplates.AgentNotLoadedException}.
     */
    static final Set<String> scannedClasses =
        Collections.synchronizedSet(new HashSet<String>());

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer)
            throws IllegalClassFormatException {

        if (className == null) return null;
        // Skip agent internals and JDK classes
        if (className.startsWith("ai/jacc/simplejavatemplates/agent/")) return null;
        if (className.startsWith("java/") || className.startsWith("javax/") ||
            className.startsWith("jdk/") || className.startsWith("sun/") ||
            className.startsWith("com/sun/")) return null;

        try {
            // Always register any annotated methods declared in this class
            registerAnnotatedMethods(classfileBuffer);
            scannedClasses.add(className);

            ClassReader cr = new ClassReader(classfileBuffer);

            // Pre-scan referenced classes so their @RequiresCallerLocalVariableDetails
            // annotations are registered before we look for call sites here. Without
            // this, a caller loaded earlier than its callee would not have its call
            // site rewritten — the JVM would later invoke the original stub body and
            // throw AgentNotLoadedException.
            preregisterReferencedClasses(cr, loader);

            if (annotatedMethods.isEmpty()) return null;

            // Quick scan for annotated calls in this class
            Map<String, List<String[]>> methodsWithCalls = quickScan(cr);

            if (methodsWithCalls.isEmpty()) return null;

            return doFullTransform(cr, className, methodsWithCalls);
        } catch (Exception e) {
            System.err.println("[SimpleJavaTemplates] ERROR transforming " +
                className.replace('/', '.') + ": " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    // ========== Phase 1: annotation registration ==========

    static void registerAnnotatedMethods(byte[] classfileBuffer) {
        final String[] ownerHolder = new String[1];
        final Set<String> declaredMethods = new HashSet<String>();
        final List<String[]> annotatedHere = new ArrayList<String[]>();

        ClassReader cr = new ClassReader(classfileBuffer);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name,
                              String signature, String superName, String[] interfaces) {
                ownerHolder[0] = name;
            }

            @Override
            public MethodVisitor visitMethod(int access, final String name, final String desc,
                                             String signature, String[] exceptions) {
                declaredMethods.add(name + desc);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String adesc, boolean visible) {
                        if (ANNOTATION_DESC.equals(adesc)) {
                            annotatedHere.add(new String[]{name, desc});
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        String owner = ownerHolder[0];
        if (owner == null) return;

        for (String[] m : annotatedHere) {
            String name = m[0], desc = m[1];
            String key = owner + "." + name + "." + desc;
            annotatedMethods.add(key);

            String companionName = MethodRewriter.computeSyntheticName(name, desc);
            String companionDesc = MethodRewriter.computeSyntheticDesc(desc);
            if (declaredMethods.contains(companionName + companionDesc)) {
                // Companion present — clear any prior "missing" record.
                missingCompanionMessages.remove(key);
            } else {
                missingCompanionMessages.put(key,
                    buildMissingCompanionMessage(owner, name, desc, companionName));
            }
        }
    }

    private static String buildMissingCompanionMessage(String ownerInternal, String name,
                                                       String desc, String companionName) {
        String ownerDot = ownerInternal.replace('/', '.');
        Type[] params = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);

        StringBuilder origSig = new StringBuilder();
        origSig.append(ret.getClassName()).append(' ')
               .append(ownerDot).append('.').append(name).append('(');
        for (int i = 0; i < params.length; i++) {
            if (i > 0) origSig.append(", ");
            origSig.append(params[i].getClassName());
        }
        origSig.append(')');

        StringBuilder companionSig = new StringBuilder();
        companionSig.append("public static ").append(ret.getClassName()).append(' ')
                    .append(companionName).append("(\n")
                    .append("        java.util.Map<String, Object> localVariableValues");
        for (int i = 0; i < params.length; i++) {
            companionSig.append(",\n        ").append(params[i].getClassName())
                        .append(" arg").append(i);
        }
        companionSig.append(") {\n    // your implementation\n}");

        return "@RequiresCallerLocalVariableDetails method '" + origSig
            + "' is missing its companion implementation. Add this method to "
            + ownerDot + ":\n\n" + companionSig + "\n\n"
            + "The companion receives a Map of the caller's local variables (keyed by name),"
            + " followed by the original method arguments.";
    }

    // ========== Phase 1b: pre-register referenced class annotations ==========

    /**
     * Walks every method invocation in {@code cr} and, for each distinct
     * owner class not yet scanned, fetches its bytes via the supplied
     * {@link ClassLoader} and runs {@link #registerAnnotatedMethods} on them.
     * This guarantees that, by the time the quick scan looks for annotated
     * call sites, any callee declared in a separate (not-yet-loaded) class
     * has already had its annotations registered.
     */
    private void preregisterReferencedClasses(ClassReader cr, ClassLoader loader) {
        final Set<String> referencedOwners = new HashSet<String>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String desc, boolean itf) {
                        if (owner != null && owner.length() > 0
                                && owner.charAt(0) != '[') {
                            referencedOwners.add(owner);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        ClassLoader cl = loader != null ? loader : ClassLoader.getSystemClassLoader();
        if (cl == null) return;
        for (String owner : referencedOwners) {
            if (owner.startsWith("java/") || owner.startsWith("javax/")
                    || owner.startsWith("jdk/") || owner.startsWith("sun/")
                    || owner.startsWith("com/sun/")
                    || owner.startsWith("ai/jacc/simplejavatemplates/agent/")) {
                continue;
            }
            if (!scannedClasses.add(owner)) continue;
            byte[] bytes = readClassBytes(cl, owner);
            if (bytes != null) {
                registerAnnotatedMethods(bytes);
            }
        }
    }

    private static byte[] readClassBytes(ClassLoader cl, String internalName) {
        InputStream is = cl.getResourceAsStream(internalName + ".class");
        if (is == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }

    // ========== Phase 2: quick scan for annotated calls ==========

    private Map<String, List<String[]>> quickScan(ClassReader cr) {
        final Map<String, List<String[]>> result = new HashMap<String, List<String[]>>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, final String mName, final String mDesc,
                                             String signature, String[] exceptions) {
                final String key = mName + mDesc;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String desc, boolean itf) {
                        if (annotatedMethods.contains(owner + "." + name + "." + desc)) {
                            List<String[]> list = result.get(key);
                            if (list == null) {
                                list = new ArrayList<String[]>();
                                result.put(key, list);
                            }
                            list.add(new String[]{owner, name, desc});
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }

    // ========== Phase 3: full transformation ==========

    private byte[] doFullTransform(ClassReader cr, String className,
                                   Map<String, List<String[]>> methodsWithCalls) {

        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        List<MetadataFieldInfo> metadataFields = new ArrayList<MetadataFieldInfo>();
        boolean anyRewritten = false;

        for (MethodNode mn : cn.methods) {
            String methodKey = mn.name + mn.desc;
            if (!methodsWithCalls.containsKey(methodKey)) continue;

            // Check for LocalVariableTable
            if (mn.localVariables == null || mn.localVariables.isEmpty()) {
                System.err.println("[SimpleJavaTemplates] WARNING: No LocalVariableTable for " +
                    className.replace('/', '.') + "." + mn.name +
                    ". Compile with -g or -g:vars. Skipping transformation of this method.");
                continue;
            }

            boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;

            // --- Step 1: Raw bytecode analysis (original PCs, LVT) ---
            RawBytecodeScanner.MethodCodeInfo rawCode =
                RawBytecodeScanner.findMethodRawCode(cr, mn.name, mn.desc);
            if (rawCode == null) {
                System.err.println("[SimpleJavaTemplates] WARNING: No Code attribute for " +
                    className.replace('/', '.') + "." + mn.name + ". Skipping.");
                continue;
            }

            int[] instrPcs = RawBytecodeScanner.computeInstructionOffsets(
                cr.b, rawCode.codeStart, rawCode.codeLength);

            // --- Step 2: Correlate InsnList nodes ↔ original PCs ---
            Map<AbstractInsnNode, Integer> nodeToPc =
                new IdentityHashMap<AbstractInsnNode, Integer>();
            int rawIdx = 0;
            for (AbstractInsnNode node = mn.instructions.getFirst();
                 node != null; node = node.getNext()) {
                if (node instanceof LabelNode || node instanceof LineNumberNode
                        || node instanceof FrameNode) {
                    continue;
                }
                if (rawIdx < instrPcs.length) {
                    nodeToPc.put(node, instrPcs[rawIdx]);
                    rawIdx++;
                }
            }

            // --- Step 3: Build logical variables ---
            List<LogicalVariable> allLogicals = MethodRewriter.buildAllLogicals(
                rawCode.lvtEntries, mn.desc, isStatic, className,
                rawCode.codeLength, rawCode.maxLocals);

            List<LogicalVariable> metadataLogicals = new ArrayList<LogicalVariable>();
            for (LogicalVariable lv : allLogicals) {
                if (!lv.name.contains("$")) {
                    metadataLogicals.add(lv);
                }
            }

            // --- Step 4: Record original PCs for annotated call sites BEFORE rewriting ---
            List<CallSiteInfo> callSites = new ArrayList<CallSiteInfo>();
            for (AbstractInsnNode node = mn.instructions.getFirst();
                 node != null; node = node.getNext()) {
                if (node instanceof MethodInsnNode) {
                    MethodInsnNode min = (MethodInsnNode) node;
                    String callKey = min.owner + "." + min.name + "." + min.desc;
                    if (annotatedMethods.contains(callKey)) {
                        Integer pc = nodeToPc.get(node);
                        if (pc != null) {
                            callSites.add(new CallSiteInfo(min, pc));
                        }
                    }
                }
            }

            // --- Step 5: Apply slot renumbering ---
            MethodRewriter.applySlotRenumbering(mn.instructions, nodeToPc, allLogicals);

            // --- Step 5b: Emit default-value stores for non-parameter logicals ---
            int paramSlotCount = MethodRewriter.computeParamSlotCount(mn.desc, isStatic);
            MethodRewriter.emitSlotInitializers(mn, allLogicals, paramSlotCount);

            // --- Step 6: Compute new maxLocals and temp slot base ---
            int newMaxLocals = MethodRewriter.computeNewMaxLocals(allLogicals);
            int maxTempSlots = 0;
            for (CallSiteInfo cs : callSites) {
                Type[] params = Type.getArgumentTypes(cs.node.desc);
                int needed = 0;
                for (Type p : params) needed += p.getSize();
                if (needed > maxTempSlots) maxTempSlots = needed;
            }
            int tempSlotBase = newMaxLocals;

            // --- Step 7: Rewrite each call site (uses original PCs captured in step 4) ---
            String fieldName = MethodRewriter.computeFieldName(mn.name, mn.desc);
            for (CallSiteInfo cs : callSites) {
                String calleeKey = cs.node.owner + "." + cs.node.name + "." + cs.node.desc;
                String missingMsg = missingCompanionMessages.get(calleeKey);
                if (missingMsg != null) {
                    MethodRewriter.rewriteCallSiteAsMissingCompanion(
                        mn.instructions, cs.node, missingMsg);
                } else {
                    MethodRewriter.rewriteCallSite(mn.instructions, cs.node, cs.originalPc,
                        metadataLogicals, className, fieldName, tempSlotBase);
                }
            }

            // --- Step 8: Update method metadata ---
            mn.maxLocals = newMaxLocals + maxTempSlots;
            MethodRewriter.updateLocalVariablesForVerifier(mn, allLogicals);

            metadataFields.add(new MetadataFieldInfo(className, fieldName,
                metadataLogicals.toArray(new LogicalVariable[0])));
            anyRewritten = true;
        }

        if (!anyRewritten) return null;

        // Inject metadata fields
        for (MetadataFieldInfo mfi : metadataFields) {
            cn.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                mfi.fieldName, METHOD_LVD_DESC, null, null));
        }

        // Add or extend <clinit>
        addClinitInit(cn, className, metadataFields);

        // Write with COMPUTE_FRAMES — recomputes StackMapTable from scratch
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                // Safe fallback: avoids class loading during transformation
                return "java/lang/Object";
            }
        };
        cn.accept(cw);
        return cw.toByteArray();
    }

    // ========== <clinit> generation ==========

    private void addClinitInit(ClassNode cn, String className,
                               List<MetadataFieldInfo> metadataFields) {
        MethodNode clinit = null;
        for (MethodNode mn : cn.methods) {
            if ("<clinit>".equals(mn.name)) {
                clinit = mn;
                break;
            }
        }

        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions = new InsnList();
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(clinit);
        }

        // Prepend initialization code before existing <clinit> body
        InsnList init = new InsnList();
        for (MetadataFieldInfo mfi : metadataFields) {
            emitMetadataFieldInit(init, mfi);
        }
        clinit.instructions.insert(init);
    }

    private void emitMetadataFieldInit(InsnList init, MetadataFieldInfo mfi) {
        LogicalVariable[] logicals = mfi.logicals;

        // Build the LocalVariableDetails[]
        init.add(MethodRewriter.pushInt(logicals.length));
        init.add(new TypeInsnNode(Opcodes.ANEWARRAY, LOCAL_VD_INTERNAL));

        for (int i = 0; i < logicals.length; i++) {
            LogicalVariable lv = logicals[i];
            init.add(new InsnNode(Opcodes.DUP));
            init.add(MethodRewriter.pushInt(i));

            // new LocalVariableDetails(name, slotNumber, originalStartPc, originalLength)
            init.add(new TypeInsnNode(Opcodes.NEW, LOCAL_VD_INTERNAL));
            init.add(new InsnNode(Opcodes.DUP));
            init.add(new LdcInsnNode(lv.name));
            init.add(MethodRewriter.pushInt(lv.newSlot));
            init.add(MethodRewriter.pushInt(lv.originalStartPc));
            init.add(MethodRewriter.pushInt(lv.originalLength));
            init.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, LOCAL_VD_INTERNAL, "<init>",
                "(Ljava/lang/String;III)V", false));

            init.add(new InsnNode(Opcodes.AASTORE));
        }

        // new MethodLocalVariableDetails(array)
        init.add(new TypeInsnNode(Opcodes.NEW, METHOD_LVD_INTERNAL));
        init.add(new InsnNode(Opcodes.DUP_X1));
        init.add(new InsnNode(Opcodes.SWAP));
        init.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, METHOD_LVD_INTERNAL, "<init>",
            "([Lai/jacc/simplejavatemplates/LocalVariableDetails;)V", false));

        init.add(new FieldInsnNode(Opcodes.PUTSTATIC, mfi.className, mfi.fieldName, METHOD_LVD_DESC));
    }

    // ========== Inner data classes ==========

    private static class CallSiteInfo {
        final MethodInsnNode node;
        final int originalPc;
        CallSiteInfo(MethodInsnNode node, int originalPc) {
            this.node = node;
            this.originalPc = originalPc;
        }
    }

    static class MetadataFieldInfo {
        final String className;
        final String fieldName;
        final LogicalVariable[] logicals;
        MetadataFieldInfo(String className, String fieldName, LogicalVariable[] logicals) {
            this.className = className;
            this.fieldName = fieldName;
            this.logicals = logicals;
        }
    }
}
