package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * `--mode sig-gate` (T-228). shim 클래스 폴더를 기준 서명표(바닐라·Paper 서버·라이브러리)와 맞대고 기준선 파일과 견준다.
 * 새 차이가 있거나, 기준선에 적힌 차이가 사라졌거나(낡은 줄), 이유가 비었으면 실패한다.
 *
 * <p>기준선 줄: {@code 코드<TAB>소유 클래스<TAB>멤버<TAB>자세히<TAB>이유}. {@code #} 로 시작하는 줄은 주석.
 * 이 파일은 저장소에 커밋되므로 shim 에 이미 있는 이름과 Furnace 가 쓴 이유만 담는다. 기준(바닐라·Paper) 쪽 이름·서술자는 {@code #<12자리 지문>} 으로만 적고
 * (Mojang 대응표 조건: 완전·미수정 재배포 금지 — 원문은 커밋하지 않는다), 사람이 읽을 원문은 커밋하지 않는 보고서(build/reports/signature-gate.md)에만 나간다.
 */
final class SigGateCommand {
    /** 처음 채울 때의 이유(코드별) — 구멍이므로 생성기(T-229~)가 바로잡으면 줄을 지운다. 의도한 차이로 확인되면 이유를 구체적으로 고쳐 쓴다. */
    static String defaultReason(String code) {
        String what = switch (code) {
            case "super" -> "상위 클래스 사슬이 바닐라와 다르다";
            case "type-missing" -> "바닐라·Paper 가 구현하는 인터페이스·상위가 shim 에 없다";
            case "descriptor" -> "같은 이름 멤버의 서술자(인자·반환형)가 다르다";
            case "class-access" -> "클래스 접근·final·abstract 가 다르다";
            case "class-kind" -> "클래스 종류(인터페이스·열거형·레코드)가 다르다";
            case "member-access" -> "멤버 접근·final 이 다르다";
            case "static" -> "static 여부가 다르다";
            default -> "기준과 다르다";
        };
        return "미분류 — " + what + ". 손 shim 의 알려진 차이, 생성기(T-229~)가 바로잡으면 이 줄은 지운다";
    }
    /** shim 에서 보는 접두: Mojang 이름 NMS·라이브러리·CraftBukkit. */
    static final List<String> SHIM_PREFIXES = List.of("net/minecraft/", "com/mojang/", "org/bukkit/craftbukkit/");

    private SigGateCommand() {}

    static boolean run(Options o) throws IOException {
        if (o.sigShims == null || o.sigBaseline == null) throw new IllegalArgumentException("--sig-shims <클래스 폴더> 와 --sig-baseline <파일> 이 필요하다");
        Path dir = o.cache.resolve(o.mc);
        List<SigTable> refs = new ArrayList<>();
        // 순서가 기준 우선순위다: Paper 서버(바닐라를 포함한 상위 집합) → 바닐라 → 라이브러리
        for (String f : List.of("sig-table-paper-server.tsv.gz", "sig-table.tsv.gz", "sig-table-libs.tsv.gz")) {
            Path p = dir.resolve(f);
            if (!Files.isRegularFile(p)) {
                System.err.println("기준 서명표가 없다: " + p + " (tools/compat/sig-tables.sh " + o.mc + " 로 만든다) — 건너뛰지 않고 실패한다");
                return false;
            }
            refs.add(SigTable.read(p));
        }
        SigTable shim = SigTable.fromDirectory(o.sigShims, SHIM_PREFIXES, "shim");
        SigGate gate = new SigGate(shim, refs);
        List<SigGate.Diff> diffs = gate.compare();
        List<SigGate.Diff> info = gate.info();
        Map<String, String> baseline = readBaseline(o.sigBaseline);
        if (o.sigBaselineWrite) {
            writeBaseline(o.sigBaseline, diffs, baseline);
            System.out.println("[서명 관문] 기준선 " + diffs.size() + "줄 씀 → " + o.sigBaseline);
            return true;
        }
        Result r = judge(diffs, baseline);
        String md = render(shim, diffs, info, r);
        if (o.sigReport != null) {
            Files.createDirectories(o.sigReport.toAbsolutePath().getParent());
            Files.writeString(o.sigReport, md, StandardCharsets.UTF_8);
        }
        long missing = info.stream().filter(d -> d.code().equals("missing-member")).count();
        System.out.println("[서명 관문] shim 클래스 " + shim.classes().size() + "개, 차이 " + diffs.size() + "건(참고: shim 이 더 가진 것 " + (info.size() - missing) + "건, shim 에 빠진 멤버 "
                + missing + "건 — 전체 껍데기 빌드가 껍데기 멤버로 합친다) — 새 차이 " + r.fresh().size()
                + ", 낡은 기준선 " + r.stale().size() + ", 이유 없음 " + r.noReason().size());
        Map<String, String> readable = new java.util.HashMap<>();
        for (SigGate.Diff d : diffs) readable.put(d.key(), d.code() + "\t" + d.owner() + "\t" + d.member() + "\t" + d.readable());
        for (String k : r.fresh()) System.out.println("  새 차이  " + readable.getOrDefault(k, k).replace('\t', ' '));
        for (String k : r.stale()) System.out.println("  낡은 기준선(이제 같다 — 줄을 지운다)  " + k.replace('\t', ' '));
        for (String k : r.noReason()) System.out.println("  이유 없음  " + k.replace('\t', ' '));
        return r.ok();
    }

    record Result(List<String> fresh, List<String> stale, List<String> noReason) {
        boolean ok() {
            return fresh.isEmpty() && stale.isEmpty() && noReason.isEmpty();
        }
    }

    static Result judge(List<SigGate.Diff> diffs, Map<String, String> baseline) {
        List<String> fresh = new ArrayList<>(), stale = new ArrayList<>(), noReason = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SigGate.Diff d : diffs) {
            String k = d.key();
            seen.add(k);
            if (!baseline.containsKey(k)) fresh.add(k);
            else if (baseline.get(k).isBlank()) noReason.add(k);
        }
        for (String k : baseline.keySet()) if (!seen.contains(k)) stale.add(k);
        return new Result(fresh, stale, noReason);
    }

    static Map<String, String> readBaseline(Path file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return out;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f.length < 4) throw new IOException("기준선 줄 형식이 틀렸다(코드·소유·멤버·자세히[·이유], 탭 구분): " + line);
            out.put(String.join("\t", f[0], f[1], f[2], f[3]), f.length > 4 ? f[4] : "");
        }
        return out;
    }

    static void writeBaseline(Path file, List<SigGate.Diff> diffs, Map<String, String> old) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("# 서명 관문 기준선(T-228) — shim 이 기준(바닐라·Paper·라이브러리)과 일부러 다르거나 아직 못 바로잡은 곳.\n");
        b.append("# 기준 쪽 이름·서술자는 #지문(SHA-256 앞 12자리)으로만 적는다(Mojang 이름은 커밋하지 않는다). 원문은 CI 보고서 signature-gate.md 에 나온다.\n");
        b.append("# 줄: 코드<TAB>소유 클래스<TAB>멤버<TAB>자세히<TAB>이유. 새 차이는 여기에 없으면 CI 가 실패하고, 이제 같아진 줄이 남아 있어도 실패한다.\n");
        b.append("# 의도한 차이는 이유에 Furnace 가 그렇게 한 까닭을 쓴다. 이 파일에는 shim 에 이미 있는 이름과 지문만 들어간다.\n");
        Map<String, String> sorted = new TreeMap<>();
        for (SigGate.Diff d : diffs) sorted.put(d.key(), old.getOrDefault(d.key(), "").isBlank() ? defaultReason(d.code()) : old.get(d.key()));
        sorted.forEach((k, v) -> b.append(k).append('\t').append(v).append('\n'));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
    }

    static String render(SigTable shim, List<SigGate.Diff> diffs, List<SigGate.Diff> info, Result r) {
        Map<String, Integer> byCode = new TreeMap<>();
        for (SigGate.Diff d : diffs) byCode.merge(d.code(), 1, Integer::sum);
        StringBuilder b = new StringBuilder("# shim 서명 관문 보고 (자동 생성)\n\nshim 클래스 " + shim.classes().size() + "개 · 차이 " + diffs.size() + "건\n\n| 코드 | 건수 |\n|---|--:|\n");
        byCode.forEach((c, n) -> b.append("| ").append(c).append(" | ").append(n).append(" |\n"));
        Map<String, Integer> byInfo = new TreeMap<>();
        for (SigGate.Diff d : info) byInfo.merge(d.code(), 1, Integer::sum);
        b.append("\n참고(실패 아님 — shim 이 더 가진 것, missing-member 는 shim 에 빠진 것으로 전체 껍데기 빌드가 합친다): ");
        byInfo.forEach((c, n) -> b.append(c).append(' ').append(n).append(" · "));
        b.append('\n');
        b.append("\n## 차이 전체(원문 — 이 보고서는 커밋하지 않는다)\n\n");
        for (SigGate.Diff d : diffs) b.append("- `").append(d.code()).append(' ').append(d.owner()).append(' ').append(d.member()).append("` ").append(d.readable()).append('\n');
        b.append("\n새 차이 ").append(r.fresh().size()).append("건 · 낡은 기준선 ").append(r.stale().size()).append("건 · 이유 없음 ").append(r.noReason().size()).append("건\n");
        for (String k : r.fresh()) b.append("\n- 새 차이 `").append(k.replace('\t', ' ')).append('`');
        for (String k : r.stale()) b.append("\n- 낡은 기준선 `").append(k.replace('\t', ' ')).append('`');
        return b.append('\n').toString();
    }
}
