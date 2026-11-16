package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * 난독화된 바닐라 서버 jar 를 Mojang 이름으로 바꾼 jar 로 다시 쓴다. 클래스·필드·메서드 이름만 바꾸고 로직·접근 제한은 그대로다.
 * 서명 파일(META-INF/*.SF·RSA·DSA)은 뺀다 — 바이트코드를 바꾸면 서명이 안 맞아 클래스 로드가 거부된다.
 */
public final class JarRemapper {
    private final MojangMappings maps;
    private final ClassHierarchy hierarchy;
    private final Remapper remapper;

    public JarRemapper(MojangMappings maps, ClassHierarchy hierarchy) {
        this.maps = maps;
        this.hierarchy = hierarchy;
        this.remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return maps.mapClass(internalName);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                String r = hierarchy.findUp(owner, o -> {
                    MojangMappings.ClassMap c = maps.byObf(o);
                    return c == null ? null : c.field(name, descriptor);
                });
                return r == null ? name : r;
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                if (owner.startsWith("[") || name.equals("<init>") || name.equals("<clinit>")) return name;
                String r = hierarchy.findUp(owner, o -> {
                    MojangMappings.ClassMap c = maps.byObf(o);
                    return c == null ? null : c.method(name, descriptor);
                });
                return r == null ? name : r;
            }
        };
    }

    /** 이름 변환기(시험·도구 공용). */
    public Remapper remapper() {
        return remapper;
    }

    public byte[] remapClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor v = new ClassRemapper(writer, remapper) {
            @Override
            protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
                return new LambdaAwareMethodRemapper(mv, remapper);
            }
        };
        reader.accept(v, 0);
        return writer.toByteArray();
    }

    /**
     * invokedynamic 의 메서드 이름은 "람다가 구현하는 인터페이스의 메서드 이름"이라 인터페이스(호출 서술자의 반환형)와
     * 구현 메서드 서술자(부트스트랩 인자 0 번)로 찾아야 한다. 기본 구현은 이름만 보고 못 바꾼다.
     */
    private static final class LambdaAwareMethodRemapper extends MethodRemapper {
        LambdaAwareMethodRemapper(MethodVisitor mv, Remapper remapper) {
            super(mv, remapper);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... bsmArgs) {
            String newName = name;
            if (bsmArgs.length > 0 && bsmArgs[0] instanceof Type sam && sam.getSort() == Type.METHOD) {
                String iface = Type.getReturnType(descriptor).getInternalName();
                newName = remapper.mapMethodName(iface, name, sam.getDescriptor());
            }
            Object[] args = new Object[bsmArgs.length];
            for (int i = 0; i < args.length; i++) args[i] = remapper.mapValue(bsmArgs[i]);
            mv.visitInvokeDynamicInsn(newName, remapper.mapMethodDesc(descriptor), (Handle) remapper.mapValue(bsm), args);
        }
    }

    /** jar 전체를 변환한다. 반환값은 바꾼 클래스 수. */
    public int remapJar(Path in, Path out) throws IOException {
        int classes = 0;
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (ZipFile zf = new ZipFile(in.toFile());
             OutputStream os = Files.newOutputStream(out);
             JarOutputStream jo = new JarOutputStream(os)) {
            Manifest mf = new Manifest();
            mf.getMainAttributes().putValue("Manifest-Version", "1.0");
            jo.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            mf.write(jo);
            jo.closeEntry();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (e.isDirectory() || isSignature(name) || name.equals("META-INF/MANIFEST.MF")) continue;
                byte[] data;
                try (InputStream is = zf.getInputStream(e)) {
                    data = is.readAllBytes();
                }
                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - ".class".length());
                    data = remapClass(data);
                    name = maps.mapClass(internal) + ".class";
                    classes++;
                }
                JarEntry je = new JarEntry(name);
                je.setTime(315532800000L);   // 1980-01-01 — 같은 입력이면 같은 jar
                jo.putNextEntry(je);
                jo.write(data);
                jo.closeEntry();
            }
        }
        return classes;
    }

    /** 서명 파일만 뺀 복사본(바닐라 jar 를 이름 풀기 없이 그대로 돌려야 할 때 — 서명된 채로는 다른 jar 의 같은 패키지 클래스를 못 올린다). */
    public static void stripSignatures(Path in, Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (ZipFile zf = new ZipFile(in.toFile()); OutputStream os = Files.newOutputStream(out); JarOutputStream jo = new JarOutputStream(os)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || isSignature(e.getName())) continue;
                byte[] data;
                try (InputStream is = zf.getInputStream(e)) {
                    data = is.readAllBytes();
                }
                if (e.getName().equals("META-INF/MANIFEST.MF")) data = "Manifest-Version: 1.0\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                JarEntry je = new JarEntry(e.getName());
                je.setTime(315532800000L);
                jo.putNextEntry(je);
                jo.write(data);
                jo.closeEntry();
            }
        }
    }

    static boolean isSignature(String name) {
        if (!name.startsWith("META-INF/")) return false;
        String n = name.substring("META-INF/".length());
        if (n.contains("/")) return false;
        return n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC");
    }
}
