// PR #190 검토 재현: 앉은 고양이가 엔더 상자를 막는가. 바닐라 EnderChestBlock(dip).useWithoutItem 은 위 칸 레드스톤 전도체만 보고 isChestBlockedAt(dhb.a)을 부르지 않는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A09EnderChestCatTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static BlockActor actor(List<String> log) {
        return (BlockActor) Proxy.newProxyInstance(BlockActor.class.getClassLoader(), new Class<?>[]{BlockActor.class}, (p, m, a) -> {
            if (m.getName().equals("openContainer") || m.getName().equals("awardStat")) { log.add(m.getName()); return null; }
            Class<?> rt = m.getReturnType();
            if (rt == boolean.class) return false;
            if (rt == int.class) return 0;
            if (rt == double.class) return 0.0;
            if (rt == float.class) return 0f;
            return null;
        });
    }

    private void run(String block, boolean expectOpen) {
        var level = new PlayerSleepingTest.Lvl();
        Pos chest = new Pos(2, -60, 2);
        int state = B.defaultState(block);
        level.setBlock(chest, state, 3);
        var cat = new Cat(level, level.nextEntityId());
        cat.moveTo(2.5, -59, 2.5, 0, 0);
        level.addFreshEntity(cat);
        cat.setTame(true, false);
        cat.setInSittingPose(true);
        System.out.println(block + " blockEntity=" + level.getBlockEntity(chest));
        List<String> log = new ArrayList<>();
        B.behavior(state).useWithoutItem(state, level, chest, actor(log), new Hit(chest, 1, 2.5, -59, 2.5, false));
        System.out.println(block + " 앉은 고양이 위: 열림=" + log.contains("openContainer"));
        assertEquals(expectOpen, log.contains("openContainer"));
    }

    /** 바닐라: 엔더 상자는 앉은 고양이가 있어도 열린다(dip.useWithoutItem 에 isChestBlockedAt 호출 없음). */
    @Test void enderChestOpensWithSittingCat() { run("minecraft:ender_chest", true); }

    /** 대조: 일반 상자는 막힌다. */
    @Test void chestBlockedWithSittingCat() { run("minecraft:chest", false); }
}
