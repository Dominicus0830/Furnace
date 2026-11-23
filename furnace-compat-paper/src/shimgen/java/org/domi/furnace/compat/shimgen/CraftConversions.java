// T-264 P4 변환 가족 W/U(설계 3.5·7장 c 칸): Paper CraftBukkit 클래스의 레지스트리 값 변환 정적 메서드를 서명 규칙으로 찾아 빌드 때 몸통을 낸다
// (커밋하는 명세 줄 없음). 규칙 — CraftBukkit 클래스의 인자 하나짜리 정적 메서드로
//   minecraftToBukkit(M)B · bukkitToMinecraft(B)M · minecraftHolderToBukkit(Holder<M>)B · bukkitToMinecraftHolder(B)Holder<M>
// 꼴이고 B 가 Bukkit API 형(org/bukkit/, CraftBukkit 아님), M 이 NMS 형이며 Paper Registries 에 ResourceKey<Registry<M>> 필드가 하나뿐인 것.
// 몸통은 그 Registries 필드와 B 를 실행 도우미 CraftConvert 에 넘긴다(같은 키의 값을 반대쪽 레지스트리에서 찾는다 — Paper CraftRegistry 길).
// 예외 종류·문구는 Paper 가 클래스마다 다르게 쓴 것(제 몸통인지 CraftRegistry 로 넘기는지, 홀더 문구 꼬리)을 DIRECT·HOLDER_TAIL 표로 맞춘다.
// 표지 @CraftBridge(진짜 동작). 레지스트리를 Furnace 가 아직 주지 않으면 실행 때 껍데기와 같은 UnsupportedNmsCall — 레지스트리 영역이 채우면 저절로 산다.
// 이름 꼴은 맞으나 Paper 몸통이 레지스트리 길이 아닌 것은 내지 않는다(EXCLUDED — 손 몫, 이유는 보고서에).
package org.domi.furnace.compat.shimgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class CraftConversions {
    static final String HELPER = "org/domi/furnace/compat/nms/CraftConvert";
    static final String REGISTRIES = "net/minecraft/core/registries/Registries";
    private static final String HOLDER = "net/minecraft/core/Holder";
    private static final String RESOURCE_KEY_DESC = "Lnet/minecraft/resources/ResourceKey;";
    private static final String CRAFT_PREFIX = "org/bukkit/craftbukkit/";

    /** 이름 꼴은 맞지만 Paper 몸통이 "같은 키" 길이 아닌 클래스 → 이유. 손으로 채운다(영역 10). */
    static final Map<String, String> EXCLUDED = Map.of(
            "org/bukkit/craftbukkit/block/CraftBiome", "Paper maps unregistered biomes to Biome.CUSTOM and CUSTOM to null",
            "org/bukkit/craftbukkit/entity/memory/CraftMemoryKey", "Paper returns null for unknown memory keys instead of throwing");
    /** Bukkit 형이 이것이면 내지 않는다 → 이유. */
    static final Map<String, String> EXCLUDED_BUKKIT = Map.of(
            "org/bukkit/Material", "Material conversions go through CraftMagicNumbers (null for a material that is not a block/item)");

    /**
     * Paper 1.21.1 에서 minecraftToBukkit 을 CraftRegistry 로 넘기지 않고 제 몸통으로 바꾸는 클래스(등록 안 된 NMS 값이 IllegalStateException 이
     * 아니라 NoSuchElementException). 11-23 Paper 1.21.1 서버(userdev) 바이트코드 대조. 여기에 없는 클래스는 CraftRegistry 길.
     */
    static final Set<String> DIRECT = Set.of(
            "org/bukkit/craftbukkit/CraftArt", "org/bukkit/craftbukkit/CraftFluid", "org/bukkit/craftbukkit/CraftParticle",
            "org/bukkit/craftbukkit/attribute/CraftAttribute", "org/bukkit/craftbukkit/entity/CraftEntityType", "org/bukkit/craftbukkit/potion/CraftPotionType");
    /**
     * bukkitToMinecraftHolder 를 제 몸통으로 쓰는 클래스의 예외 문구 꼬리("No Reference holder found for " + 값 뒤) — Paper 가 클래스마다 베껴 쓴 문구
     * 그대로(오타·다른 종류 이름 포함). 같은 대조. 여기에 없는 클래스는 CraftRegistry 의 꼬리({@code CraftConvert.REGISTRY_HOLDER_TAIL}).
     */
    static final Map<String, String> HOLDER_TAIL = Map.of(
            "org/bukkit/craftbukkit/CraftArt", tail("painting variant", true),
            "org/bukkit/craftbukkit/CraftJukeboxSong", tail("trim pattern", false),
            "org/bukkit/craftbukkit/CraftMusicInstrument", tail("instrument", false),
            "org/bukkit/craftbukkit/attribute/CraftAttribute", tail("sound effect", true),
            "org/bukkit/craftbukkit/block/banner/CraftPatternType", tail("banner pattern", false),
            "org/bukkit/craftbukkit/damage/CraftDamageType", tail("damage type", true),
            "org/bukkit/craftbukkit/entity/CraftWolf$CraftVariant", tail("wolf variant", true),
            "org/bukkit/craftbukkit/inventory/trim/CraftTrimMaterial", tail("trim material", false),
            "org/bukkit/craftbukkit/inventory/trim/CraftTrimPattern", tail("trim pattern", false),
            "org/bukkit/craftbukkit/potion/CraftPotionType", tail("sound effect", true));
    static final String REGISTRY_HOLDER_TAIL = tail("registry entry", true);

    private static String tail(String what, boolean spaced) {
        return ", this can happen if a plugin creates its own " + what + (spaced ? " with out" : " without") + " properly registering it.";
    }

    enum Kind {
        MINECRAFT_TO_BUKKIT("minecraftToBukkit"), BUKKIT_TO_MINECRAFT("bukkitToMinecraft"),
        MINECRAFT_HOLDER_TO_BUKKIT("minecraftHolderToBukkit"), BUKKIT_TO_MINECRAFT_HOLDER("bukkitToMinecraftHolder");

        final String method;

        Kind(String method) { this.method = method; }

        static Kind of(String name) {
            for (Kind k : values()) if (k.method.equals(name)) return k;
            return null;
        }
    }

    /** 낼 변환 하나: Registries 필드 이름, Bukkit 형, NMS 형(지운 형). */
    record Conv(Kind kind, String field, String bukkit, String minecraft) {}

    /** NMS 형(지운 형) → Registries 필드(둘 이상이면 ""). */
    private final Map<String, String> fieldOf = new HashMap<>();
    /** 낸 변환(소유#이름서술자 — 역할 표의 c 칸). */
    final Set<String> converted = new TreeSet<>();
    /** 이름 꼴은 맞으나 내지 않은 것(이유 → 목록). */
    final Map<String, List<String>> skipped = new TreeMap<>();

    CraftConversions(SigTable paper) {
        for (SigTable.Member f : paper.members(REGISTRIES)) {
            if (f.kind() != 'F' || (f.access() & Opcodes.ACC_STATIC) == 0 || !f.desc().equals(RESOURCE_KEY_DESC)) continue;
            String m = typeArg(f.signature(), "Lnet/minecraft/core/Registry<");
            if (m != null) fieldOf.merge(m, f.name(), (a, b) -> "");
        }
    }

    /** m 이 변환 가족이면 Conv(내지 않을 이유가 있으면 skipped 에 적고 null). */
    Conv match(String owner, SigTable.Member m) {
        if (!owner.startsWith(CRAFT_PREFIX) || m.kind() != 'M' || (m.access() & Opcodes.ACC_STATIC) == 0) return null;
        Kind kind = Kind.of(m.name());
        if (kind == null) return null;
        Type[] args = Type.getArgumentTypes(m.desc());
        Type ret = Type.getReturnType(m.desc());
        if (args.length != 1 || args[0].getSort() != Type.OBJECT || ret.getSort() != Type.OBJECT) return null;
        String what = owner + "#" + m.name() + m.desc();
        String bukkit;
        String minecraft;
        switch (kind) {
            case MINECRAFT_TO_BUKKIT -> { minecraft = args[0].getInternalName(); bukkit = ret.getInternalName(); }
            case BUKKIT_TO_MINECRAFT -> { bukkit = args[0].getInternalName(); minecraft = ret.getInternalName(); }
            case MINECRAFT_HOLDER_TO_BUKKIT -> {
                bukkit = ret.getInternalName();
                minecraft = args[0].getInternalName().equals(HOLDER) ? typeArg(m.signature(), "Lnet/minecraft/core/Holder<") : null;
            }
            default -> {
                bukkit = args[0].getInternalName();
                minecraft = ret.getInternalName().equals(HOLDER) ? typeArg(m.signature(), "Lnet/minecraft/core/Holder<") : null;
            }
        }
        if (minecraft == null || !minecraft.startsWith("net/minecraft/") || minecraft.startsWith("net/minecraft/resources/")
                || !bukkit.startsWith("org/bukkit/") || bukkit.startsWith(CRAFT_PREFIX)) {
            skip("not a registry value conversion (key, location or non-API value)", what);
            return null;
        }
        String why = EXCLUDED.get(owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner);
        if (why == null) why = EXCLUDED_BUKKIT.get(bukkit);
        if (why != null) {
            skip(why, what);
            return null;
        }
        String field = fieldOf.get(minecraft);
        if (field == null || field.isEmpty()) {
            skip(field == null ? "no Registries field for the Minecraft type" : "more than one Registries field for the Minecraft type", what);
            return null;
        }
        converted.add(what);
        return new Conv(kind, field, bukkit, minecraft);
    }

    /** 열린 메서드 mv 에 변환 몸통을 쓴다(가지 없는 곧은 코드 — 프레임 없음). */
    static void body(MethodVisitor mv, String owner, SigTable.Member m, Conv c) {
        mv.visitAnnotation(ShellMerge.CRAFT_BRIDGE, false).visitEnd();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETSTATIC, REGISTRIES, c.field(), RESOURCE_KEY_DESC);
        boolean toBukkit = c.kind() == Kind.MINECRAFT_TO_BUKKIT || c.kind() == Kind.MINECRAFT_HOLDER_TO_BUKKIT;
        if (toBukkit) {
            mv.visitLdcInsn(Type.getObjectType(c.bukkit()));
            mv.visitInsn(DIRECT.contains(owner) ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
        }
        if (c.kind() == Kind.BUKKIT_TO_MINECRAFT_HOLDER) mv.visitLdcInsn(HOLDER_TAIL.getOrDefault(owner, REGISTRY_HOLDER_TAIL));
        mv.visitLdcInsn(owner.replace('/', '.') + "#" + m.name() + m.desc());
        String desc = switch (c.kind()) {
            case MINECRAFT_TO_BUKKIT -> "(Ljava/lang/Object;" + RESOURCE_KEY_DESC + "Ljava/lang/Class;ZLjava/lang/String;)Ljava/lang/Object;";
            case MINECRAFT_HOLDER_TO_BUKKIT -> "(L" + HOLDER + ";" + RESOURCE_KEY_DESC + "Ljava/lang/Class;ZLjava/lang/String;)Ljava/lang/Object;";
            case BUKKIT_TO_MINECRAFT -> "(Ljava/lang/Object;" + RESOURCE_KEY_DESC + "Ljava/lang/String;)Ljava/lang/Object;";
            case BUKKIT_TO_MINECRAFT_HOLDER -> "(Ljava/lang/Object;" + RESOURCE_KEY_DESC + "Ljava/lang/String;Ljava/lang/String;)L" + HOLDER + ";";
        };
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, c.kind().method, desc, false);
        String ret = Type.getReturnType(m.desc()).getInternalName();
        if (!ret.equals(HOLDER)) mv.visitTypeInsn(Opcodes.CHECKCAST, ret);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(toBukkit ? 5 : c.kind() == Kind.BUKKIT_TO_MINECRAFT_HOLDER ? 4 : 3, 1);
        mv.visitEnd();
    }

    private void skip(String why, String what) { skipped.computeIfAbsent(why, k -> new ArrayList<>()).add(what); }

    /** 일반 서명에서 prefix 뒤 첫 형 인자의 지운 형(예: Holder&lt;MenuType&lt;*&gt;&gt; → MenuType). 형 변수·와일드카드면 null. */
    static String typeArg(String signature, String prefix) {
        if (signature == null || signature.equals("-")) return null;
        int i = signature.indexOf(prefix);
        if (i < 0) return null;
        i += prefix.length();
        if (i >= signature.length() || signature.charAt(i) != 'L') return null;
        int j = i + 1;
        while (j < signature.length() && signature.charAt(j) != '<' && signature.charAt(j) != ';') j++;
        return signature.substring(i + 1, j);
    }

    void report(StringBuilder sb) {
        sb.append("\n## conversion family W/U (T-264 P4)\n\n").append(converted.size()).append(" registry value conversions get a real body (CraftConvert, @CraftBridge).\n\n");
        for (String c : converted) sb.append("- ").append(c).append('\n');
        for (Map.Entry<String, List<String>> e : skipped.entrySet()) {
            sb.append("\nnot converted - ").append(e.getKey()).append(" (").append(e.getValue().size()).append("):\n");
            for (String w : e.getValue()) sb.append("- ").append(w).append('\n');
        }
    }

    String summary() { return "conversions " + converted.size() + " (not converted " + skipped.values().stream().mapToInt(List::size).sum() + ")"; }
}
