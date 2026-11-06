// Furnace.difficulty(): 서버 난이도(=/difficulty)와 플러그인 난이도 프로필. 프로필은 건 플러그인이 꺼지면 내려간다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Difficulty;
import org.domi.furnace.api.world.DifficultyProfile;
import org.domi.furnace.api.world.DifficultySettings;

final class DifficultySettingsImpl implements DifficultySettings {

    private final PluginHost host;

    DifficultySettingsImpl(PluginHost host) { this.host = host; }

    @Override
    public Difficulty difficulty() { return Difficulty.valueOf(host.core().levelData().difficulty.name()); }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        host.core().setDifficulty(org.domi.furnace.server.world.Difficulty.valueOf(difficulty.name()), true);
    }

    @Override
    public DifficultyProfile profile() {
        DifficultyProfile p = host.core().difficultyProfile();
        return p != null ? p : DifficultyProfile.vanilla(difficulty());
    }

    @Override
    public void setProfile(FurnacePlugin owner, DifficultyProfile profile) {
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(profile, "profile");
        host.core().setDifficultyProfile(owner.name(), profile);
    }

    @Override
    public void resetProfile() { host.core().clearDifficultyProfile(null); }

    /** 그 플러그인이 건 프로필이면 내린다(꺼질 때). */
    void unregisterAll(FurnacePlugin owner) { host.core().clearDifficultyProfile(owner.name()); }
}
