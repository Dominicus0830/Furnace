// /summon <모든 엔티티 타입> 이 빈 NBT 로 로드·저장·소환 마무리 중 예외를 내지 않는지 확인한다(예외를 삼키지 않는다).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.command.arguments.EntityTypes;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SummonAllTypesLoadTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 소환 가능한 종류만(EntityType.canSummon: 플레이어·낚시찌 제외 — 각각 접속·던지기로만 생긴다). */
    static List<String> summonable() {
        List<String> out = new ArrayList<>();
        for (String id : EntityTypes.all()) {
            if (id.equals("minecraft:player") || id.equals("minecraft:fishing_bobber")) continue;
            out.add(id);
        }
        return out.stream().sorted().toList();
    }

    @Test
    void emptyNbtLoadsWithoutException() {
        List<String> failures = new ArrayList<>();
        List<String> unimplemented = new ArrayList<>();
        for (String id : summonable()) {
            try {
                if (!loadOnce(id)) unimplemented.add(id);
            } catch (Throwable t) {
                StackTraceElement[] st = t.getStackTrace();
                failures.add(id + ": " + t + (st.length > 0 ? " @ " + st[0] : ""));
            }
        }
        assertEquals(List.of(), unimplemented, "load 가 null 인(미구현) 종류");
        assertTrue(failures.isEmpty(), "빈 NBT 로드 실패 " + failures.size() + "개:\n" + String.join("\n", failures));
    }

    /** 구현된 종류면 true. 미구현이면 false(부르는 쪽이 원본 NBT 를 보존한다). */
    private static boolean loadOnce(String id) {
        for (boolean randomize : new boolean[] {true, false}) {
            var level = new BlockPlacementTest.TestLevel();
            NbtCompound tag = new NbtCompound();   // /summon <type> ~ ~ ~ 과 {} 모두 빈 NBT
            tag.putString("id", id);
            Entity e = Entities.load(level, tag);
            if (e == null) return false;
            if (randomize && e instanceof Mob mob) mob.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()));
            NbtCompound saved = e.save();   // 빈 아이템·번개처럼 저장하지 않는 종류는 null 이어도 된다
            if (saved != null) {   // 저장한 결과를 다시 읽어도 예외가 없고, 다시 저장하면 같은 NBT
                Entity again = Entities.load(new BlockPlacementTest.TestLevel(), saved.copy());
                assertNotNull(again, id + " 왕복 로드");
                NbtCompound saved2 = again.save();
                assertNotNull(saved2, id + " 왕복 저장");
                assertEquals(saved.tags().keySet(), saved2.tags().keySet(), id + " 왕복 시 키 집합");
            }
        }
        return true;
    }
}
