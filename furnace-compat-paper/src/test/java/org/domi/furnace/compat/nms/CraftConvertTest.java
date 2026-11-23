package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.Fluid;
import org.bukkit.Keyed;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-264 P4 변환 가족 W/U 의 실행 도우미({@link CraftConvert} — 생성기가 낸 {@code CraftPotionEffectType.minecraftToBukkit} 등의 몸통이 부른다).
 * 지금 양쪽 레지스트리가 다 있는 종류(상태 효과·속성·엔티티 종류·그림)는 값 전부가 왕복해 같은 객체로 돌아오고, NMS 쪽 등록 값은 모두 같은 키의
 * Bukkit 값이 있다. 아직 없는 레지스트리는 껍데기와 같은 {@link UnsupportedNmsCall}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftConvertTest {
    static final String TAIL = ", this can happen if a plugin creates its own registry entry with out properly registering it.";
    FurnaceTestServer server;

    @BeforeAll
    void start() { server = FurnaceTestServer.builder().failFast(false).start(); }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void registryValuesRoundTrip() {
        roundTrip(Registries.MOB_EFFECT, PotionEffectType.class);
        roundTrip(Registries.ATTRIBUTE, Attribute.class);
        roundTrip(Registries.ENTITY_TYPE, EntityType.class);
        roundTrip(Registries.PAINTING_VARIANT, Art.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void roundTrip(ResourceKey<?> key, Class<? extends Keyed> type) {
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (Keyed v : (Iterable<Keyed>) (Iterable) Bukkit.getRegistry((Class) type)) {
            try {
                Object m = CraftConvert.bukkitToMinecraft(v, key, "test");
                if (CraftConvert.minecraftToBukkit(m, key, type, true, "test") != v) bad.add(v + " -> " + m + " does not come back");
                Holder<?> h = CraftConvert.bukkitToMinecraftHolder(v, key, TAIL, "test");
                assertInstanceOf(Holder.Reference.class, h, v.toString());
                assertSame(m, h.value());
                assertSame(v, CraftConvert.minecraftHolderToBukkit(h, key, type, true, "test"));
                n++;
            } catch (RuntimeException e) {
                bad.add(v + ": " + e);
            }
        }
        Registry<Object> nms = (Registry<Object>) RegistryAccess.SERVER.registry((ResourceKey) key).orElseThrow();
        int nmsCount = 0;
        for (Object m : nms) {
            nmsCount++;
            try {
                CraftConvert.minecraftToBukkit(m, key, type, true, "test");
            } catch (RuntimeException e) {
                bad.add(nms.getKey(m) + " (Minecraft side): " + e);
            }
        }
        assertEquals(List.of(), bad, key.toString());
        assertEquals(nmsCount, n, key + ": Bukkit 값 수 = 등록된 NMS 값 수");
        assertTrue(n > 0, key.toString());
    }

    /** 변환 가족 U 의 손 몫: CraftPotionEffectType#getHandle 은 같은 키의 NMS 상태 효과(Paper 의 handle 필드와 같은 객체). */
    @Test
    void potionEffectTypeHandle() {
        for (PotionEffectType t : PotionEffectType.values()) {
            Object h = ((org.bukkit.craftbukkit.potion.CraftPotionEffectType) t).getHandle();
            assertSame(CraftConvert.bukkitToMinecraft(t, Registries.MOB_EFFECT, "test"), h, t.toString());
            assertSame(t, CraftConvert.minecraftToBukkit(h, Registries.MOB_EFFECT, PotionEffectType.class, true, "test"));
        }
    }

    @Test
    void edgesMatchPaper() {
        Object speed = CraftConvert.bukkitToMinecraft(PotionEffectType.SPEED, Registries.MOB_EFFECT, "test");
        assertThrows(IllegalArgumentException.class, () -> CraftConvert.minecraftToBukkit(null, Registries.MOB_EFFECT, PotionEffectType.class, true, "test"));
        assertThrows(IllegalArgumentException.class, () -> CraftConvert.bukkitToMinecraft(null, Registries.MOB_EFFECT, "test"));
        // 아직 없는 NMS 레지스트리(물약)·Bukkit 레지스트리(유체 — Bukkit.getRegistry 에 없음)·생성기가 못 찾은 필드(null)는 껍데기와 같은 오류
        var e = assertThrows(UnsupportedNmsCall.class, () -> CraftConvert.bukkitToMinecraftHolder(PotionType.WATER, Registries.POTION, TAIL, "x.CraftPotionType#bukkitToMinecraftHolder"));
        assertTrue(e.getMessage().contains("CraftPotionType"), e.getMessage());
        assertThrows(UnsupportedNmsCall.class, () -> CraftConvert.minecraftToBukkit(speed, Registries.MOB_EFFECT, Fluid.class, true, "test"));
        assertThrows(UnsupportedNmsCall.class, () -> CraftConvert.bukkitToMinecraft(PotionEffectType.SPEED, null, "test"));
        // Paper 처럼 null 은 레지스트리를 찾기 전에 본다(레지스트리가 없어도 IllegalArgumentException, 메시지 없음)
        var n = assertThrows(IllegalArgumentException.class, () -> CraftConvert.bukkitToMinecraftHolder(null, Registries.POTION, TAIL, "test"));
        assertEquals(null, n.getMessage());
        assertThrows(IllegalArgumentException.class, () -> CraftConvert.bukkitToMinecraft(null, null, "test"));
        // 등록 안 된 NMS 값: CraftRegistry 길은 IllegalStateException(Paper 문구), 제 몸통 길은 NoSuchElementException
        Object stray = new Object();
        var ise = assertThrows(IllegalStateException.class, () -> CraftConvert.minecraftToBukkit(stray, Registries.MOB_EFFECT, PotionEffectType.class, true, "test"));
        assertEquals("Cannot convert '" + stray + "' to bukkit representation, since it is not registered.", ise.getMessage());
        assertThrows(java.util.NoSuchElementException.class, () -> CraftConvert.minecraftToBukkit(stray, Registries.MOB_EFFECT, PotionEffectType.class, false, "test"));
    }
}
