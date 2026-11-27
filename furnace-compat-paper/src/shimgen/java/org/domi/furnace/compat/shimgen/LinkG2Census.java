// T-263 S3: G2(동작 없는 NMS 멤버 → Furnace CraftBukkit 구현의 공개 메서드) 후보 세기 — 보고서만, 채택 없음(설계 3.2 G2, 3.4).
// 정답기(O2·O3) 중 무엇을 먼저 만들지 정하려고 받는 쪽 종류·읽기/쓰기·필요한 형식 변환별로 센다. 이름이 들어간 표는 build/ 에만.
// 짝은 이름(get/is/has/set 뗀 낱말이 같음)·인자 수·형식 대응(같은 형식 또는 알려진 NMS↔Bukkit 쌍)으로만 찾는다 — 점수일 뿐 증명이 아니다.
// 더해 Paper CraftBukkit 몸통이 그 NMS 멤버를 실제로 부르는지(위임) 나눈다: pure(getHandle·변환·그 멤버 호출뿐), guarded(+ 인자 검사),
// other(그 멤버도 부르지만 다른 NMS 호출·필드 읽기/쓰기도), none(그 멤버를 안 부름 — 이름만 같은 짝).
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class LinkG2Census {
    static final String CRAFT = "org/bukkit/craftbukkit/";

    /** 알려진 형식 쌍(NMS → Bukkit) — 설계 3.2 의 형식 대응 표 후보. 값은 보고서에 쓰는 변환 이름. */
    static final Map<String, String> PAIRS = Map.ofEntries(
            Map.entry("net/minecraft/world/phys/Vec3>org/bukkit/util/Vector", "Vec3-Vector"),
            Map.entry("net/minecraft/resources/ResourceLocation>org/bukkit/NamespacedKey", "ResourceLocation-NamespacedKey"),
            Map.entry("net/minecraft/world/item/ItemStack>org/bukkit/inventory/ItemStack", "ItemStack"),
            Map.entry("net/minecraft/network/chat/Component>net/kyori/adventure/text/Component", "Component-adventure"),
            Map.entry("net/minecraft/world/level/Level>org/bukkit/World", "Level-World"),
            Map.entry("net/minecraft/server/level/ServerLevel>org/bukkit/World", "Level-World"),
            Map.entry("net/minecraft/world/level/block/state/BlockState>org/bukkit/block/data/BlockData", "BlockState-BlockData"),
            Map.entry("net/minecraft/core/BlockPos>org/bukkit/Location", "BlockPos-Location"),
            Map.entry("net/minecraft/world/entity/Entity>org/bukkit/entity/Entity", "Entity"),
            Map.entry("net/minecraft/world/entity/LivingEntity>org/bukkit/entity/LivingEntity", "Entity"),
            Map.entry("net/minecraft/world/entity/player/Player>org/bukkit/entity/Player", "Entity"),
            Map.entry("net/minecraft/server/level/ServerPlayer>org/bukkit/entity/Player", "Entity"));

    record Pair(LinkInfer.Shell m, String craft, String target, boolean write, List<String> conversions, boolean live, String delegation) {}

    final LinkInfer inf;
    final SigTable paper, api;
    /** NMS 클래스 → 그 클래스를 getHandle() 로 돌려주는 CraftBukkit 클래스. */
    final Map<String, String> craftOf = new TreeMap<>();
    final List<Pair> pairs = new ArrayList<>();
    int eligible, withReceiver;

    final java.util.zip.ZipFile paperJar;

    LinkG2Census(LinkInfer inf, java.util.zip.ZipFile paperJar) {
        this.inf = inf;
        this.paperJar = paperJar;
        this.paper = inf.g.paper;
        this.api = inf.g.api;
    }

    void run() {
        for (SigTable.Cls k : new TreeMap<>(paper.classes()).values()) {
            if (!k.name().startsWith(CRAFT) || (k.access() & Opcodes.ACC_ABSTRACT) != 0 && (k.access() & Opcodes.ACC_INTERFACE) != 0) continue;
            for (SigTable.Member mm : paper.members(k.name())) {
                if (mm.kind() != 'M' || !mm.name().equals("getHandle") || !mm.desc().startsWith("()Lnet/minecraft/")) continue;
                if ((mm.access() & Opcodes.ACC_BRIDGE) != 0) continue;
                String nms = Type.getReturnType(mm.desc()).getInternalName();
                craftOf.merge(nms, k.name(), (a, b) -> depth(a) >= depth(b) ? a : b);   // 같은 NMS 를 돌려주면 더 아래(구체) 클래스
            }
        }
        for (LinkInfer.Shell s : inf.shells) {
            int acc = s.access();
            if ((acc & Opcodes.ACC_STATIC) != 0 || s.name().startsWith("<") || !Links.visible(acc) || !inf.bodies.containsKey(s.key())) continue;
            eligible++;
            String craft = receiver(s.owner());
            if (craft == null) continue;
            withReceiver++;
            match(s, craft);
        }
    }

    private int depth(String craft) {
        int d = 0;
        for (String c = craft; c != null && paper.classes().containsKey(c); c = paper.classes().get(c).superName()) d++;
        return d;
    }

    /** NMS 클래스 사슬에서 가장 가까운 CraftBukkit 짝. */
    private String receiver(String nms) {
        for (String c = nms; c != null; c = paper.classes().containsKey(c) ? paper.classes().get(c).superName() : null) {
            String k = craftOf.get(c);
            if (k != null) return k;
        }
        return null;
    }

    private void match(LinkInfer.Shell s, String craft) {
        String want = norm(s.name());
        boolean write = isWrite(s.name());
        Type[] args = Type.getArgumentTypes(s.desc());
        Type ret = Type.getReturnType(s.desc());
        Set<String> seen = new LinkedHashSet<>();
        for (String k = craft; k != null && k.startsWith(CRAFT); k = paper.classes().containsKey(k) ? paper.classes().get(k).superName() : null) {
            for (SigTable.Member b : paper.members(k)) {
                if (b.kind() != 'M' || (b.access() & Opcodes.ACC_PUBLIC) == 0 || (b.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) continue;
                if (b.name().equals("getHandle") || !norm(b.name()).equals(want) || isWrite(b.name()) != write) continue;
                if (!seen.add(b.name() + b.desc())) continue;   // 하위가 재정의한 것을 한 번만
                Type[] bargs = Type.getArgumentTypes(b.desc());
                if (bargs.length != args.length) continue;
                List<String> conv = new ArrayList<>();
                boolean ok = true;
                for (int i = 0; i < args.length && ok; i++) ok = convertible(args[i], bargs[i], conv);
                Type bret = Type.getReturnType(b.desc());
                if (ok) ok = ret.getSort() == Type.VOID ? bret.getSort() == Type.VOID : convertible(ret, bret, conv);
                if (!ok) continue;
                pairs.add(new Pair(s, k, b.name() + b.desc(), write, conv, liveIn(k, b.name(), b.desc()), delegation(k, b.name(), b.desc(), s)));
            }
        }
    }

    /** Paper 몸통이 그 NMS 멤버(받는 쪽 또는 그 조상에 선언된 같은 이름·서술자)를 부르는지와 그 밖에 하는 일. */
    private String delegation(String craft, String name, String desc, LinkInfer.Shell s) {
        byte[] b;
        try {
            java.util.zip.ZipEntry e = paperJar.getEntry(craft + ".class");
            if (e == null) return "none";
            try (var in = paperJar.getInputStream(e)) { b = in.readAllBytes(); }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Set<String> chain = new java.util.HashSet<>();
        for (String c = s.owner(); c != null; c = paper.classes().containsKey(c) ? paper.classes().get(c).superName() : null) chain.add(c);
        boolean[] f = new boolean[3];   // 0 그 멤버 호출, 1 인자 검사, 2 다른 NMS 일
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String d, String sig, String[] ex) {
                if (!n.equals(name) || !d.equals(desc)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
                        if (mn.equals(s.name()) && md.equals(s.desc()) && chain.contains(owner)) f[0] = true;
                        else if (owner.equals("com/google/common/base/Preconditions") || owner.equals("org/apache/commons/lang3/Validate")
                                || owner.equals("org/apache/commons/lang/Validate") || owner.equals("java/util/Objects") && mn.equals("requireNonNull")) f[1] = true;
                        else if (owner.startsWith("net/minecraft/") && !mn.equals("getHandle")) f[2] = true;
                    }

                    @Override
                    public void visitFieldInsn(int op, String owner, String fn, String fd) {
                        if (owner.startsWith("net/minecraft/")) f[2] = true;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG);
        return !f[0] ? "none" : f[2] ? "other" : f[1] ? "guarded" : "pure";
    }

    /** Furnace 호환 계층이 손으로 만든 CraftBukkit 클래스에 그 메서드가 몸통째 있나(껍데기면 아니다). */
    private boolean liveIn(String craft, String name, String desc) {
        if (!inf.g.existing.has(craft)) return false;
        byte[] b = inf.g.existing.bytesOf(craft);
        if (b == null) return false;
        boolean[] found = {false, false};   // 0 몸통 있음, 1 "아직 지원 안 함"을 던짐(손 Craft 의 던지는 자리 — 살아 있지 않다)
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String d, String sig, String[] ex) {
                if (!n.equals(name) || !d.equals(desc) || (access & Opcodes.ACC_ABSTRACT) != 0) return null;
                found[0] = true;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int op, String type) {
                        if (op == Opcodes.NEW && type.startsWith("org/domi/furnace/") && type.contains("Unsupported")) found[1] = true;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG);
        return found[0] && !found[1];
    }

    private boolean convertible(Type nms, Type bukkit, List<String> conv) {
        if (nms.equals(bukkit)) return true;
        if (nms.getSort() != Type.OBJECT || bukkit.getSort() != Type.OBJECT) return false;
        String key = nms.getInternalName() + ">" + bukkit.getInternalName();
        String p = PAIRS.get(key);
        if (p == null && isEnum(paper, nms.getInternalName()) && isEnum(api, bukkit.getInternalName())) p = "enum";   // 엔티티 안 열거형(Parrot$Variant)이 먼저
        if (p == null && nms.getInternalName().startsWith("net/minecraft/world/entity/") && bukkit.getInternalName().startsWith("org/bukkit/entity/")) p = "Entity";
        if (p == null) return false;
        conv.add(p);
        return true;
    }

    private static boolean isEnum(SigTable t, String n) {
        SigTable.Cls c = t.classes().get(n);
        return c != null && (c.access() & Opcodes.ACC_ENUM) != 0;
    }

    static boolean isWrite(String n) { return n.startsWith("set") && n.length() > 3 && Character.isUpperCase(n.charAt(3)); }

    /** get/is/has/set 를 떼고 소문자로(이름 짝은 이것이 같을 때만). */
    static String norm(String n) {
        for (String p : new String[] {"get", "is", "has", "set"}) {
            if (n.startsWith(p) && n.length() > p.length() && Character.isUpperCase(n.charAt(p.length()))) return n.substring(p.length()).toLowerCase(Locale.ROOT);
        }
        return n.toLowerCase(Locale.ROOT);
    }

    /** 숫자 보고서(이름 없음)와 이름 들어간 후보 표(build/ 만). */
    void write(Path out) throws IOException {
        Files.createDirectories(out);
        Map<String, int[]> byRoot = new TreeMap<>(), byConv = new TreeMap<>(), byDeleg = new TreeMap<>();
        Set<String> members = new LinkedHashSet<>(), liveMembers = new LinkedHashSet<>(), ambiguous = new LinkedHashSet<>();
        Map<String, Integer> perMember = new HashMap<>();
        for (Pair p : pairs) perMember.merge(p.m().key(), 1, Integer::sum);
        StringBuilder tsv = new StringBuilder("member\tcraft target\twrite\tconversions\tfurnace has it\tdelegation\n");
        for (Pair p : pairs) {
            String root = root(p.m().owner());
            int[] r = byRoot.computeIfAbsent(root, x -> new int[4]);
            if (members.add(p.m().key())) {
                r[p.write() ? 1 : 0]++;
                if (perMember.get(p.m().key()) > 1) ambiguous.add(p.m().key());
            }
            if (p.live() && liveMembers.add(p.m().key())) r[p.write() ? 3 : 2]++;
            int[] dl = byDeleg.computeIfAbsent(p.delegation(), x -> new int[4]);
            dl[p.write() ? 1 : 0]++;
            if (p.live()) dl[p.write() ? 3 : 2]++;
            for (String c : p.conversions().isEmpty() ? List.of("(none)") : p.conversions()) byConv.computeIfAbsent(c, x -> new int[1])[0]++;
            tsv.append(p.m().key()).append('\t').append(p.craft()).append('#').append(p.target()).append('\t').append(p.write())
                    .append('\t').append(String.join(",", p.conversions())).append('\t').append(p.live()).append('\t').append(p.delegation()).append('\n');
        }
        Files.writeString(out.resolve("g2-candidates.tsv"), tsv.toString(), StandardCharsets.UTF_8);
        StringBuilder md = new StringBuilder("# T-263 G2 census (numbers only - report, nothing adopted)\n\n");
        md.append("throwing public/protected instance methods with a vanilla body: ").append(eligible)
                .append(", with a CraftBukkit receiver (getHandle chain): ").append(withReceiver).append('\n');
        md.append("members with a name/arity/type pair: ").append(members.size()).append(" (more than one pair: ").append(ambiguous.size())
                .append("), pairs: ").append(pairs.size()).append(", members whose pair Furnace already implements: ").append(liveMembers.size()).append("\n\n");
        md.append("| receiver root | read members | write members | read, Furnace has it | write, Furnace has it |\n|---|---:|---:|---:|---:|\n");
        byRoot.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v[0]).append(" | ").append(v[1]).append(" | ").append(v[2]).append(" | ").append(v[3]).append(" |\n"));
        md.append("\n| Paper body delegates to the member (per pair) | read | write | read, Furnace has it | write, Furnace has it |\n|---|---:|---:|---:|---:|\n");
        byDeleg.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v[0]).append(" | ").append(v[1]).append(" | ").append(v[2]).append(" | ").append(v[3]).append(" |\n"));
        md.append("\n| conversion needed (per pair) | pairs |\n|---|---:|\n");
        byConv.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v[0]).append(" |\n"));
        Files.writeString(out.resolve("g2-census.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println("[link-infer] G2 census: " + members.size() + " members paired (" + liveMembers.size() + " to a method Furnace has) -> " + out.resolve("g2-census.md"));
    }

    /** 받는 쪽 큰 갈래(NMS 패키지 앞부분). */
    static String root(String owner) {
        String[] p = owner.split("/");
        return p.length > 4 ? p[2] + "/" + p[3] : owner.substring(0, owner.lastIndexOf('/'));
    }
}
