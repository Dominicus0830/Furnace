package org.bukkit.configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/** 설정 트리의 한 구역. 경로는 '.' 로 이어 쓴다. */
public interface ConfigurationSection {

    Set<String> getKeys(boolean deep);

    Map<String, Object> getValues(boolean deep);

    boolean contains(String path);

    boolean contains(String path, boolean ignoreDefault);

    boolean isSet(String path);

    String getCurrentPath();

    String getName();

    Configuration getRoot();

    ConfigurationSection getParent();

    Object get(String path);

    Object get(String path, Object def);

    void set(String path, Object value);

    ConfigurationSection createSection(String path);

    ConfigurationSection createSection(String path, Map<?, ?> map);

    String getString(String path);

    String getString(String path, String def);

    boolean isString(String path);

    int getInt(String path);

    int getInt(String path, int def);

    boolean isInt(String path);

    boolean getBoolean(String path);

    boolean getBoolean(String path, boolean def);

    boolean isBoolean(String path);

    double getDouble(String path);

    double getDouble(String path, double def);

    boolean isDouble(String path);

    long getLong(String path);

    long getLong(String path, long def);

    boolean isLong(String path);

    List<?> getList(String path);

    List<?> getList(String path, List<?> def);

    boolean isList(String path);

    List<String> getStringList(String path);

    List<Integer> getIntegerList(String path);

    List<Boolean> getBooleanList(String path);

    List<Double> getDoubleList(String path);

    List<Long> getLongList(String path);

    List<Map<?, ?>> getMapList(String path);

    <T> T getObject(String path, Class<T> clazz);

    <T> T getObject(String path, Class<T> clazz, T def);

    /** 값을 ConfigurationSerializable 로 읽는다(저장된 {@code ==} 맵을 되읽은 것). 종류가 다르거나 없으면 null. */
    <T extends ConfigurationSerializable> T getSerializable(String path, Class<T> clazz);

    <T extends ConfigurationSerializable> T getSerializable(String path, Class<T> clazz, T def);

    org.bukkit.inventory.ItemStack getItemStack(String path);

    org.bukkit.inventory.ItemStack getItemStack(String path, org.bukkit.inventory.ItemStack def);

    boolean isItemStack(String path);

    org.bukkit.Location getLocation(String path);

    org.bukkit.Location getLocation(String path, org.bukkit.Location def);

    boolean isLocation(String path);

    org.bukkit.util.Vector getVector(String path);

    org.bukkit.util.Vector getVector(String path, org.bukkit.util.Vector def);

    boolean isVector(String path);

    org.bukkit.Color getColor(String path);

    org.bukkit.Color getColor(String path, org.bukkit.Color def);

    boolean isColor(String path);

    /** ConfigurationSerializable 로 저장된 값은 구역이 아니라 객체다 — 그 경로는 null. */
    ConfigurationSection getConfigurationSection(String path);

    boolean isConfigurationSection(String path);

    ConfigurationSection getDefaultSection();

    void addDefault(String path, Object value);

    List<String> getComments(String path);

    List<String> getInlineComments(String path);

    void setComments(String path, List<String> comments);

    void setInlineComments(String path, List<String> comments);
}
