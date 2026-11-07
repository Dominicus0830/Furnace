package org.domi.furnace.compat.realworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * H5: 실제 jar 가 서버 구현 내부(`net.minecraft`·`org.bukkit.craftbukkit`)에 거는 참조를 센다.
 *
 * <p>H4 의 {@link Coverage} 는 Bukkit 계열 API 를 호환 계층과 견줬다. 여기서는 견줄 대상이 없다(NMS·CraftBukkit 클래스는 이 저장소에
 * 없다) — 대신 **무엇을 얼마나 부르는지**(호출 빈도), 어느 이름 체계(Spigot 난독화·Mojang 이름·버전 접미사)로 부르는지, 리플렉션 문자열로
 * 가린 접근이 얼마인지를 센다. 결과는 H6·H7 의 첫 구현 범위를 정하는 근거다.
 *
 * <p>정적 분석이라 요구 상한이고, 리플렉션 문자열로 조립한 이름은 하한으로만 보인다.
 */
public final class NmsScan {

    private NmsScan() {}

    public static final Pattern CB_VERSIONED = Pattern.compile("^org/bukkit/craftbukkit/(v1_\\d+_R\\d+)/(.*)$");
    public static final Pattern NMS_LEGACY = Pattern.compile("^net/minecraft/server/(v1_\\d+_R\\d+)/(.*)$");
    private static final Pattern VERSION_TOKEN = Pattern.compile("v1_\\d+_R\\d+");

    /** 참조 대상의 갈래. */
    public enum Group {
        /** {@code org.bukkit.craftbukkit.*}(버전 접미사 유무 불문). */
        CRAFTBUKKIT,
        /** 1.17 이전의 {@code net.minecraft.server.v1_X_RN.*}(패키지 이름에 버전). */
        NMS_LEGACY,
        /** {@code net.minecraft.*}(1.17 이후 패키지 구조, Spigot 난독화 이름이거나 Mojang 이름). */
        NMS,
        AUTHLIB,
        SERIALIZATION,
        BRIGADIER,
        NETTY
    }

    public static Group groupOf(String internal) {
        if (internal.startsWith("org/bukkit/craftbukkit/")) return Group.CRAFTBUKKIT;
        if (NMS_LEGACY.matcher(internal).matches()) return Group.NMS_LEGACY;
        if (internal.startsWith("net/minecraft/")) return Group.NMS;
        if (internal.startsWith("com/mojang/authlib/")) return Group.AUTHLIB;
        if (internal.startsWith("com/mojang/serialization/") || internal.startsWith("com/mojang/datafixers/")) return Group.SERIALIZATION;
        if (internal.startsWith("com/mojang/brigadier/")) return Group.BRIGADIER;
        if (internal.startsWith("io/netty/")) return Group.NETTY;
        return null;
    }

    /** 버전 접미사 패키지 조각을 지운 이름({@code org/bukkit/craftbukkit/v1_21_R1/entity/CraftPlayer} → {@code .../craftbukkit/entity/CraftPlayer}). */
    public static String normalize(String internal) {
        var m = CB_VERSIONED.matcher(internal);
        if (m.matches()) return "org/bukkit/craftbukkit/" + m.group(2);
        m = NMS_LEGACY.matcher(internal);
        if (m.matches()) return "net/minecraft/server/" + m.group(2);
        return internal;
    }

    /** 이름에 든 버전 접미사({@code v1_21_R1}), 없으면 {@code null}. */
    public static String versionOf(String internal) {
        var m = CB_VERSIONED.matcher(internal);
        if (m.matches()) return m.group(1);
        m = NMS_LEGACY.matcher(internal);
        if (m.matches()) return m.group(1);
        return null;
    }

    private static final Pattern SPIGOT_LIKE = Pattern.compile(
            "Packet(Play|Status|Login|Handshaking)(In|Out)\\w*|IChat\\w+|ChatComponent\\w+|NBTTag\\w+|NBTBase|BlockPosition|WorldServer|PlayerConnection"
                    + "|NetworkManager|EntityPlayer|EntityHuman|EntityLiving|EntityInsentient|MinecraftKey|IBlockData|IRegistry\\w*|CommandListenerWrapper"
                    + "|DataWatcher\\w*|ICommandListener|ChunkCoordIntPair|IInventory|Enum[A-Z]\\w+|PlayerInteractManager");
    private static final Pattern MOJANG_LIKE = Pattern.compile(
            "(Clientbound|Serverbound)\\w*Packet|ServerPlayer|ServerLevel|ServerGamePacketListenerImpl|BlockPos|CompoundTag|ListTag|ResourceLocation"
                    + "|CommandSourceStack|SynchedEntityData|FriendlyByteBuf|Connection|ServerPlayerGameMode|LivingEntity|PathfinderMob");

    public enum Style {
        SPIGOT, MOJANG, OTHER
    }

    /** 단순 이름 모양으로 본 이름 체계 표시(어림). 두 체계에서 같은 이름({@code ItemStack}·{@code Entity}·{@code Block})은 OTHER. */
    public static Style styleOf(String internal) {
        String simple = internal.substring(internal.lastIndexOf('/') + 1);
        int dollar = simple.indexOf('$');
        if (dollar > 0) simple = simple.substring(0, dollar);
        if (SPIGOT_LIKE.matcher(simple).matches()) return Style.SPIGOT;
        if (MOJANG_LIKE.matcher(simple).matches()) return Style.MOJANG;
        return Style.OTHER;
    }

    private static final Set<String> READABLE_SHORT = Set.of("x", "y", "z", "id", "of", "at", "is", "to", "w", "h", "uv");
    private static final Pattern OBF_NAME = Pattern.compile("[a-z]{1,2}\\d{0,2}");
    private static final Pattern OBF_INNER = Pattern.compile("[a-z]{1,2}\\d?");

    /** 난독화처럼 보이는 멤버 이름(1~2 글자 소문자). */
    public static boolean obfMember(String name) {
        return !READABLE_SHORT.contains(name) && OBF_NAME.matcher(name).matches();
    }

    /** 내부 클래스 이름이 {@code $a}·{@code $b} 꼴이면 난독화로 본다. */
    public static boolean obfClass(String internal) {
        int d = internal.lastIndexOf('$');
        return d > 0 && OBF_INNER.matcher(internal.substring(d + 1)).matches();
    }

    private static final Set<String> BRIDGE_LITERALS = Set.of(
            "getHandle", "asNMSCopy", "asBukkitCopy", "asCraftMirror", "getBukkitEntity", "getServer", "getMinecraftServer", "playerConnection",
            "connection", "networkManager", "channel", "sendPacket", "send", "getProfile", "getGameProfile", "pipeline", "packet_handler");

    private static final Set<String> REFLECT_CLASS = Set.of(
            "forName", "getMethod", "getDeclaredMethod", "getField", "getDeclaredField", "getConstructor", "getDeclaredConstructor",
            "getMethods", "getDeclaredMethods", "getFields", "getDeclaredFields", "getDeclaredConstructors");

    /** jar 하나의 측정값. */
    public static final class JarScan {
        public final String jar;
        public final boolean plugin;
        public String manifestNamespace;
        public int classCount;
        /** 정규화한 이름(버전 접미사 제거) → 그 이름을 쓴 갈래. */
        public final Map<Group, Set<String>> classes = new LinkedHashMap<>();
        /** 정규화한 {@code owner#이름}(오버로드·서명은 합친다) → 같은 갈래. */
        public final Map<Group, Set<String>> members = new LinkedHashMap<>();
        public final Set<String> versions = new TreeSet<>();
        public int obfMembers;
        public int readableMembers;
        public int spigotClasses;
        public int mojangClasses;
        public int obfInnerClasses;
        public int reflectionCalls;
        public final Set<String> nameLiterals = new TreeSet<>();
        public final Set<String> fragmentLiterals = new TreeSet<>();
        public final Set<String> bridgeLiterals = new TreeSet<>();
        public boolean usesPackageNameForVersion;
        /** 이 jar 의 클래스들이 NMS·CraftBukkit 이름을 붙들고 있는가(직접이든 문자열이든). */
        public boolean touchesServerInternals() {
            return !classes.getOrDefault(Group.CRAFTBUKKIT, Set.of()).isEmpty() || !classes.getOrDefault(Group.NMS, Set.of()).isEmpty()
                    || !classes.getOrDefault(Group.NMS_LEGACY, Set.of()).isEmpty() || !nameLiterals.isEmpty() || !fragmentLiterals.isEmpty();
        }

        JarScan(String jar, boolean plugin) {
            this.jar = jar;
            this.plugin = plugin;
        }

        public Set<String> classes(Group g) {
            return classes.getOrDefault(g, Set.of());
        }

        public Set<String> members(Group g) {
            return members.getOrDefault(g, Set.of());
        }

        /** 이름 체계 어림: 난독화 멤버·클래스가 읽히는 이름보다 많으면 Spigot, 아니면 Mojang 쪽 표시가 많으면 Mojang. */
        public String namespaceGuess() {
            int nmsClasses = classes(Group.NMS).size() + classes(Group.NMS_LEGACY).size();
            if (nmsClasses == 0) return "—";
            int obf = obfMembers + obfInnerClasses;
            boolean spigot = obf > readableMembers || spigotClasses > mojangClasses;
            boolean mojang = mojangClasses > spigotClasses || (obf < readableMembers && obf * 4 < readableMembers);
            if (spigot && mojang) return "섞임";
            if (spigot) return "Spigot(난독화)";
            if (mojang) return "Mojang";
            return "불명";
        }
    }

    public static List<JarScan> scan(List<Path> jars) {
        List<JarScan> out = new ArrayList<>();
        for (Path jar : jars) {
            try {
                out.add(scanJar(jar));
            } catch (IOException e) {
                throw new UncheckedIOException(jar.toString(), e);
            }
        }
        return out;
    }

    static JarScan scanJar(Path jar) throws IOException {
        JarScan r = new JarScan(jar.getFileName().toString(), RealJars.pluginName(jar) != null);
        try (JarFile jf = new JarFile(jar.toFile())) {
            if (jf.getManifest() != null) {
                r.manifestNamespace = jf.getManifest().getMainAttributes().getValue("paperweight-mappings-namespace");
            }
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.endsWith("module-info.class")) continue;
                try (InputStream in = jf.getInputStream(e)) {
                    addClass(r, ClassRefs.parse(in.readAllBytes()));
                } catch (IOException | RuntimeException ex) {
                    // 읽을 수 없는 클래스(예: 아주 새 클래스 파일 형식)는 건너뛴다 — 측정은 상한이므로 건너뛴 만큼 낮게 나온다
                }
            }
        }
        return r;
    }

    /** 클래스 하나를 jar 측정값에 더한다(시험이 합성 클래스로 부르기도 한다). */
    static void addClass(JarScan r, ClassRefs c) {
        r.classCount++;
        for (String name : c.classes) {
            Group g = groupOf(name);
            if (g == null) continue;
            String norm = normalize(name);
            if (r.classes.computeIfAbsent(g, k -> new TreeSet<>()).add(norm)) {
                if (g == Group.NMS || g == Group.NMS_LEGACY) {
                    switch (styleOf(norm)) {
                        case SPIGOT -> r.spigotClasses++;
                        case MOJANG -> r.mojangClasses++;
                        default -> {
                        }
                    }
                    if (obfClass(norm)) r.obfInnerClasses++;
                }
            }
            String v = versionOf(name);
            if (v != null) r.versions.add(v);
        }
        boolean hasGetPackage = false;
        boolean hasPackageGetName = false;
        for (ClassRefs.Member m : c.members) {
            Group g = groupOf(m.owner());
            if (g != null) {
                String key = normalize(m.owner()) + "#" + m.name();
                if (r.members.computeIfAbsent(g, k -> new TreeSet<>()).add(key) && (g == Group.NMS || g == Group.NMS_LEGACY)
                        && !m.name().startsWith("<")) {
                    if (obfMember(m.name())) r.obfMembers++;
                    else r.readableMembers++;
                }
            }
            switch (m.owner()) {
                case "java/lang/Class" -> {
                    if (REFLECT_CLASS.contains(m.name())) r.reflectionCalls++;
                    if (m.name().equals("getPackage")) hasGetPackage = true;
                }
                case "java/lang/reflect/Method" -> {
                    if (m.name().equals("invoke") || m.name().equals("setAccessible")) r.reflectionCalls++;
                }
                case "java/lang/reflect/Field" -> {
                    if (m.name().startsWith("get") || m.name().startsWith("set")) r.reflectionCalls++;
                }
                case "java/lang/Package" -> {
                    if (m.name().equals("getName")) hasPackageGetName = true;
                }
                case "java/lang/invoke/MethodHandles$Lookup" -> {
                    if (m.name().startsWith("find")) r.reflectionCalls++;
                }
                default -> {
                }
            }
        }
        if (hasGetPackage && hasPackageGetName) r.usesPackageNameForVersion = true;
        for (String s : c.strings) {
            if (s.startsWith("net.minecraft.") || s.startsWith("org.bukkit.craftbukkit") || s.startsWith("net/minecraft/")
                    || s.startsWith("org/bukkit/craftbukkit") || s.startsWith("com.mojang.authlib") || s.startsWith("com.mojang.brigadier")
                    || s.startsWith("com.mojang.serialization")) {
                // 자바 9+ 의 문자열 이어붙이기 레시피({@code a.b.\u0001.c})와 옛 StringBuilder 조각({@code a.b.})은 둘 다 "조각"이다
                if (s.endsWith(".") || s.endsWith("/") || s.indexOf('\u0001') >= 0) r.fragmentLiterals.add(s.replace('\u0001', '*'));
                else r.nameLiterals.add(s);
            } else if ((s.contains("craftbukkit.") || s.contains("net.minecraft.server.")) && (s.endsWith(".") || s.indexOf('\u0001') >= 0)) {
                r.fragmentLiterals.add(s.replace('\u0001', '*'));
            }
            if (VERSION_TOKEN.matcher(s).find() && s.length() < 64) r.versions.add(VERSION_TOKEN.matcher(s).results().findFirst().get().group());
            if (BRIDGE_LITERALS.contains(s)) r.bridgeLiterals.add(s);
        }
    }

    /** 합집합 집계: 이름 → 그 이름을 쓴 jar 수. */
    public static Map<String, Integer> jarCounts(List<JarScan> scans, Group g) {
        Map<String, Integer> out = new TreeMap<>();
        for (JarScan s : scans) for (String c : s.classes(g)) out.merge(c, 1, Integer::sum);
        return out;
    }

    /** 합집합 집계: 소유 클래스(정규화) → jar 별 서로 다른 멤버 참조의 합. */
    public static Map<String, Integer> memberRefsByOwner(List<JarScan> scans, Group g) {
        Map<String, Integer> out = new TreeMap<>();
        for (JarScan s : scans) {
            for (String key : s.members(g)) {
                int hash = key.indexOf('#');
                out.merge(key.substring(0, hash), 1, Integer::sum);
            }
        }
        return out;
    }

    /** 합집합 집계: {@code owner#이름} → 그 멤버를 참조한 jar 수. */
    public static Map<String, Integer> memberJarCounts(List<JarScan> scans, Group g) {
        Map<String, Integer> out = new TreeMap<>();
        for (JarScan s : scans) for (String key : s.members(g)) out.merge(key, 1, Integer::sum);
        return out;
    }

    static Set<String> literalUnion(List<JarScan> scans, java.util.function.Function<JarScan, Set<String>> f) {
        Set<String> out = new LinkedHashSet<>();
        for (JarScan s : scans) out.addAll(f.apply(s));
        return out;
    }
}
