package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 피해 출처 shim: 피해 형식 레지스트리(값·순서·태그)를 바닐라 1.21.1 데이터 파일과 견주고, DamageSources 가 고르는 형식과
 * Paper DamageSource 의 표시 규칙(복사할 때 옮기는 것, 이벤트 가해자 제약)을 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsDamageSourceTest {

    static final Path DATA = Path.of("../tools/vanilla-data/server-1.21.1/data/minecraft");

    FurnaceTestServer server;
    ServerLevel level;
    ServerPlayer player;
    Registry<DamageType> types;

    @BeforeAll
    void start() {
        Assumptions.assumeTrue(Files.isDirectory(DATA.resolve("damage_type")), "로컬 바닐라 데이터 없음");
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Hurt");
        server.tick(3);
        player = ((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle();
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        types = RegistryAccess.SERVER.registryOrThrow(Registries.DAMAGE_TYPE);
    }

    @AfterAll
    void stop() { if (server != null) server.close(); }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"?([^\",}\\s]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    @Test
    void registryMatchesVanillaDataFiles() throws IOException {
        TreeSet<String> files = new TreeSet<>();
        try (var s = Files.list(DATA.resolve("damage_type"))) {
            s.forEach(p -> files.add("minecraft:" + p.getFileName().toString().replace(".json", "")));
        }
        TreeSet<String> ids = new TreeSet<>();
        types.keySet().forEach(k -> ids.add(k.toString()));
        assertEquals(files, ids);
        // 번호 순서는 서버가 클라에 보내는 순서
        List<String> order = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) order.add(types.getKey(types.byId(i)).toString());
        assertEquals(org.domi.furnace.api.Furnace.server().gameRegistries().entries("minecraft:damage_type"), order);
        for (String id : files) {
            String json = Files.readString(DATA.resolve("damage_type/" + id.substring(10) + ".json"));
            DamageType t = types.get(ResourceLocation.parse(id));
            assertEquals(field(json, "message_id"), t.msgId(), id);
            assertEquals(Float.parseFloat(field(json, "exhaustion")), t.exhaustion(), id);
            assertEquals(field(json, "scaling"), t.scaling().getSerializedName(), id);
            String effects = field(json, "effects");
            assertEquals(effects == null ? "hurt" : effects, t.effects().getSerializedName(), id);
            String death = field(json, "death_message_type");
            assertEquals(death == null ? "default" : death, t.deathMessageType().getSerializedName(), id);
        }
    }

    @Test
    void everyDamageTypesKeyIsRegistered() throws IllegalAccessException {
        int n = 0;
        for (Field f : DamageTypes.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            @SuppressWarnings("unchecked")
            ResourceKey<DamageType> k = (ResourceKey<DamageType>) f.get(null);
            assertTrue(types.containsKey(k), f.getName());
            n++;
        }
        assertEquals(types.size(), n);
    }

    @Test
    void tagsMatchVanillaTagFiles() throws IOException, IllegalAccessException {
        for (Field f : DamageTypeTags.class.getFields()) {
            @SuppressWarnings("unchecked")
            TagKey<DamageType> tag = (TagKey<DamageType>) f.get(null);
            Path file = DATA.resolve("tags/damage_type/" + tag.location().getPath() + ".json");
            if (!Files.exists(file)) continue;   // 바닐라에 파일이 없는 태그(비어 있음)
            String json = Files.readString(file);
            if (json.contains("#minecraft:")) continue;   // 다른 태그를 품은 태그는 펼친 값을 따로 견주지 않는다
            TreeSet<String> want = new TreeSet<>();
            Matcher m = Pattern.compile("\"(minecraft:[a-z_]+)\"").matcher(json);
            while (m.find()) want.add(m.group(1));
            TreeSet<String> got = new TreeSet<>();
            for (var h : types.getOrCreateTag(tag)) got.add(h.getRegisteredName());
            assertEquals(want, got, tag.toString());
        }
        assertTrue(level.damageSources().fall().is(DamageTypeTags.IS_FALL));
        assertTrue(level.damageSources().fall().is(DamageTypeTags.BYPASSES_ARMOR));   // 바닐라: 떨어짐은 방어구를 무시한다
        assertFalse(level.damageSources().fall().is(DamageTypeTags.IS_FIRE));
        assertTrue(level.damageSources().fellOutOfWorld().is(DamageTypeTags.BYPASSES_INVULNERABILITY));
    }

    @Test
    void damageSourcesPickTheVanillaTypes() {
        var ds = level.damageSources();
        assertSame(ds, level.damageSources());
        assertSame(ds, player.damageSources());
        assertSame(ds.fall(), ds.fall());   // 엔티티 없는 출처는 한 번 만든 것
        assertTrue(ds.fellOutOfWorld().is(DamageTypes.FELL_OUT_OF_WORLD));
        assertEquals("outOfWorld", ds.fellOutOfWorld().getMsgId());
        assertTrue(ds.melting().isMelting());
        assertTrue(ds.melting().is(DamageTypes.ON_FIRE));
        assertTrue(ds.poison().isPoison());
        assertFalse(ds.magic().isPoison());

        DamageSource attack = ds.playerAttack(player);
        assertTrue(attack.is(DamageTypes.PLAYER_ATTACK));
        assertSame(player, attack.getEntity());
        assertSame(player, attack.getDirectEntity());
        assertTrue(attack.isDirect());
        assertFalse(attack.scalesWithDifficulty());   // 원인이 플레이어
        assertTrue(ds.explosion(player, player).is(DamageTypes.PLAYER_EXPLOSION));
        assertTrue(ds.explosion(null, player).is(DamageTypes.EXPLOSION));
        assertTrue(ds.explosion(null, null).is(DamageTypes.EXPLOSION));
        assertTrue(ds.thrown(player, null).is(DamageTypes.THROWN));
        assertFalse(ds.thrown(player, null).isDirect());
        assertTrue(ds.outOfBorder().is(DamageTypes.OUTSIDE_BORDER));

        Vec3 at = new Vec3(1, 2, 3);
        DamageSource bed = ds.badRespawnPointExplosion(at);
        assertTrue(bed.is(DamageTypes.BAD_RESPAWN_POINT));
        assertSame(at, bed.getSourcePosition());
        assertNull(bed.getDirectBlockState());
        assertEquals(player.position(), attack.getSourcePosition());
        assertNull(attack.sourcePositionRaw());
        assertEquals(DamageScaling.ALWAYS, bed.type().scaling());
        assertTrue(bed.scalesWithDifficulty());
        assertEquals("DamageSource (badRespawnPoint)", bed.toString());
        assertEquals(bed.type().exhaustion(), bed.getFoodExhaustion());
    }

    @Test
    void paperMarksFollowPaperCopyRules() {
        var ds = level.damageSources();
        DamageSource plain = new DamageSource(types.getHolderOrThrow(DamageTypes.GENERIC));
        assertSame(plain, plain.sweep());   // 자기를 고친다
        assertTrue(plain.isSweep());
        assertSame(plain, plain.critical());
        assertTrue(plain.isCritical());
        assertSame(plain, plain.directBlock((org.bukkit.block.Block) null));   // null 이면 그대로

        DamageSource withDamager = plain.customEventDamager(player);   // 복사본에 싣는다
        assertSame(player, withDamager.getCustomEventDamager());
        assertNull(plain.getCustomEventDamager());
        assertTrue(withDamager.isSweep());   // 복사는 sweep 을 옮기고
        assertFalse(withDamager.isCritical());   // critical 은 옮기지 않는다(Paper cloneInstance)

        DamageSource attack = ds.playerAttack(player);
        assertSame(player, attack.getCustomEventDamager());   // 없으면 직접 엔티티
        assertThrows(IllegalStateException.class, () -> attack.customEventDamager(player));
    }
}
