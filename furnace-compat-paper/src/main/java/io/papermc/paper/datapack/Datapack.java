// Paper 1.21.1 Datapack: 데이터 팩 하나(만들 때의 사본 — isEnabled 는 그때 값). 구현은 org.domi.furnace.compat.paper.CompatDatapacks.
// Adventure Component 를 돌려주는 getTitle·getDescription·computeDisplayName 은 호환 계층이 Adventure 를 싣지 않아 아직 없다.
package io.papermc.paper.datapack;

import java.util.Set;
import org.bukkit.FeatureFlag;

public interface Datapack {

    /** /datapack 이 보이는 이름(vanilla, file/이름). */
    String getName();

    boolean isRequired();

    Compatibility getCompatibility();

    Set<FeatureFlag> getRequiredFeatures();

    boolean isEnabled();

    /** 켜거나 끈다 — /datapack enable·disable 처럼 리소스를 다시 읽는다(비동기). 이미 그 상태면 아무것도 안 한다. */
    void setEnabled(boolean enabled);

    DatapackSource getSource();

    enum Compatibility {
        TOO_OLD,
        TOO_NEW,
        COMPATIBLE,
    }
}
