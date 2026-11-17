package net.minecraft.world.level.saveddata.maps;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 지도 위 표시 하나(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같은 record 다(방향은 바닐라처럼 아래 4비트만 둔다). 코덱 필드는 두지 않았다. */
public record MapDecoration(Holder<MapDecorationType> type, byte x, byte y, byte rot, Optional<Component> name) {

    public MapDecoration {
        rot = (byte) (rot & 15);   // 바닐라 생성자와 같다(검토 PR #267 R1)
    }

    public ResourceLocation getSpriteLocation() { return type.value().assetId(); }

    public boolean renderOnFrame() { return type.value().showOnItemFrame(); }
}
