package org.domi.furnace.tools.extract;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 본문 지문표(버전 사이 "동작이 바뀐 후보" 찾기용). 메서드마다 **명령 흐름의 SHA-256 앞 12자리**만 남긴다.
 *
 * <p>서명표({@link SigTable}, T-227)는 구조상 메서드 몸통을 안 읽는다는 약속이 있어 그대로 두고, 이 표는 따로 만든다.
 * 몸통의 내용(명령·상수)은 어디에도 저장하지 않고 해시만 쓴다 — 두 판의 같은 메서드를 해시로 견주어 "달라졌다/같다"만 안다.
 * 표는 서명표처럼 개발자 캐시에만 두고 커밋·배포하지 않는다. 판 사이 보고서에 커밋할 때는 개수만 적는다.
 *
 * <p>해시는 두 개다. <b>모양 해시</b>는 명령 코드, 정수·문자열 상수, 분기 대상의 순서 번호, try-catch 구조, 지역 변수 번호만 넣는다
 * (호출·필드·형식이 가리키는 이름은 뺀다). <b>참조 해시</b>는 호출·필드 접근의 (소유자, 이름, 서술자)와 형식 명령의 클래스를 넣는다.
 * 모양이 같고 참조만 다르면 "다른 이름을 부르게 됐다"(이름 바뀜·옮김)일 뿐 흐름은 그대로라 약한 후보, 모양이 다르면 흐름이 바뀐 진짜 후보다.
 * 뺀 것: 줄 번호·지역 변수 이름·프레임(SKIP_DEBUG|SKIP_FRAMES). 람다 몸통은 컴파일러가
 * 번호를 붙인 합성 메서드(`lambda$이름$N`)라 번호가 판마다 흔들리므로, 호출 쪽은 `lambda$` 로만 적고 클래스마다 람다 해시를 정렬해
 * 묶은 한 줄(`L`)로 따로 비교한다.
 *
 * <p>형식(탭, UTF-8, gzip, 같은 입력이면 같은 바이트):
 * <pre>
 * #furnace-body-table 1 &lt;이름표&gt;
 * M 소유자 이름 서술자 모양해시12 참조해시12   (합성이 아닌 메서드·생성자·&lt;clinit&gt;)
 * L 소유자 람다개수 모양묶음12 참조묶음12      (람다 합성 메서드가 있는 클래스)
 * K 소유자 이름 서술자 값           (static final 기본형·문자열 상수 — ConstantValue 속성)
 * </pre>
 */
public final class BodyTable {
    public static final String MAGIC = "#furnace-body-table";
    private static final int MAXSTR = 160;

    private final String label;
    private final Map<String, String> methods = new TreeMap<>();
    private final Map<String, String> lambdas = new TreeMap<>();
    private final Map<String, String> constants = new TreeMap<>();

    private BodyTable(String label) {
        this.label = label;
    }

    public int methodCount() {
        return methods.size();
    }

    public static BodyTable fromJar(Path jar, List<String> prefixes, String label) throws IOException {
        BodyTable t = new BodyTable(label);
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                if (!SigTable.matches(n, prefixes)) continue;
                try (InputStream in = zf.getInputStream(e)) {
                    t.add(in.readAllBytes());
                }
            }
        }
        return t;
    }

    void add(byte[] classBytes) {
        ClassReader r = new ClassReader(classBytes);
        Collector c = new Collector();
        r.accept(c, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!c.lambdaShapes.isEmpty()) {
            Collections.sort(c.lambdaShapes);
            Collections.sort(c.lambdaRefs);
            lambdas.put(c.name, c.lambdaShapes.size() + "\t" + sha(String.join(",", c.lambdaShapes)) + "\t" + sha(String.join(",", c.lambdaRefs)));
        }
    }

    private final class Collector extends ClassVisitor {
        String name;
        final List<String> lambdaShapes = new ArrayList<>();
        final List<String> lambdaRefs = new ArrayList<>();

        Collector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.name = name;
        }

        @Override
        public FieldVisitor visitField(int access, String fname, String desc, String signature, Object value) {
            if (value != null && (access & Opcodes.ACC_STATIC) != 0) {
                String v = value instanceof String s ? "\"" + (s.length() > MAXSTR ? s.substring(0, MAXSTR) + "…" : s) + "\"" : String.valueOf(value);
                constants.put(name + "\t" + fname + "\t" + desc, v.replace('\n', ' ').replace('\t', ' '));
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String mname, String desc, String signature, String[] exceptions) {
            boolean synthetic = (access & Opcodes.ACC_SYNTHETIC) != 0;
            boolean lambda = synthetic && mname.startsWith("lambda$");
            if (synthetic && !lambda) return null;
            return new Hasher(lambda ? null : name + "\t" + mname + "\t" + desc, lambda ? lambdaShapes : null, lambda ? lambdaRefs : null);
        }
    }

    /** 명령 흐름을 두 글자열(모양·참조)로 이어 붙여 마지막에 해시한다(글자열은 저장하지 않는다). */
    private final class Hasher extends MethodVisitor {
        private final String key;
        private final List<String> shapeSink;
        private final List<String> refSink;
        private final StringBuilder shape = new StringBuilder(256);
        private final StringBuilder refs = new StringBuilder(128);
        private final Map<Label, Integer> labels = new HashMap<>();

        Hasher(String key, List<String> shapeSink, List<String> refSink) {
            super(Opcodes.ASM9);
            this.key = key;
            this.shapeSink = shapeSink;
            this.refSink = refSink;
        }

        private int lab(Label l) {
            return labels.computeIfAbsent(l, k -> labels.size());
        }

        private static String h(Handle x) {
            String n = x.getName().startsWith("lambda$") ? "lambda$" : x.getName();
            return x.getTag() + ":" + x.getOwner() + "." + n + x.getDesc();
        }

        /** 이름을 안 가리키는 명령: 모양에만 */
        private void op(int opcode, String a) {
            shape.append(opcode).append(' ').append(a).append(';');
        }

        /** 이름을 가리키는 명령: 모양에는 명령 코드만, 참조에 가리키는 곳 */
        private void ref(int opcode, String a) {
            shape.append(opcode).append(" @;");
            refs.append(opcode).append(' ').append(a).append(';');
        }

        @Override public void visitInsn(int o) { op(o, ""); }
        @Override public void visitIntInsn(int o, int v) { op(o, Integer.toString(v)); }
        @Override public void visitVarInsn(int o, int v) { op(o, Integer.toString(v)); }
        @Override public void visitTypeInsn(int o, String t) { ref(o, t); }
        @Override public void visitFieldInsn(int o, String ow, String n, String d) { ref(o, ow + "." + n + ":" + d); }

        @Override
        public void visitMethodInsn(int o, String ow, String n, String d, boolean itf) {
            ref(o, ow + "." + (n.startsWith("lambda$") ? "lambda$" : n) + d + (itf ? "i" : ""));
        }

        @Override
        public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
            StringBuilder a = new StringBuilder(h(bsm)).append(' ').append(n).append(d);
            StringBuilder lit = new StringBuilder();
            for (Object x : args) {
                a.append(' ');
                if (x instanceof Handle hh) a.append(h(hh));
                else if (x instanceof Type t) a.append(t.getDescriptor());
                else {
                    a.append(x);
                    lit.append(x).append('|'); // 문자열 이어 붙이기 레시피 같은 상수는 모양에 넣는다
                }
            }
            shape.append(Opcodes.INVOKEDYNAMIC).append(' ').append(lit).append(';');
            refs.append(Opcodes.INVOKEDYNAMIC).append(' ').append(a).append(';');
        }

        @Override public void visitJumpInsn(int o, Label l) { op(o, Integer.toString(lab(l))); }
        @Override public void visitLabel(Label l) { shape.append('L').append(lab(l)).append(';'); }

        @Override
        public void visitLdcInsn(Object v) {
            if (v instanceof Type t) ref(Opcodes.LDC, "T" + t.getDescriptor());
            else op(Opcodes.LDC, v.getClass().getSimpleName() + ":" + v);
        }

        @Override public void visitIincInsn(int v, int inc) { op(Opcodes.IINC, v + "," + inc); }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... ls) {
            StringBuilder a = new StringBuilder(min + "," + max + "," + lab(dflt));
            for (Label l : ls) a.append(',').append(lab(l));
            op(Opcodes.TABLESWITCH, a.toString());
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] ls) {
            StringBuilder a = new StringBuilder(Integer.toString(lab(dflt)));
            for (int i = 0; i < keys.length; i++) a.append(',').append(keys[i]).append('>').append(lab(ls[i]));
            op(Opcodes.LOOKUPSWITCH, a.toString());
        }

        @Override public void visitMultiANewArrayInsn(String d, int n) { ref(Opcodes.MULTIANEWARRAY, d + n); }

        @Override
        public void visitTryCatchBlock(Label s, Label e, Label hd, String t) {
            shape.append("TC").append(lab(s)).append(',').append(lab(e)).append(',').append(lab(hd)).append(';');
            refs.append("TC ").append(t).append(';');
        }

        @Override
        public void visitEnd() {
            String sh = sha(shape.toString());
            String rf = sha(refs.toString());
            if (shapeSink != null) {
                shapeSink.add(sh);
                refSink.add(rf);
            } else {
                methods.put(key, sh + "\t" + rf);
            }
        }
    }

    static String sha(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void write(Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(tmp);
             GZIPOutputStream gz = new GZIPOutputStream(os);
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
            w.write(MAGIC + "\t1\t" + label + "\n");
            for (var e : methods.entrySet()) w.write("M\t" + e.getKey() + "\t" + e.getValue() + "\n");
            for (var e : lambdas.entrySet()) w.write("L\t" + e.getKey() + "\t" + e.getValue() + "\n");
            for (var e : constants.entrySet()) w.write("K\t" + e.getKey() + "\t" + e.getValue() + "\n");
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
