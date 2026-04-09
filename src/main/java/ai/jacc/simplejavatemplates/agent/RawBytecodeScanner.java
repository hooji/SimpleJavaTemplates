package ai.jacc.simplejavatemplates.agent;

import org.objectweb.asm.ClassReader;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans raw class file bytes to extract method code offsets, instruction PCs,
 * and LocalVariableTable entries. This avoids relying on ASM's tree API for
 * bytecode offset computation (which ASM does not expose).
 */
final class RawBytecodeScanner {

    private RawBytecodeScanner() {}

    // ========== Instruction size lookup table ==========

    private static final int[] INSN_SIZE = new int[256];
    static {
        java.util.Arrays.fill(INSN_SIZE, -1); // -1 = variable-size
        INSN_SIZE[0x00] = 1; // nop
        INSN_SIZE[0x01] = 1; // aconst_null
        for (int i = 0x02; i <= 0x0F; i++) INSN_SIZE[i] = 1; // iconst_m1..dconst_1
        INSN_SIZE[0x10] = 2; // bipush
        INSN_SIZE[0x11] = 3; // sipush
        INSN_SIZE[0x12] = 2; // ldc
        INSN_SIZE[0x13] = 3; // ldc_w
        INSN_SIZE[0x14] = 3; // ldc2_w
        for (int i = 0x15; i <= 0x19; i++) INSN_SIZE[i] = 2; // iload..aload
        for (int i = 0x1A; i <= 0x35; i++) INSN_SIZE[i] = 1; // iload_0..saload
        for (int i = 0x36; i <= 0x3A; i++) INSN_SIZE[i] = 2; // istore..astore
        for (int i = 0x3B; i <= 0x56; i++) INSN_SIZE[i] = 1; // istore_0..sastore
        for (int i = 0x57; i <= 0x5F; i++) INSN_SIZE[i] = 1; // pop..swap
        for (int i = 0x60; i <= 0x83; i++) INSN_SIZE[i] = 1; // iadd..lxor
        INSN_SIZE[0x84] = 3; // iinc
        for (int i = 0x85; i <= 0x93; i++) INSN_SIZE[i] = 1; // i2l..i2s
        for (int i = 0x94; i <= 0x98; i++) INSN_SIZE[i] = 1; // lcmp..dcmpg
        for (int i = 0x99; i <= 0xA6; i++) INSN_SIZE[i] = 3; // ifeq..if_acmpne
        INSN_SIZE[0xA7] = 3; // goto
        INSN_SIZE[0xA8] = 3; // jsr
        INSN_SIZE[0xA9] = 2; // ret
        // 0xAA tableswitch  - variable
        // 0xAB lookupswitch - variable
        for (int i = 0xAC; i <= 0xB1; i++) INSN_SIZE[i] = 1; // ireturn..return
        for (int i = 0xB2; i <= 0xB5; i++) INSN_SIZE[i] = 3; // getstatic..putfield
        for (int i = 0xB6; i <= 0xB8; i++) INSN_SIZE[i] = 3; // invokevirtual/special/static
        INSN_SIZE[0xB9] = 5; // invokeinterface
        INSN_SIZE[0xBA] = 5; // invokedynamic
        INSN_SIZE[0xBB] = 3; // new
        INSN_SIZE[0xBC] = 2; // newarray
        INSN_SIZE[0xBD] = 3; // anewarray
        INSN_SIZE[0xBE] = 1; // arraylength
        INSN_SIZE[0xBF] = 1; // athrow
        INSN_SIZE[0xC0] = 3; // checkcast
        INSN_SIZE[0xC1] = 3; // instanceof
        INSN_SIZE[0xC2] = 1; // monitorenter
        INSN_SIZE[0xC3] = 1; // monitorexit
        // 0xC4 wide - variable
        INSN_SIZE[0xC5] = 4; // multianewarray
        INSN_SIZE[0xC6] = 3; // ifnull
        INSN_SIZE[0xC7] = 3; // ifnonnull
        INSN_SIZE[0xC8] = 5; // goto_w
        INSN_SIZE[0xC9] = 5; // jsr_w
    }

    /**
     * Computes the bytecode size of the instruction at the given PC within the method.
     */
    static int instructionSize(byte[] b, int codeStart, int pc) {
        int opcode = b[codeStart + pc] & 0xFF;
        int fixed = INSN_SIZE[opcode];
        if (fixed > 0) return fixed;

        switch (opcode) {
            case 0xAA: { // tableswitch
                int padding = (4 - ((pc + 1) % 4)) % 4;
                int base = codeStart + pc + 1 + padding;
                int low = readInt(b, base + 4);
                int high = readInt(b, base + 8);
                return 1 + padding + 12 + (high - low + 1) * 4;
            }
            case 0xAB: { // lookupswitch
                int padding = (4 - ((pc + 1) % 4)) % 4;
                int base = codeStart + pc + 1 + padding;
                int npairs = readInt(b, base + 4);
                return 1 + padding + 8 + npairs * 8;
            }
            case 0xC4: { // wide
                int wideOp = b[codeStart + pc + 1] & 0xFF;
                return (wideOp == 0x84) ? 6 : 4; // wide iinc = 6, others = 4
            }
            default:
                throw new IllegalStateException(
                    "Unknown opcode 0x" + Integer.toHexString(opcode) + " at pc " + pc);
        }
    }

    /**
     * Walks the raw bytecode and returns the PC of every instruction, in order.
     */
    static int[] computeInstructionOffsets(byte[] b, int codeStart, int codeLength) {
        List<Integer> offsets = new ArrayList<Integer>();
        int pc = 0;
        while (pc < codeLength) {
            offsets.add(pc);
            pc += instructionSize(b, codeStart, pc);
        }
        int[] result = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) result[i] = offsets.get(i);
        return result;
    }

    // ========== Class file navigation ==========

    /**
     * Finds the Code attribute for a specific method in the raw class file bytes.
     * Returns null if the method or its Code attribute is not found.
     */
    static MethodCodeInfo findMethodRawCode(ClassReader cr, String targetName, String targetDesc) {
        byte[] b = cr.b;
        char[] buf = new char[cr.getMaxStringLength()];

        int offset = cr.header;
        // Skip access_flags(2) + this_class(2) + super_class(2)
        offset += 6;

        // Skip interfaces
        int ifaceCount = readUnsignedShort(b, offset);
        offset += 2 + ifaceCount * 2;

        // Skip fields
        int fieldCount = readUnsignedShort(b, offset);
        offset += 2;
        for (int i = 0; i < fieldCount; i++) {
            offset += 6; // access, name, desc
            int attrCount = readUnsignedShort(b, offset);
            offset += 2;
            for (int j = 0; j < attrCount; j++) {
                int attrLen = readInt(b, offset + 2);
                offset += 6 + attrLen;
            }
        }

        // Read methods
        int methodCount = readUnsignedShort(b, offset);
        offset += 2;
        for (int i = 0; i < methodCount; i++) {
            String name = cr.readUTF8(offset + 2, buf);
            String desc = cr.readUTF8(offset + 4, buf);
            offset += 6;
            int attrCount = readUnsignedShort(b, offset);
            offset += 2;

            MethodCodeInfo result = null;
            for (int j = 0; j < attrCount; j++) {
                String attrName = cr.readUTF8(offset, buf);
                int attrLen = readInt(b, offset + 2);

                if (name.equals(targetName) && desc.equals(targetDesc)
                        && "Code".equals(attrName)) {
                    result = parseCodeAttribute(cr, b, offset + 6, buf);
                }
                offset += 6 + attrLen;
            }
            if (result != null) return result;
        }
        return null;
    }

    private static MethodCodeInfo parseCodeAttribute(ClassReader cr, byte[] b,
                                                     int codeAttrStart, char[] buf) {
        int off = codeAttrStart;
        int maxLocals = readUnsignedShort(b, off + 2);
        int codeLength = readInt(b, off + 4);
        int codeStart = off + 8;

        // Skip past code bytes
        off = codeStart + codeLength;

        // Skip exception table
        int excCount = readUnsignedShort(b, off);
        off += 2 + excCount * 8;

        // Parse sub-attributes looking for LocalVariableTable
        List<RawLVTEntry> lvt = new ArrayList<RawLVTEntry>();
        int subAttrCount = readUnsignedShort(b, off);
        off += 2;
        for (int i = 0; i < subAttrCount; i++) {
            String attrName = cr.readUTF8(off, buf);
            int attrLen = readInt(b, off + 2);

            if ("LocalVariableTable".equals(attrName)) {
                int lvtOff = off + 6;
                int lvtCount = readUnsignedShort(b, lvtOff);
                lvtOff += 2;
                for (int j = 0; j < lvtCount; j++) {
                    int startPc = readUnsignedShort(b, lvtOff);
                    int length = readUnsignedShort(b, lvtOff + 2);
                    String varName = cr.readUTF8(lvtOff + 4, buf);
                    String varDesc = cr.readUTF8(lvtOff + 6, buf);
                    int slot = readUnsignedShort(b, lvtOff + 8);
                    lvt.add(new RawLVTEntry(varName, varDesc, slot, startPc, length));
                    lvtOff += 10;
                }
            }
            off += 6 + attrLen;
        }

        return new MethodCodeInfo(codeStart, codeLength, maxLocals, lvt);
    }

    // ========== Byte reading utilities ==========

    static int readUnsignedShort(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
    }

    static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16) |
               ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }

    // ========== Data classes ==========

    static final class MethodCodeInfo {
        final int codeStart;
        final int codeLength;
        final int maxLocals;
        final List<RawLVTEntry> lvtEntries;

        MethodCodeInfo(int codeStart, int codeLength, int maxLocals, List<RawLVTEntry> lvtEntries) {
            this.codeStart = codeStart;
            this.codeLength = codeLength;
            this.maxLocals = maxLocals;
            this.lvtEntries = lvtEntries;
        }
    }

    static final class RawLVTEntry {
        final String name;
        final String descriptor;
        final int slot;
        final int startPc;
        final int length;

        RawLVTEntry(String name, String descriptor, int slot, int startPc, int length) {
            this.name = name;
            this.descriptor = descriptor;
            this.slot = slot;
            this.startPc = startPc;
            this.length = length;
        }
    }
}
