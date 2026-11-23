package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.server.plugin.packet.EntityData767;
import org.junit.jupiter.api.Test;

/**
 * 엔티티 데이터 칸 상수(엔티티 데이터 설계 D2): 명세(entity-data-spec.py 가 Paper 서버 서명표의 public/protected 필드로 만든다)가 잠금 파일에 묶은
 * {@code DATA_*} 필드마다, 그 클래스를 사슬에 가진 엔티티 종류 전부의 칸 배치에서 그 번호의 값 종류가 상수의 값 종류와 같다.
 */
class NmsEntityDataConstantsTest {

    static final Path LOCK = Path.of("src/shimspec/shim-" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".lock");

    @Test
    void everyDataConstantMatchesTheSlotLayoutOfEveryKindThatHasIt() throws Exception {
        List<String[]> fields = new ArrayList<>();
        for (String l : Files.readAllLines(LOCK, StandardCharsets.UTF_8)) {
            String[] p = l.split("\t");
            if (p.length >= 4 && p[0].equals("F") && p[3].equals("Lnet/minecraft/network/syncher/EntityDataAccessor;")) fields.add(p);
        }
        assertEquals(49, fields.size(), "Paper 1.21.1 의 public/protected 칸 상수 수");
        Map<String, Class<?>> kinds = kinds();
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (String[] f : fields) {
            Class<?> owner = Class.forName(f[1].replace('/', '.'));
            var field = owner.getDeclaredField(f[2]);
            assertTrue(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), f[1] + "." + f[2]);
            field.setAccessible(true);
            var acc = (EntityDataAccessor<?>) field.get(null);
            assertNotNull(acc, f[1] + "." + f[2]);
            String ser = EntityDataBridge.name(acc.serializer());
            int kindsWithIt = 0;
            for (var k : kinds.entrySet()) {
                if (!owner.isAssignableFrom(k.getValue())) continue;
                kindsWithIt++;
                EntityDataType<?>[] layout = EntityData767.layout(k.getKey());
                if (acc.id() >= layout.length || !layout[acc.id()].name().equals(ser)) bad.add(k.getKey() + " " + f[1] + "." + f[2] + " 칸 " + acc.id() + " " + ser);
                checked++;
            }
            if (kindsWithIt == 0 && !f[1].endsWith("/Player")) bad.add(f[1] + "." + f[2] + ": 그 클래스를 가진 종류가 없다");
        }
        if (!bad.isEmpty()) fail(bad.size() + " 곳:\n  " + String.join("\n  ", bad));
        assertTrue(checked > 100, "견준 (종류, 상수) 수 " + checked);
    }

    /** FancyNpcs 가 쓰는 네 상수(바닐라 1.21.1 번호). */
    @Test
    void fancyNpcsConstants() {
        assertEquals(18, net.minecraft.world.entity.animal.goat.Goat.DATA_HAS_LEFT_HORN.id());
        assertEquals(19, net.minecraft.world.entity.animal.goat.Goat.DATA_HAS_RIGHT_HORN.id());
        assertEquals(18, net.minecraft.world.entity.monster.Shulker.DATA_COLOR_ID.id());
        assertEquals(17, net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION.id());
    }

    static Map<String, Class<?>> kinds() throws IOException, ClassNotFoundException {
        Map<String, Class<?>> out = new LinkedHashMap<>();
        try (var in = NmsEntityDataConstantsTest.class.getClassLoader().getResourceAsStream("META-INF/furnace/shim-kinds.txt")) {
            assertNotNull(in);
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] p = l.trim().split(" ");
                if (p.length == 3 && p[0].equals("E")) out.put(p[1], Class.forName(p[2], false, NmsEntityDataConstantsTest.class.getClassLoader()));
            }
        }
        assertEquals(129, out.size());
        return out;
    }
}
