// 서버 엔티티가 아닌 api.entity.Entity 가 서버 입구로 들어왔을 때의 오류. 호환 계층이 만든 월드 밖 엔티티(ChunkInternals.WorldlessEntity)는 왜 안 되는지 말해 준다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.internal.ChunkInternals;

final class ForeignEntity {

    private ForeignEntity() {}

    /** 던질 오류: 월드 밖 엔티티면 UnsupportedOperationException(분명한 이유), 그 밖은 IllegalArgumentException(서버가 만든 핸들이 아니다). */
    static RuntimeException reject(Entity e, String prefix) {
        if (e instanceof ChunkInternals.WorldlessEntity) {
            return new UnsupportedOperationException(prefix + ": the entity is not in a world yet (it was made with EntityType#create and not added) - add it to a world first: " + e.type());
        }
        return new IllegalArgumentException(prefix + ": " + e);
    }
}
