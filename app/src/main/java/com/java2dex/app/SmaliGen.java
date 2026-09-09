package com.java2dex.app;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.InstructionWithReference;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.instruction.ThreeRegisterInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Lightweight smali disassembler built on dexlib2 */
public final class SmaliGen {

    private SmaliGen() {}

    public static DexBackedDexFile open(File dexFile) throws Exception {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(dexFile), 262144);
        try {
            return DexBackedDexFile.fromInputStream(null, in);
        } finally {
            in.close();
        }
    }

    public static List<String> classNames(DexBackedDexFile dex) {
        List<String> out = new ArrayList<String>();
        for (ClassDef c : dex.getClasses()) out.add(pretty(c.getType()));
        Collections.sort(out);
        return out;
    }

    public static List<String> stringList(DexBackedDexFile dex) {
        Set<String> s = dex.getStrings();
        List<String> out = new ArrayList<String>(s);
        Collections.sort(out);
        return out;
    }

    public static String pretty(String type) {
        if (type != null && type.length() > 2 && type.charAt(0) == 'L'
                && type.charAt(type.length() - 1) == ';') {
            return type.substring(1, type.length() - 1).replace('/', '.');
        }
        return type == null ? "?" : type;
    }

    public static ClassDef findClass(DexBackedDexFile dex, String prettyName) {
        String raw = "L" + prettyName.replace('.', '/') + ";";
        for (ClassDef c : dex.getClasses()) {
            if (raw.equals(c.getType())) return c;
        }
        return null;
    }

    public static String renderClass(ClassDef c) {
        StringBuilder b = new StringBuilder();
        b.append(".class ").append(AccessFlags.formatAccessFlagsForClass(c.getAccessFlags()))
                .append(c.getType()).append('\n');
        if (c.getSuperclass() != null) {
            b.append(".super ").append(c.getSuperclass()).append('\n');
        }
        for (String i : c.getInterfaces()) {
            b.append(".implements ").append(i).append('\n');
        }
        b.append('\n');
        for (Field f : c.getFields()) {
            b.append(".field ").append(AccessFlags.formatAccessFlagsForField(f.getAccessFlags()))
                    .append(f.getName()).append(':').append(f.getType());
            try {
                Object v = f.getInitialValue();
                if (v != null) b.append(" = ").append(litValue(v));
            } catch (Throwable ignored) { }
            b.append('\n');
        }
        b.append('\n');
        for (Method m : c.getMethods()) {
            b.append(".method ").append(AccessFlags.formatAccessFlagsForMethod(m.getAccessFlags()))
                    .append(m.getName()).append('(');
            for (Object t : m.getParameterTypes()) b.append(t);
            b.append(')').append(m.getReturnType()).append('\n');
            MethodImplementation impl = m.getImplementation();
            if (impl != null) {
                b.append("    .registers ").append(impl.getRegisterCount()).append('\n');
                for (Instruction ins : impl.getInstructions()) {
                    b.append("    ").append(fmt(ins)).append('\n');
                }
            }
            b.append(".end method\n\n");
        }
        return b.toString();
    }

    private static String litValue(Object v) {
        if (v instanceof String) return quote((String) v);
        return String.valueOf(v);
    }

    private static String fmt(Instruction ins) {
        try {
            StringBuilder b = new StringBuilder(ins.getOpcode().name);

            if (ins instanceof OffsetInstruction) {
                int off = ((OffsetInstruction) ins).getCodeOffset();
                long target = (long) ins.getLocation() + off;
                if (ins instanceof TwoRegisterInstruction) {
                    TwoRegisterInstruction t = (TwoRegisterInstruction) ins;
                    b.append(" v").append(t.getRegisterA()).append(", v").append(t.getRegisterB());
                } else if (ins instanceof OneRegisterInstruction) {
                    b.append(" v").append(((OneRegisterInstruction) ins).getRegisterA());
                }
                b.append("  # -> 0x").append(Long.toHexString(target));
                return b.toString();
            }

            if (ins instanceof RegisterRangeInstruction && ins instanceof InstructionWithReference) {
                b.append(' ').append(rangeRegs((RegisterRangeInstruction) ins))
                        .append(", ").append(refStr(((InstructionWithReference) ins).getReference()));
                return b.toString();
            }
            if (ins instanceof InstructionWithReference) {
                b.append(' ').append(simpleRegs(ins))
                        .append(", ").append(refStr(((InstructionWithReference) ins).getReference()));
                return b.toString();
            }
            if (ins instanceof RegisterRangeInstruction) {
                b.append(' ').append(rangeRegs((RegisterRangeInstruction) ins));
                return b.toString();
            }
            if (ins instanceof WideLiteralInstruction) {
                long lit = ((WideLiteralInstruction) ins).getWideLiteral();
                if (ins instanceof OneRegisterInstruction) {
                    b.append(" v").append(((OneRegisterInstruction) ins).getRegisterA())
                            .append(", ").append(lit);
                } else {
                    b.append(' ').append(lit);
                }
                return b.toString();
            }
            if (ins instanceof NarrowLiteralInstruction) {
                long lit = ((NarrowLiteralInstruction) ins).getNarrowLiteral();
                if (ins instanceof TwoRegisterInstruction) {
                    TwoRegisterInstruction t = (TwoRegisterInstruction) ins;
                    b.append(" v").append(t.getRegisterA()).append(", v")
                            .append(t.getRegisterB()).append(", ").append(lit);
                } else if (ins instanceof OneRegisterInstruction) {
                    b.append(" v").append(((OneRegisterInstruction) ins).getRegisterA())
                            .append(", ").append(lit);
                } else {
                    b.append(' ').append(lit);
                }
                return b.toString();
            }
            if (ins instanceof ThreeRegisterInstruction) {
                ThreeRegisterInstruction t = (ThreeRegisterInstruction) ins;
                b.append(" v").append(t.getRegisterA()).append(", v")
                        .append(t.getRegisterB()).append(", v").append(t.getRegisterC());
            } else if (ins instanceof TwoRegisterInstruction) {
                TwoRegisterInstruction t = (TwoRegisterInstruction) ins;
                b.append(" v").append(t.getRegisterA()).append(", v").append(t.getRegisterB());
            } else if (ins instanceof OneRegisterInstruction) {
                b.append(" v").append(((OneRegisterInstruction) ins).getRegisterA());
            }
            return b.toString();
        } catch (Throwable t) {
            return ins.getOpcode().name;
        }
    }

    private static String rangeRegs(RegisterRangeInstruction r) {
        int n = r.getRegisterCount();
        int s = r.getStartRegister();
        if (n <= 0) return "{}";
        if (n == 1) return "{v" + s + "}";
        return "{v" + s + " .. v" + (s + n - 1) + "}";
    }

    private static String simpleRegs(Instruction ins) {
        if (ins instanceof FiveRegisterInstruction) {
            int n = 5;
            try {
                java.lang.reflect.Method m = ins.getClass().getMethod("getRegisterCount");
                n = ((Integer) m.invoke(ins)).intValue();
            } catch (Throwable ignored) { }
            StringBuilder b = new StringBuilder("{");
            for (int i = 0; i < n && i < 5; i++) {
                if (i > 0) b.append(", ");
                b.append('v').append(((FiveRegisterInstruction) ins).getRegister(i));
            }
            return b.append('}').toString();
        }
        if (ins instanceof TwoRegisterInstruction) {
            TwoRegisterInstruction t = (TwoRegisterInstruction) ins;
            return "v" + t.getRegisterA() + ", v" + t.getRegisterB();
        }
        if (ins instanceof OneRegisterInstruction) {
            return "v" + ((OneRegisterInstruction) ins).getRegisterA();
        }
        return "";
    }

    private static String refStr(Reference r) {
        try {
            if (r instanceof StringReference) return quote(((StringReference) r).getString());
            if (r instanceof MethodReference) {
                MethodReference m = (MethodReference) r;
                StringBuilder b = new StringBuilder(m.getDefiningClass())
                        .append("->").append(m.getName()).append('(');
                for (Object t : m.getParameterTypes()) b.append(t);
                return b.append(')').append(m.getReturnType()).toString();
            }
            if (r instanceof FieldReference) {
                FieldReference f = (FieldReference) r;
                return f.getDefiningClass() + "->" + f.getName() + ':' + f.getType();
            }
            if (r instanceof TypeReference) return ((TypeReference) r).getType();
            return String.valueOf(r);
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') b.append("\\n");
            else if (ch == '\t') b.append("\\t");
            else if (ch == '\r') b.append("\\r");
            else if (ch == '"') b.append("\\\"");
            else if (ch == '\\') b.append("\\\\");
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
