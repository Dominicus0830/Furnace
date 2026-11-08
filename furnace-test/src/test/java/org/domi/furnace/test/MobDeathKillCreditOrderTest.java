// 회귀(PR #166 검토): 몹 EntityDeathEvent 구독자가 있으면 처치 점수(awardKillScore)가 장비 칸 정리 뒤에 돌아
// player_killed_entity 조건이 죽은 몹의 떨군 장비를 못 본다 — 습격 대장(불길한 깃발) 처치 발전 과제 voluntary_exile 이 안 된다.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.domi.furnace.api.event.entity.EntityDeathEvent;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Pillager;
import org.domi.furnace.server.entity.Raider;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class MobDeathKillCreditOrderTest {

    /** 처치 점수(KillCredit.award → player_killed_entity 의 entity 조건)가 머리 칸을 읽을 때 본 값을 적어 둔다. */
    static final class ProbePillager extends Pillager {
        volatile String seenByKillCredit = "안 읽음";
        ProbePillager(org.domi.furnace.server.block.ChunkLevel level, int id) { super(level, id); }
        @Override
        public org.domi.furnace.server.entity.ItemStack getItemBySlot(EquipmentSlot slot) {
            var v = super.getItemBySlot(slot);
            if (slot == EquipmentSlot.HEAD && StackWalker.getInstance().walk(f -> f.anyMatch(x -> x.getClassName().endsWith(".KillCredit") && x.getMethodName().equals("award")))) {
                seenByKillCredit = v.isEmpty() ? "빈 칸" : "깃발";
            }
            return v;
        }
    }

    private static String killCaptain(boolean listen) {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            if (listen) server.api().events().subscribe(server.plugin(CounterPlugin.class), EntityDeathEvent.class, e -> {});   // 아무것도 안 하는 구독자
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            var p = server.internals().playerByName("Dana");
            return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
                var level = r.level();
                var cap = new ProbePillager(level, level.nextEntityId());
                cap.moveTo(p.x() + 1.5, p.y(), p.z(), 0, 0);
                level.addFreshEntity(cap);
                cap.setItemSlot(EquipmentSlot.HEAD, Raider.leaderBanner());
                cap.setDropChance(EquipmentSlot.HEAD, 2.0F);   // 바닐라 대장: 반드시 떨군다(칸을 비운다)
                cap.hurtBy(new Entity.Damage("player_attack", false, false, false), 1000f, p);
                return cap.seenByKillCredit;
            }));
        }
    }

    @Test
    void voluntaryExileWithoutListener() {
        assertEquals("깃발", killCaptain(false), "구독자 없음(바닐라 경로): 처치 점수는 장비를 비우기 전에 본다");
    }

    @Test
    void voluntaryExileWithDeathListener() {
        assertEquals("깃발", killCaptain(true), "EntityDeathEvent 구독자만 있어도 처치 점수가 장비 정리 뒤에 돈다 → voluntary_exile 조건(머리=불길한 깃발)이 깨진다");
    }
}
