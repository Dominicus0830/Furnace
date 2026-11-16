// Paper 1.21.1 DatapackManager — Server#getDatapackManager.
package io.papermc.paper.datapack;

import java.util.Collection;

public interface DatapackManager {

    /** 폴더를 다시 훑는다(켜진 목록은 그대로). */
    void refreshPacks();

    /** 이름으로, 없으면 null. */
    Datapack getPack(String name);

    /** 알려진 모든 팩(바꿀 수 없는 사본). */
    Collection<Datapack> getPacks();

    /** 켜진 팩(적용 순서). */
    Collection<Datapack> getEnabledPacks();
}
