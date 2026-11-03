package br.com.tw.core.utils;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public final class TWItemTags {

    private static final String NAMESPACE = "tw";

    private TWItemTags() {
    }

    public static ItemStack set(ItemStack item, Map<String, Object> tags) {
        if (isEmpty(item) || tags == null || tags.isEmpty()) return item;

        ItemMeta meta = requireMeta(item);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            NamespacedKey key = key(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof String) {
                data.set(key, PersistentDataType.STRING, (String) value);
            } else if (value instanceof Boolean) {
                data.set(key, PersistentDataType.BYTE, (byte) ((Boolean) value ? 1 : 0));
            } else if (value instanceof Integer) {
                data.set(key, PersistentDataType.INTEGER, (Integer) value);
            } else if (value instanceof Long) {
                data.set(key, PersistentDataType.LONG, (Long) value);
            } else if (value instanceof Double) {
                data.set(key, PersistentDataType.DOUBLE, (Double) value);
            } else {
                throw new IllegalArgumentException("Tipo de tag não suportado: "
                        + (value == null ? "null" : value.getClass().getName()));
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    public static boolean has(ItemStack item, String key) {
        if (isEmpty(item)) return false;
        PersistentDataContainer data = requireMeta(item).getPersistentDataContainer();
        NamespacedKey namespacedKey = key(key);
        return data.has(namespacedKey, PersistentDataType.STRING)
                || data.has(namespacedKey, PersistentDataType.BYTE)
                || data.has(namespacedKey, PersistentDataType.INTEGER)
                || data.has(namespacedKey, PersistentDataType.LONG)
                || data.has(namespacedKey, PersistentDataType.DOUBLE);
    }

    public static String getString(ItemStack item, String key) {
        if (isEmpty(item)) return null;
        return requireMeta(item).getPersistentDataContainer()
                .get(key(key), PersistentDataType.STRING);
    }

    public static long getLong(ItemStack item, String key, long fallback) {
        if (isEmpty(item)) return fallback;
        PersistentDataContainer data = requireMeta(item).getPersistentDataContainer();
        return getLong(data, key(key), fallback);
    }

    static long getLong(PersistentDataContainer data, NamespacedKey key, long fallback) {
        if (data.has(key, PersistentDataType.LONG)) {
            Long value = data.get(key, PersistentDataType.LONG);
            if (value != null) return value;
        }
        if (data.has(key, PersistentDataType.INTEGER)) {
            Integer value = data.get(key, PersistentDataType.INTEGER);
            if (value != null) return value.longValue();
        }
        return fallback;
    }

    public static int getInt(ItemStack item, String key, int fallback) {
        if (isEmpty(item)) return fallback;
        Integer value = requireMeta(item).getPersistentDataContainer().get(key(key), PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }

    public static double getDouble(ItemStack item, String key, double fallback) {
        if (isEmpty(item)) return fallback;
        Double value = requireMeta(item).getPersistentDataContainer().get(key(key), PersistentDataType.DOUBLE);
        return value == null ? fallback : value;
    }

    public static boolean getBoolean(ItemStack item, String key, boolean fallback) {
        if (isEmpty(item)) return fallback;
        Byte value = requireMeta(item).getPersistentDataContainer().get(key(key), PersistentDataType.BYTE);
        return value == null ? fallback : value != 0;
    }

    private static ItemMeta requireMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalStateException("O item não disponibiliza metadados.");
        return meta;
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
