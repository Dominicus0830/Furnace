// 호환 기준 장면 compat-asm-prefilter — NmsClassTransformer 의 사전 거르기(mayNeed: 상수 풀 UTF-8 이름 조각이 하나라도 있나)와 변환 결과를 기록한다.
//  (1) 경계 사례: 바늘(필요한 조각)이 맨 앞·맨 끝·가운데·접두부가 겹친 채·한 글자 모자란 채·배열보다 길 때·빈 배열일 때의 판정.
//  (2) 실제 jar 말뭉치(furnace-compat-paper 의 realWorld 의존성 13개, 버전 고정): 클래스마다 (판정, 변환 결과 바이트) 해시. 기본 옵션과 버전 접미사 패키지 옵션 두 가지.
// 기대는 쪽: P-O13-10 "한 번 훑기(첫 바이트 분기)" — 판정이 같으면 변환 입력이 같다(wave2-rebuttal-compat.md 5장: JDK 클래스 15405개 판정 차이 0, 8~10배).
// 새 JVM 이 필요 없다(서버 없음, 정적 상태 없음): 같은 JVM 모드. realWorld jar 경로는 gradle 이 -Dfurnace.realworld.jars 로 준다.
// 변환 결과 바이트는 ASM 버전·의존성 jar 버전에 따라 달라진다 — ASM 이나 realWorld 의존성 버전을 올리면 이 금 파일은 그 PR 이 사유를 적어 다시 만든다.
package org.domi.furnace.compat.nms;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.LibraryScene;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;

public final class SceneAsmPrefilter implements LibraryScene {

    static volatile Object sink;

    @Override public String name() { return "compat-asm-prefilter"; }

    @Override public String golden() { return "scene-compat-asm-prefilter"; }

    @Override public boolean freshJvm() { return false; }

    @Override public String unit() { return "pass"; }

    @Override public String covers() { return "P-O13-10 ASM 사전 거르기 한 번 훑기, 변환 결과 바이트, T-075"; }

    /** 바늘 후보: 코드의 NEEDLES 와 같은 출처 상수 + 흔한 부분 문자열. 판정은 mayNeed 가 하므로 후보가 틀려도 금 파일이 사실을 적는다. */
    private static List<String> needles() {
        List<String> n = new ArrayList<>(List.of(CraftNames.VERSIONED_INTERNAL, "forName", "loadClass", "findClass", "net/minecraft/", "org/bukkit/craftbukkit/"));
        n.addAll(List.of(ApiRenames.OWNERS));
        return n;
    }

    private static byte[] filler(int len, int seed) {
        byte[] b = new byte[len];
        long x = 0x9E3779B97F4A7C15L * (seed + 1);
        for (int i = 0; i < len; i++) {
            x ^= x << 13; x ^= x >>> 7; x ^= x << 17;
            b[i] = (byte) (x & 0x7f);   // ASCII 범위 — 바늘의 바이트가 우연히 섞여도 판정이 같아야 한다
        }
        return b;
    }

    private static byte[] with(byte[] filler, byte[] needle, int at) {
        byte[] out = filler.clone();
        System.arraycopy(needle, 0, out, at, needle.length);
        return out;
    }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        // (1) 경계 사례
        List<String> ns = needles();
        IdentityHash edge = new IdentityHash();
        int hits = 0, total = 0;
        for (int ni = 0; ni < ns.size(); ni++) {
            byte[] n = ns.get(ni).getBytes(StandardCharsets.UTF_8);
            byte[] shortOne = java.util.Arrays.copyOf(n, n.length - 1);
            byte[] wrongLast = n.clone();
            wrongLast[wrongLast.length - 1] ^= 1;
            byte[] doubledPrefix = new byte[n.length * 2 - 1];   // 접두부를 한 번 더 깔고 바늘이 이어진다: "ab" + "abX..."
            System.arraycopy(n, 0, doubledPrefix, 0, n.length - 1);
            System.arraycopy(n, 0, doubledPrefix, n.length - 1, n.length);
            byte[] f = filler(300, ni);
            byte[][] cases = {
                n, new byte[0], new byte[] {n[0]}, shortOne, wrongLast, doubledPrefix,
                with(f, n, 0), with(f, n, f.length - n.length), with(f, n, 123), with(f, shortOne, 77), with(f, wrongLast, 150), f,
                concat(shortOne, n), concat(n, shortOne), concat(shortOne, shortOne),
            };
            for (int ci = 0; ci < cases.length; ci++) {
                boolean r = NmsClassTransformer.mayNeed(cases[ci]);
                edge.add(ni).add(ci).add(r);
                total++;
                if (r) hits++;
            }
        }
        rec.text("edge needles " + ns.size() + " cases " + total + " hits " + hits);
        rec.line("edge-decisions", edge);

        // (2) 실제 jar 말뭉치
        String prop = System.getProperty("furnace.realworld.jars");
        if (prop == null || prop.isBlank()) throw new AssertionError("furnace.realworld.jars 가 없다 — gradle 로 :furnace-compat-paper:test 를 돌려야 한다(realWorld jar 목록)");
        List<Path> jars = new ArrayList<>();
        for (String p : prop.split(File.pathSeparator)) if (!p.isBlank()) jars.add(Path.of(p));
        jars.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        int passes = size.pick(1, 5);
        for (Path jar : jars) {
            String fileName = jar.getFileName().toString();
            List<String> names = new ArrayList<>();
            List<byte[]> classes = new ArrayList<>();
            try (JarFile jf = new JarFile(jar.toFile())) {
                List<JarEntry> entries = new ArrayList<>();
                for (var en = jf.entries(); en.hasMoreElements();) {
                    JarEntry e = en.nextElement();
                    if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) entries.add(e);
                }
                entries.sort(java.util.Comparator.comparing(JarEntry::getName));
                for (JarEntry e : entries) {
                    try (var in = jf.getInputStream(e)) {
                        names.add(e.getName());
                        classes.add(in.readAllBytes());
                    }
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            for (var opt : new NmsClassTransformer.Options[] {NmsClassTransformer.Options.DEFAULT, new NmsClassTransformer.Options(false, true)}) {
                String variant = opt.versionedPackage() ? "versioned" : "default";
                NmsClassTransformer tr = NmsClassTransformer.forJar("scene-" + fileName, jar, opt);
                IdentityHash h = new IdentityHash();
                int need = 0, changed = 0, refused = 0;
                for (int pass = 0; pass < passes; pass++) {
                    probe.before(pass);
                    need = 0; changed = 0; refused = 0;
                    h = new IdentityHash();
                    for (int i = 0; i < classes.size(); i++) {
                        byte[] b = classes.get(i);
                        boolean may = NmsClassTransformer.mayNeed(b);
                        if (may) need++;
                        byte[] out;
                        String status;
                        try {
                            out = tr.transform(names.get(i).substring(0, names.get(i).length() - 6).replace('/', '.'), b);
                            status = out == null ? "same" : "changed";
                        } catch (RuntimeException e) {
                            out = null;
                            status = "refused:" + e.getClass().getSimpleName();
                        }
                        if (status.equals("changed")) changed++;
                        else if (status.startsWith("refused")) refused++;
                        h.add(names.get(i)).add(may).add(status);
                        if (out != null) h.add(out);
                    }
                    probe.after(pass);
                }
                rec.text("jar " + fileName + " " + variant + " classes=" + classes.size() + " prefilter-hit=" + need + " changed=" + changed + " refused=" + refused + " " + IdentityHash.hex(h.value()));
            }
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
