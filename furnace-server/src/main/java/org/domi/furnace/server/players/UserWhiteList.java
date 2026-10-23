// 바닐라 UserWhiteList (whitelist.json). 키는 UUID 문자열.
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.nio.file.Path;

public final class UserWhiteList extends StoredUserList<GameProfile, UserWhiteListEntry> {

    public UserWhiteList(Path file) { super(file); }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject json) { return new UserWhiteListEntry(json); }

    public boolean isListed(GameProfile profile) { return contains(profile); }

    @Override
    protected String getKeyForUser(GameProfile profile) { return profile.id().toString(); }

    /** 목록의 이름들(바닐라 getUserList 재정의). */
    @Override
    public String[] getUserList() {
        return getEntries().stream().map(StoredUserEntry::getUser).filter(java.util.Objects::nonNull)
                .map(GameProfile::name).toArray(String[]::new);
    }
}
