// 엔티티 활성 범위 장면(D11): 플레이어 한 명을 가운데 두고 동물·적대 몹·주민·경험치 구슬을 4~88칸 거리에 흩어 놓고(먼 곳에 기둥 위 아이템·새끼·번식 대기·
// 사랑 모드·효과 걸린 동물도 더해)
// 활성 범위를 **켠 채로**(기본값) 돌린다. 금 파일 scene-activation 은 Paper 방식 동작을 못 박는다(바뀌면 활성 범위 동작이 바뀐 것).
// 첫 200틱은 새 엔티티 면역(tickCount < 200)으로 모두 활성이라 끈 것(Off)과 같고, 그 뒤로 달라진다 — SceneLibraryTest 가 확인한다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.config.EntityActivationSettings;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.AgeableMob;
import org.domi.furnace.server.entity.Animal;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityActivation;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.item.ItemContext;

public class ActivationScene extends TickScene {
    private static final String[] TYPES = {"minecraft:cow", "minecraft:zombie", "minecraft:sheep", "minecraft:skeleton", "minecraft:chicken",
            "minecraft:creeper", "minecraft:pig", "minecraft:spider", "minecraft:villager", "minecraft:experience_orb"};

    private SceneLevel level;

    @Override public String name() { return "activation"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "D11 엔티티 활성 범위(켜짐) — 범위·면역·깨우기·inactiveTick"; }

    @Override
    protected EntityActivationSettings entityActivation() { return EntityActivationSettings.DEFAULT; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("activation", 6);
        level.dayTime = 18000;   // 밤: 적대 몹이 해에 타지 않는다
        level.gameTime = 18000;
        var player = SceneKit.addPlayer(level, "A", 0.5, SceneLevel.FLOOR_Y, 0.5);
        pillars.clear();
        java.util.Random r = new java.util.Random(7);
        int n = size.pick(160, 800);
        for (int i = 0; i < n; i++) {
            double dist = 4 + r.nextInt(85), ang = r.nextDouble() * Math.PI * 2;
            double x = Math.floor(Math.cos(ang) * dist) + 0.5, z = Math.floor(Math.sin(ang) * dist) + 0.5;
            Entity e = Entities.create(level, TYPES[i % TYPES.length]);
            e.moveTo(x, SceneLevel.FLOOR_Y + 1, z, r.nextFloat() * 360, 0);
            level.addFreshEntity(e);
        }
        extras(level, player);
    }

    /** 기둥(돌 4칸) 위 먼 아이템들 — PILLAR_GONE 틱에 기둥을 지워 떨어지기 시작하는 틱을 본다. */
    private final java.util.List<Pos> pillars = new java.util.ArrayList<>();
    private static final int PILLAR_GONE = 340;

    /**
     * PR #326 검토 R3: 처음 장면이 놓친 변이(아이템 중력 틱, 비활성 나이 먹기, noActionTime, 생물 면역, 새끼·사랑 모드 면역)가 드러나게
     * 먼 곳(40~80칸)에 기둥 위 아이템, 새끼·번식 대기·사랑 모드 동물, 효과가 걸린 동물을 더 둔다. 서로 10칸 넘게 떨어뜨려 번식이 일어나지 않게 한다.
     */
    private void extras(SceneLevel level, org.domi.furnace.server.entity.Player player) {
        int stone = B.defaultState("minecraft:stone");
        for (int i = 0; i < 18; i++) {
            double ang = i * Math.PI * 2 / 18 + 0.13, dist = 40 + (i * 7) % 41;
            int x = (int) Math.floor(Math.cos(ang) * dist), z = (int) Math.floor(Math.sin(ang) * dist);
            int kind = i % 6, j = i / 6;
            if (kind < 2) {   // 기둥 위 아이템
                for (int dy = 0; dy < 4; dy++) {
                    Pos p = new Pos(x, SceneLevel.FLOOR_Y + dy, z);
                    level.setBlock(p, stone, 2);
                    pillars.add(p);
                }
                for (int k = 0; k < i % 4; k++) level.nextEntityId();   // 아이템 중력 틱 위상((tickCount + id) % 4)을 흩는다
                ItemEntity item = new ItemEntity(level, level.nextEntityId(), x + 0.5, SceneLevel.FLOOR_Y + 4, z + 0.5,
                        new ItemStack(ItemContext.id("minecraft:item", "diamond"), 1));
                item.setNeverPickUp();
                item.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(item);
                continue;
            }
            Entity e = Entities.create(level, kind == 5 ? "minecraft:zombie" : "minecraft:cow");
            e.moveTo(x + 0.5, SceneLevel.FLOOR_Y, z + 0.5, 0, 0);
            e.setOnGround(true);   // AI 없는 몹은 움직이지 않아(바닐라) 땅 여부가 갱신되지 않는다 — 바닥에 바로 세운다
            // AI 없음: 걸어 다니면 "움직이는 중" 면역(100틱)에 먼저 걸려 아래 면역이 드러나지 않는다. 활성 틱 수는 noActionTime(AI 없으면 활성 틱엔
            // 안 오르고 비활성 틱엔 오른다)·효과 남은 시간(활성 틱에만 준다)으로 보인다.
            ((org.domi.furnace.server.entity.Mob) e).setNoAi(true);
            switch (kind) {
                // 면역이 이어지는 동안은 면역 틱 수가 겉으로 안 보인다(끝나자마자 다시 검사해 또 면역) — 장면 안(300~400틱)에서 끝나게 해 끝난 순간의 차이를 본다.
                case 2 -> ((AgeableMob) e).setAge(-330 - 20 * j);        // 새끼: 비활성에도 나이를 먹고 5틱 면역, 장면 중에 다 큰다
                case 3 -> ((AgeableMob) e).setAge(6000);                  // 번식 대기: 비활성에도 0 으로 다가간다
                case 4 -> ((Animal) e).setInLove(player);                 // 사랑 모드: 5틱 면역(AI 가 없어 줄지 않는다)
                default -> ((LivingEntity) e).addEffect(new MobEffectInstance(MobEffect.SLOWNESS, 320 + 11 * j, 0));   // 효과(활성 틱에만 준다): 1틱 면역, 장면 중에 끝난다(끝나는 틱의 홀짝을 섞는다 — 1틱 면역은 2틱 걸음이라 홀짝에 따라서만 보인다)
            }
            level.addFreshEntity(e);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(400, 1200); }

    @Override protected int checkEvery(Size size) { return 50; }

    @Override
    protected void tick(int t) {
        if (t == PILLAR_GONE) {
            int air = B.defaultState("minecraft:air");
            for (Pos p : pillars) level.setBlock(p, air, 2);
        }
        level.tick();
    }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        rec.line("activation " + label + " entities", SceneKit.entities(level));
        rec.line("activation " + label + " random", new IdentityHash().add(level.random().nextLong()));
        int active = 0, all = 0;
        for (Entity e : level.entities().tickSnapshot()) {
            all++;
            if (EntityActivation.isMarkedActive(e)) active++;
        }
        rec.text("activation " + label + " count " + all + " marked-active " + active);
        // noActionTime 은 저장 NBT 에 없다 — 따로 섞는다(비활성 틱에도 센다)
        IdentityHash idle = new IdentityHash();
        java.util.List<Entity> sorted = new java.util.ArrayList<>(level.entities().tickSnapshot());
        sorted.sort(java.util.Comparator.comparingInt(Entity::id));
        for (Entity e : sorted) if (e instanceof LivingEntity le) idle.add(e.id()).add(le.getNoActionTime());
        rec.line("activation " + label + " no-action-time", idle);
    }

    /** 같은 장면을 활성 범위를 끄고(바닐라) — 금 파일 없이 SceneLibraryTest 가 켠 쪽과 비교만 한다. */
    public static final class Off extends ActivationScene {
        @Override public String name() { return "activation-off"; }
        @Override protected EntityActivationSettings entityActivation() { return EntityActivationSettings.OFF; }
    }
}
