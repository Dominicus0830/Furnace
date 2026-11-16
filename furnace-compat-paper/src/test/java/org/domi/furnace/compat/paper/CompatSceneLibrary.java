// 호환 기준 장면 목록(T-075). 새 장면은 여기 한 줄을 더한다(CompatSceneLibraryTest 가 금 파일을 비교).
package org.domi.furnace.compat.paper;

import java.util.List;
import org.domi.furnace.server.scene.LibraryScene;

public final class CompatSceneLibrary {
    private CompatSceneLibrary() {}

    public static List<LibraryScene> all() {
        return List.of(new SceneBlockReads(), new SceneHeldItem(), new SceneEntityReads(), new SceneWorldHandles(), new SceneWrapperCache(), new SceneEvents(), new SceneOnlinePlayers(), new SceneOnlineOrder(), new SceneProtocolLibOrder(), new org.domi.furnace.compat.nms.SceneAsmPrefilter());
    }
}
