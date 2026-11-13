// IdentityKit 자기 시험용 엔티티 장면: 시험 레벨에 좀비 셋을 만들고 UUID·엔티티 난수·레벨 난수를 해시한다.
// 엔티티 id 카운터(ChunkLevel.FALLBACK_IDS)가 JVM 전역이라 새 JVM(-Dfurnace.test.seeded=true)에서만 결정적이다 — IdentityKitTest.FreshJvm.
package org.domi.furnace.server.identity;

import org.domi.furnace.server.entity.DimensionTestLevel;
import org.domi.furnace.server.entity.Zombie;

public final class EntitySelftestScene implements SceneHash.Scene {
    @Override
    public void run(SceneHash.Recorder rec) {
        DimensionTestLevel level = new DimensionTestLevel("minecraft:overworld");
        for (int i = 0; i < 3; i++) {
            Zombie z = new Zombie(level, level.nextEntityId());
            rec.line("zombie " + i + " uuid", new IdentityHash().add(z.uuid().getMostSignificantBits()).add(z.uuid().getLeastSignificantBits()));
            rec.line("zombie " + i + " random", new IdentityHash().add(z.random().nextLong()).add(z.random().nextInt(1000)));
        }
        rec.line("level random", new IdentityHash().add(level.random().nextLong()));
    }
}
