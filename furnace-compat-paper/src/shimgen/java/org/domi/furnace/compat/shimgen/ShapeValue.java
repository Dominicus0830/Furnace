package org.domi.furnace.compat.shimgen;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * T-263 S5 모양값(채움 표 X 줄, 형식은 FillTable 머리말)을 읽고 클래스 초기화 코드로 낸다. 낸 코드는 NmsShapeTables 를 부른다 —
 * 리플렉션·invokedynamic 없음, 지역 변수 없음, 스택 8칸 안.
 *
 * @param kind     'v' 모양 하나, 'a' 배열, 'm' 지도
 * @param id       배열·지도의 객체 번호(모양 하나면 -1)
 * @param mapKind  지도 종류(enum·ienum·imm·hash), 아니면 null
 * @param keyEnum  지도 열쇠 열거형의 내부 이름(정수 열쇠·지도 아님이면 null)
 * @param keys     지도 열쇠(열거형 상수 이름 또는 정수 글)
 * @param shapes   모양들(배열 칸·지도 값 순서)
 */
record ShapeValue(char kind, int id, String mapKind, String keyEnum, List<String> keys, List<Shape> shapes) {
    static final int MAX_STACK = 8;
    private static final String TABLES = "org/domi/furnace/compat/nms/NmsShapeTables";
    private static final String VS = "net/minecraft/world/phys/shapes/VoxelShape";
    private static final String VS_DESC = "L" + VS + ";";

    /** 모양 하나: E(빈 모양 객체)·B(꽉 찬 블록 객체) 는 id -1·-2, 아니면 번호와 좌표 글. */
    record Shape(int id, String nums) {}

    static ShapeValue parse(String v) {
        String[] t = v.split(" ");
        try {
            switch (t[0]) {
                case "v" -> {
                    if (t.length != 2) break;
                    return new ShapeValue('v', -1, null, null, List.of(), List.of(shape(t[1])));
                }
                case "a" -> {
                    int n = Integer.parseInt(t[2]);
                    if (t.length != 3 + n) break;
                    List<Shape> s = new ArrayList<>();
                    for (int i = 0; i < n; i++) s.add(shape(t[3 + i]));
                    return new ShapeValue('a', Integer.parseInt(t[1]), null, null, List.of(), s);
                }
                case "m" -> {
                    String kind = t[2];
                    if (!List.of("enum", "ienum", "imm", "hash").contains(kind)) break;
                    String keyType = t[3];
                    String keyEnum = keyType.startsWith("e:") ? keyType.substring(2) : null;
                    if (keyEnum == null && !keyType.equals("i") || kind.equals("enum") && keyEnum == null || kind.equals("ienum") && keyEnum == null) break;
                    int n = Integer.parseInt(t[4]);
                    if (t.length != 5 + n || n == 0) break;
                    List<String> keys = new ArrayList<>();
                    List<Shape> s = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        String e = t[5 + i];
                        int eq = e.indexOf('=');
                        String k = e.substring(0, eq);
                        if (keyEnum == null) Integer.parseInt(k);
                        keys.add(k);
                        s.add(shape(e.substring(eq + 1)));
                    }
                    return new ShapeValue('m', Integer.parseInt(t[1]), kind, keyEnum, keys, s);
                }
                default -> { }
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("bad shape value: " + v, e);
        }
        throw new IllegalArgumentException("bad shape value: " + v);
    }

    private static Shape shape(String s) {
        if (s.equals("E")) return new Shape(-1, null);
        if (s.equals("B")) return new Shape(-2, null);
        int c = s.indexOf(':');
        String nums = s.substring(c + 1);
        if (!nums.isEmpty()) {
            String[] p = nums.split(",");
            if (p.length % 6 != 0) throw new IllegalArgumentException("box coordinates come in sixes: " + s);
            for (String x : p) Double.parseDouble(x);
        }
        if (nums.length() > 60_000) throw new IllegalArgumentException("shape too large for one constant: " + s.substring(0, 40));
        return new Shape(Integer.parseInt(s.substring(0, c)), nums);
    }

    /** 값을 스택에 올리고 정적 필드에 넣는 코드: owner.field = 값. */
    void emitPut(MethodVisitor mv, String owner, String field, String desc) {
        switch (kind) {
            case 'v' -> pushShape(mv, shapes.get(0));
            case 'a' -> {
                mv.visitLdcInsn(id);
                pushShapeArray(mv);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, TABLES, "array", "(I[" + VS_DESC + ")[" + VS_DESC, false);
            }
            default -> {
                mv.visitLdcInsn(id);
                mv.visitLdcInsn(mapKind);
                mv.visitLdcInsn(keys.size());
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
                for (int i = 0; i < keys.size(); i++) {
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitLdcInsn(i);
                    if (keyEnum != null) {
                        mv.visitFieldInsn(Opcodes.GETSTATIC, keyEnum, keys.get(i), "L" + keyEnum + ";");
                    } else {
                        mv.visitLdcInsn(Integer.parseInt(keys.get(i)));
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    }
                    mv.visitInsn(Opcodes.AASTORE);
                }
                pushShapeArray(mv);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, TABLES, "map", "(ILjava/lang/String;[Ljava/lang/Object;[" + VS_DESC + ")Ljava/util/Map;", false);
            }
        }
        Type ft = Type.getType(desc);
        if (kind != 'v' && !ft.getDescriptor().equals(kind == 'a' ? "[" + VS_DESC : "Ljava/util/Map;")) mv.visitTypeInsn(Opcodes.CHECKCAST, ft.getInternalName());
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, desc);
    }

    private void pushShapeArray(MethodVisitor mv) {
        mv.visitLdcInsn(shapes.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, VS);
        for (int i = 0; i < shapes.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            pushShape(mv, shapes.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private static void pushShape(MethodVisitor mv, Shape s) {
        if (s.id() == -1 || s.id() == -2) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/phys/shapes/Shapes", s.id() == -1 ? "empty" : "block", "()" + VS_DESC, false);
            return;
        }
        mv.visitLdcInsn(s.id());
        mv.visitLdcInsn(s.nums());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, TABLES, "shape", "(ILjava/lang/String;)" + VS_DESC, false);
    }
}
