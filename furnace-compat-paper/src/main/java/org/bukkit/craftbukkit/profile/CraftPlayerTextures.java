package org.bukkit.craftbukkit.profile;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.profile.SharedPlayerProfile;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.profile.PlayerTextures;
import org.domi.furnace.api.config.ConfigSection;

/**
 * 프로필의 "textures" 속성을 스킨·망토 URL 로 보여 주는 값. 속성의 Base64 JSON 은 처음 읽을 때 풀고, 고치면 속성을 새로 만든다
 * (Mojang 이 붙인 서명·시각·프로필 표시는 새 속성에 없다). 새 속성의 JSON 은 짧은 꼴(공백 없음)이다.
 */
public final class CraftPlayerTextures implements PlayerTextures {

    static final String PROPERTY_NAME = "textures";
    private static final String MINECRAFT_HOST = "textures.minecraft.net";
    private static final String MINECRAFT_PATH = "/texture/";

    private static void validateTextureUrl(URL url) {
        if (url == null) return;   // 비어 있음
        if (!MINECRAFT_HOST.equals(url.getHost())) throw new IllegalArgumentException("Expected host '" + MINECRAFT_HOST + "' but got '" + url.getHost() + "'");
        if (!url.getPath().startsWith(MINECRAFT_PATH)) throw new IllegalArgumentException("Expected path starting with '" + MINECRAFT_PATH + "' but got '" + url.getPath() + "'");
    }

    private static URL parseUrl(String urlString) {
        if (urlString == null) return null;
        try {
            return URI.create(urlString).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            return null;
        }
    }

    private static SkinModel parseSkinModel(String name) {
        if (name == null) return null;
        try {
            return SkinModel.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private final SharedPlayerProfile profile;

    // 처음 읽을 때 푼다
    private boolean loaded = false;
    private ConfigSection data;
    private long timestamp;

    private URL skin;
    private SkinModel skinModel = SkinModel.CLASSIC;
    private URL cape;

    /** 고쳤다 — 속성을 새로 만들어야 한다. Mojang 이 붙인 값(서명·시각 등)은 고치는 순간 모두 무효다(되돌려도 마찬가지). */
    private boolean dirty = false;

    public CraftPlayerTextures(SharedPlayerProfile profile) { this.profile = profile; }

    public void copyFrom(PlayerTextures other) {
        if (other == this) return;
        if (!(other instanceof CraftPlayerTextures otherTextures)) {
            throw new IllegalArgumentException("Expecting CraftPlayerTextures, got " + other.getClass().getName());
        }
        clear();
        ProfileProperty texturesProperty = otherTextures.getProperty();
        profile.setProperty(PROPERTY_NAME, texturesProperty);
        if (texturesProperty != null && (!Objects.equals(profile.getUniqueId(), otherTextures.profile.getUniqueId())
                || !Objects.equals(profile.getName(), otherTextures.profile.getName()))) {
            // 다른 프로필의 속성이면 이 프로필용으로 새로 만든다
            ensureLoaded();
            markDirty();
            rebuildPropertyIfDirty();
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        ProfileProperty property = getProperty();
        if (property == null) return;
        data = CraftProfileProperty.decodePropertyValue(property.getValue());
        if (data != null) {
            ConfigSection texturesMap = data.get("textures") instanceof ConfigSection s ? s : null;
            loadSkin(texturesMap);
            loadCape(texturesMap);
            loadTimestamp();
        }
    }

    private static String stringOrNull(ConfigSection parent, String key) {
        Object v = parent.get(key);
        return v instanceof String || v instanceof Number || v instanceof Boolean ? String.valueOf(v) : null;
    }

    private void loadSkin(ConfigSection texturesMap) {
        if (texturesMap == null || !(texturesMap.get("SKIN") instanceof ConfigSection texture)) return;
        skin = parseUrl(stringOrNull(texture, "url"));
        skinModel = texture.get("metadata") instanceof ConfigSection metadata ? parseSkinModel(stringOrNull(metadata, "model")) : null;
        if (skinModel == null && skin != null) skinModel = SkinModel.CLASSIC;   // 스킨이 있는데 모양이 없으면 기본 모양
    }

    private void loadCape(ConfigSection texturesMap) {
        if (texturesMap == null || !(texturesMap.get("CAPE") instanceof ConfigSection texture)) return;
        cape = parseUrl(stringOrNull(texture, "url"));
    }

    private void loadTimestamp() {
        Object v = data.get("timestamp");
        if (v instanceof Number n) timestamp = n.longValue();
        else if (v instanceof String s) {
            try {
                timestamp = Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                // 읽을 수 없는 시각은 0
            }
        }
    }

    private void markDirty() {
        dirty = true;
        data = null;
        timestamp = 0L;
    }

    @Override
    public boolean isEmpty() {
        ensureLoaded();
        return skin == null && cape == null;
    }

    @Override
    public void clear() {
        profile.removeProperty(PROPERTY_NAME);
        loaded = false;
        data = null;
        timestamp = 0L;
        skin = null;
        skinModel = SkinModel.CLASSIC;
        cape = null;
        dirty = false;
    }

    @Override
    public URL getSkin() {
        ensureLoaded();
        return skin;
    }

    @Override
    public void setSkin(URL skinUrl) { setSkin(skinUrl, SkinModel.CLASSIC); }

    @Override
    public void setSkin(URL skinUrl, SkinModel model) {
        validateTextureUrl(skinUrl);
        if (model == null) model = SkinModel.CLASSIC;
        if (Objects.equals(getSkin(), skinUrl) && Objects.equals(getSkinModel(), model)) return;
        skin = skinUrl;
        skinModel = skinUrl != null ? model : SkinModel.CLASSIC;
        markDirty();
    }

    @Override
    public SkinModel getSkinModel() {
        ensureLoaded();
        return skinModel;
    }

    @Override
    public URL getCape() {
        ensureLoaded();
        return cape;
    }

    @Override
    public void setCape(URL capeUrl) {
        validateTextureUrl(capeUrl);
        if (Objects.equals(getCape(), capeUrl)) return;
        cape = capeUrl;
        markDirty();
    }

    @Override
    public long getTimestamp() {
        ensureLoaded();
        return timestamp;
    }

    @Override
    public boolean isSigned() {
        if (dirty) return false;
        ProfileProperty property = getProperty();
        return property != null && CraftProfileProperty.hasValidSignature(property);
    }

    ProfileProperty getProperty() {
        rebuildPropertyIfDirty();
        return profile.getProperty(PROPERTY_NAME);
    }

    public void rebuildPropertyIfDirty() {
        if (!dirty) return;
        dirty = false;
        if (isEmpty()) {
            profile.removeProperty(PROPERTY_NAME);
            return;
        }
        // Mojang 서명·시각·프로필 표시 없이 스킨·망토만 든 새 값(JSON 항목 순서가 정해져 있다: 스킨, 망토)
        StringBuilder json = new StringBuilder("{\"textures\":{");
        if (skin != null) {
            json.append("\"SKIN\":{\"url\":");
            CraftProfileProperty.writeJsonString(json, skin.toExternalForm());
            if (skinModel != SkinModel.CLASSIC) json.append(",\"metadata\":{\"model\":\"").append(skinModel.name().toLowerCase(Locale.ROOT)).append("\"}");
            json.append('}');
        }
        if (cape != null) {
            if (skin != null) json.append(',');
            json.append("\"CAPE\":{\"url\":");
            CraftProfileProperty.writeJsonString(json, cape.toExternalForm());
            json.append('}');
        }
        json.append("}}");
        data = CraftProfileProperty.decodePropertyValue(CraftProfileProperty.encodeJson(json.toString()));
        profile.setProperty(PROPERTY_NAME, new ProfileProperty(PROPERTY_NAME, CraftProfileProperty.encodeJson(json.toString())));
    }

    @Override
    public String toString() {
        ensureLoaded();
        rebuildPropertyIfDirty();
        return "CraftPlayerTextures [data=" + (data == null ? "null" : data.toMap()) + "]";
    }

    @Override
    public int hashCode() {
        ProfileProperty property = getProperty();
        return property == null ? 0 : CraftProfileProperty.hashCode(property);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CraftPlayerTextures other)) return false;
        return CraftProfileProperty.equals(getProperty(), other.getProperty());
    }
}
