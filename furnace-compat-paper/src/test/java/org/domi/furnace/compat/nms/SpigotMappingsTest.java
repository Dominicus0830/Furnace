package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** H7d: Spigot → Mojang 대응표 읽기와 이름 찾기(가짜 표, {@link SpigotMappingsFixture}). */
class SpigotMappingsTest {

    private static final Function<String, List<String>> NO_SUPERS = c -> List.of();

    @Test
    void classesMapThroughObfuscatedNames() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        assertEquals("net/minecraft/server/level/ServerPlayer", m.mapClass("net/minecraft/server/level/EntityPlayer"));
        assertEquals("net/minecraft/core/BlockPos$MutableBlockPos", m.mapClass("net/minecraft/core/BlockPosition$MutableBlockPosition"));
        assertNull(m.mapClass("net/minecraft/server/level/ServerPlayer"), "Mojang 이름은 Spigot 이름이 아니다");
        assertNull(m.mapClass("org/bukkit/craftbukkit/CraftServer"));
        assertEquals("net.minecraft.world.level.Level", m.mapClassName("net.minecraft.world.level.World"));
    }

    @Test
    void methodsResolveOnTheOwnerAndThroughSpigotRenames() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        assertEquals("getX", m.mapMethod("net/minecraft/core/BaseBlockPosition", "u", "()I", NO_SUPERS));
        assertEquals("getY", m.mapMethod("net/minecraft/core/BaseBlockPosition", "getY", "()I", NO_SUPERS), "Spigot 이 붙인 이름 → 난독화 → Mojang");
        assertEquals("offset", m.mapMethod("net/minecraft/core/BlockPosition", "c", "(III)Lnet/minecraft/core/BlockPosition;", NO_SUPERS));
        assertEquals("literal", m.mapMethod("net/minecraft/network/chat/IChatBaseComponent", "b",
                "(Ljava/lang/String;)Lnet/minecraft/network/chat/IChatMutableComponent;", NO_SUPERS));
        assertEquals("getBukkitEntity", m.mapMethod("net/minecraft/server/level/EntityPlayer", "getBukkitEntity", "()Lorg/bukkit/entity/Player;", NO_SUPERS),
                "표에 없는 이름(CraftBukkit 이 더한 메서드)은 그대로");
        assertEquals("<init>", m.mapMethod("net/minecraft/core/BlockPosition", "<init>", "(III)V", NO_SUPERS));
    }

    @Test
    void memberLinesUseSpigotClassNamesInDescriptors() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        // 실제 members.csrg 처럼 서술자에 Spigot 클래스 이름(BaseBlockPosition)이 들어간 줄(검토 1)
        assertEquals("offset", m.mapMethod("net/minecraft/core/BaseBlockPosition", "offsetSpigot", "(III)Lnet/minecraft/core/BaseBlockPosition;", NO_SUPERS));
    }

    @Test
    void unobfuscatedOwnersAreFoundWithoutAClassLine() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        // MinecraftServer 처럼 클래스 이름이 난독화되지 않아 cl.csrg 에 없는 주인(검토 2). w()Z 는 Level 에도 있어 클래스 사이 찾기로는 못 정한다
        assertEquals("isRunning", m.mapMethod("net/minecraft/server/MinecraftServer", "w", "()Z", NO_SUPERS));
    }

    @Test
    void inheritedMembersFollowTheGivenSupertypes() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        Function<String, List<String>> supers = Map.of("aub", List.of("atu"), "jh", List.of("kb"), "aqv", List.of("cmx"), "cmx", List.of("btn"),
                "btn", List.of("bsr"))::get;
        Function<String, List<String>> safe = c -> supers.apply(c) == null ? List.of() : supers.apply(c);
        assertEquals("send", m.mapMethod("net/minecraft/server/network/PlayerConnection", "b", "(Lnet/minecraft/network/protocol/Packet;)V", safe));
        assertEquals("getX", m.mapMethod("net/minecraft/core/BlockPosition", "u", "()I", safe));
        assertEquals("tickCount", m.mapField("net/minecraft/server/level/EntityPlayer", "ticksLived", "I", safe), "상속된 필드 + Spigot 이 붙인 이름");
        assertEquals("connection", m.mapField("net/minecraft/server/level/EntityPlayer", "c", "Lnet/minecraft/server/network/PlayerConnection;", safe));
    }

    @Test
    void withoutSupertypesOnlyAgreeingDeclarationsAreUsed() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        // l()V 는 네 클래스가 선언하고 모두 tick — 재정의 관계로 보고 쓴다
        assertEquals("tick", m.mapMethod("net/minecraft/server/level/EntityPlayer", "l", "()V", NO_SUPERS));
        // b(Packet) 는 한 곳(atu)만 — 그 이름
        assertEquals("send", m.mapMethod("net/minecraft/server/network/PlayerConnection", "b", "(Lnet/minecraft/network/protocol/Packet;)V", NO_SUPERS));
        // p()V 는 dcw=doThing, aqu=otherThing 으로 갈린다 — 이름을 그대로 두어 덫에 걸리게 한다(엉뚱한 멤버로 잇지 않는다)
        assertEquals("p", m.mapMethod("net/minecraft/server/level/EntityPlayer", "p", "()V", NO_SUPERS));
    }

    @Test
    void reflectionUsesRuntimeClassChains() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        assertEquals("getX", m.reflectMethod(net.minecraft.core.BlockPos.class, "u", new Class<?>[0], false), "getMethod 는 상위 클래스도 본다");
        assertEquals("u", m.reflectMethod(net.minecraft.core.BlockPos.class, "u", new Class<?>[0], true), "getDeclaredMethod 는 그 클래스만");
        assertEquals("getY", m.reflectMethod(net.minecraft.core.Vec3i.class, "getY", new Class<?>[0], true));
        assertEquals("connection", m.reflectField(net.minecraft.server.level.ServerPlayer.class, "c", true));
        assertEquals("send", m.reflectMethod(net.minecraft.server.network.ServerGamePacketListenerImpl.class, "b",
                "(Lnet/minecraft/network/protocol/Packet;)V", false));
        assertEquals("offset", m.reflectMethod(net.minecraft.core.BlockPos.class, "c", new Class<?>[] {int.class, int.class, int.class}, true));
    }

    @Test
    void lambdaInterfaceMethodsMapByNameWhenUnambiguous() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        assertEquals("literal", m.mapMethodAnyDesc("net/minecraft/network/chat/IChatBaseComponent", "b", NO_SUPERS));
        assertEquals("zz", m.mapMethodAnyDesc("net/minecraft/network/chat/IChatBaseComponent", "zz", NO_SUPERS));
    }

    @Test
    void remapperUsesShimHierarchyAndLeavesOwnAndForeignNamesAlone() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        SpigotRemapper r = new SpigotRemapper(m, NmsSymbols.RUNTIME, Set.of("net/minecraft/server/level/EntityPlayerOwn"));
        assertEquals("net/minecraft/server/level/ServerPlayer", r.map("net/minecraft/server/level/EntityPlayer"));
        assertEquals("a", r.map("a"), "기본 패키지의 플러그인 클래스(난독화된 플러그인)는 Mojang 난독화 이름과 겹쳐도 그대로");
        assertEquals("org/bukkit/entity/Player", r.map("org/bukkit/entity/Player"));
        // ServerGamePacketListenerImpl shim 의 위 클래스가 ServerCommonPacketListenerImpl — 그 사슬로 send 를 찾는다
        assertEquals("send", r.mapMethodName("net/minecraft/server/network/PlayerConnection", "b", "(Lnet/minecraft/network/protocol/Packet;)V"));
        assertEquals("getX", r.mapMethodName("net/minecraft/core/BlockPosition", "u", "()I"));
        assertEquals("u", r.mapMethodName("spig/Own", "u", "()I"));
    }

    @Test
    void malformedInputIsRejected() {
        assertThrows(IOException.class, () -> SpigotMappings.parse(new StringReader("not a mapping"), new StringReader(SpigotMappingsFixture.CLASSES),
                new StringReader("")));
        assertThrows(IOException.class, () -> SpigotMappings.parse(new StringReader(SpigotMappingsFixture.MOJANG), new StringReader("# 빈 표"),
                new StringReader("")));
    }

    @Test
    void pluginRegistryIsPerPlugin() throws IOException {
        SpigotMappings m = SpigotMappingsFixture.parse();
        SpigotMappings.register("RegA", m);
        try {
            assertSame(m, SpigotMappings.forPlugin("RegA"));
            assertNull(SpigotMappings.forPlugin("RegB"));
            assertEquals("net.minecraft.server.level.ServerPlayer", CraftNames.resolve("net.minecraft.server.level.EntityPlayer", "RegA"));
            assertEquals("[Lnet.minecraft.server.level.ServerPlayer;", CraftNames.resolve("[Lnet.minecraft.server.level.EntityPlayer;", "RegA"));
            assertEquals("net.minecraft.server.level.EntityPlayer", CraftNames.resolve("net.minecraft.server.level.EntityPlayer", "RegB"));
        } finally {
            SpigotMappings.register("RegA", null);
        }
        assertNull(SpigotMappings.forPlugin("RegA"));
        assertTrue(m.classCount() > 10);
    }
}
