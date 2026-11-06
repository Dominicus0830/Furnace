// 권한 첨부 — 한 플러그인이 한 대상에게 준 권한 값 묶음.
package org.domi.furnace.api.permission;

import java.util.Map;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface PermissionAttachment {

    FurnacePlugin plugin();

    void setPermission(String node, boolean value);

    void unsetPermission(String node);

    Map<String, Boolean> permissions();

    /** 첨부를 떼어낸다. */
    void remove();
}
