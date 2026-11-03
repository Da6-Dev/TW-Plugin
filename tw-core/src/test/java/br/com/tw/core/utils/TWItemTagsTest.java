package br.com.tw.core.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class TWItemTagsTest {
    @Test
    public void readsIntegerPersistentDataAsLongWithoutReadingItAsLongFirst() {
        PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, arguments) -> {
                    if ("has".equals(method.getName())) {
                        return arguments[1] == PersistentDataType.INTEGER;
                    }
                    if ("get".equals(method.getName())) {
                        if (arguments[1] != PersistentDataType.INTEGER) {
                            throw new AssertionError("Tried to read an integer tag as a long.");
                        }
                        return 5000;
                    }
                    throw new AssertionError("Unexpected PersistentDataContainer method: " + method.getName());
                });

        assertEquals(5000L, TWItemTags.getLong(data, new NamespacedKey("tw", "charges"), 0L));
    }

    @Test
    public void returnsFallbackWhenPersistentDataIsMissing() {
        PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, arguments) -> {
                    if ("has".equals(method.getName())) return false;
                    if ("get".equals(method.getName())) {
                        throw new AssertionError("Read a tag that is not present.");
                    }
                    throw new AssertionError("Unexpected PersistentDataContainer method: " + method.getName());
                });

        assertEquals(0L, TWItemTags.getLong(data, new NamespacedKey("tw", "charges"), 0L));
    }
}
