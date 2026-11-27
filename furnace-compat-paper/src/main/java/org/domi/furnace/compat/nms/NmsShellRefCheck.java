package org.domi.furnace.compat.nms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.compat.i18n.CompatLang;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 기동 자기 점검(설계 shell-refs-and-field-redirect-2026-11-27 2.3, 검토 필수 1): 껍데기 jar 가 실렸으면 명세 {@code ref} 줄의 틀 클래스마다
 * 로더가 주는 바이트가 진짜 껍데기인지(틀 표지 {@link NmsShellRef} 가 없는지) 본다. 순서가 맞는 것은 지금 색인 정렬과 부모 우선 공유 로더 덕분이라,
 * 그것이 바뀌면 틀이 조용히 이기고 손 멤버가 전부 던진다 — 그때 호환 계층을 띄우지 않는다. 껍데기 jar 가 없는 축소 빌드는 틀이 대체물로 쓰이는 것이 정상이라 보지 않는다.
 */
public final class NmsShellRefCheck {
    /** 생성기가 내는 틀 목록(shimgen ShimGenerator.SHELL_REFS_INDEX 와 같은 이름). */
    static final String REFS = "META-INF/furnace/shell-refs.txt";
    /** 껍데기 jar 에만 있는 자원(합친 클래스 목록, shimgen ShellMerge.MERGED). 있으면 껍데기 jar 가 실렸다. */
    static final String SHELLS_MARK = "META-INF/furnace/full-shells/merged-classes.txt";
    private static final String REF_DESC = "L" + NmsShellRef.class.getName().replace('.', '/') + ";";

    private static volatile List<String> result;

    private NmsShellRefCheck() {}

    /** 한 번만 보고 결과를 기억한다. 틀이 이긴 클래스가 있으면 {@link IllegalStateException}(호환 계층 시작 거부). */
    public static void verify() {
        List<String> r = result;
        if (r == null) {
            synchronized (NmsShellRefCheck.class) {
                if (result == null) result = List.copyOf(templatesWinning(NmsShellRefCheck.class.getClassLoader()));
                r = result;
            }
        }
        if (!r.isEmpty()) throw new IllegalStateException(CompatLang.t("nms.shell-ref-wins", r.size(), String.join(", ", r)));
    }

    /** 껍데기 jar 가 실렸는데 틀이 로더에서 나오는 클래스(이진 이름). 껍데기 jar 가 없거나 틀이 없으면 빈 목록. */
    public static List<String> templatesWinning(ClassLoader cl) {
        List<String> out = new ArrayList<>();
        if (cl.getResource(SHELLS_MARK) == null) return out;
        for (String n : refs(cl)) {
            try (InputStream in = cl.getResourceAsStream(n + ".class")) {
                if (in == null || isTemplate(in.readAllBytes())) out.add(n.replace('/', '.'));
            } catch (IOException e) {
                out.add(n.replace('/', '.'));
            }
        }
        return out;
    }

    /** 로더에 보이는 틀 목록 전부(같은 이름의 자원이 여럿이면 합친다 — 부모 로더의 빈 목록이 호환 jar 의 목록을 가리지 않게). */
    static List<String> refs(ClassLoader cl) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        try {
            for (java.net.URL u : java.util.Collections.list(cl.getResources(REFS))) {
                try (InputStream in = u.openStream()) {
                    for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                        String n = l.strip();
                        if (!n.isEmpty()) out.add(n);
                    }
                }
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return new ArrayList<>(out);
    }

    /** 클래스 바이트에 틀 표지가 있는가. */
    public static boolean isTemplate(byte[] bytes) {
        boolean[] hit = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals(REF_DESC)) hit[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return hit[0];
    }
}
