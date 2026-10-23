// 바닐라 ServerOpList (ops.json). 키는 UUID 문자열.
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.nio.file.Path;

public final class ServerOpList extends StoredUserList<GameProfile, ServerOpListEntry> {

    public ServerOpList(Path file) { super(file); }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject json) { return new ServerOpListEntry(json); }

    public boolean isListed(GameProfile profile) { return contains(profile); }

    /** 인원 제한 무시 여부(목록에 없으면 거짓). */
    public boolean canBypassPlayerLimit(GameProfile profile) {
        ServerOpListEntry e = get(profile);
        return e != null && e.getBypassesPlayerLimit();
    }

    @Override
    protected String getKeyForUser(GameProfile profile) { return profile.id().toString(); }

    /** 목록의 이름들(바닐라 getUserList 재정의). */
    @Override
    public String[] getUserList() {
        return getEntries().stream().map(StoredUserEntry::getUser).filter(java.util.Objects::nonNull)
                .map(GameProfile::name).toArray(String[]::new);
    }
}
