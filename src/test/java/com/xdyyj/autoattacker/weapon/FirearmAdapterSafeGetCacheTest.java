package com.xdyyj.autoattacker.weapon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * FirearmAdapter.safeGet* 三兄弟的缓存契约测试。
 *
 * 这三处为消除「每次调用都遍历方法/字段表」的开销而加了按类缓存。缓存必须满足：
 *   1. 相同入参返回同一个对象实例 (证明真的命中缓存)
 *   2. 查不到时返回 null，且 null 也被缓存 (否则未安装的模组会反复走异常路径)
 *   3. 同名但参数类型不同的重载不得互相污染
 */
class FirearmAdapterSafeGetCacheTest {

    /**
     * FirearmAdapter 的静态字段里有 ItemTags.create(...)，触发 Minecraft 注册表初始化，
     * 必须先完成 bootstrap，否则类初始化会抛 ExceptionInInitializerError。
     */
    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set bootstrapped flag", e);
        }
    }

    public String over(Object a) {
        return "object";
    }

    public String over(String a) {
        return "string";
    }

    public String plain() {
        return "plain";
    }

    public static String staticField = "static";

    @Test
    @DisplayName("safeGetMethod: 相同入参返回同一实例 (命中缓存)")
    void testSafeGetMethod_cachesInstance() throws Exception {
        Method first = FirearmAdapter.safeGetMethod(FirearmAdapterSafeGetCacheTest.class, "plain");
        Method second = FirearmAdapter.safeGetMethod(FirearmAdapterSafeGetCacheTest.class, "plain");
        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("safeGetMethod: 同名不同参数类型的重载不会互相污染")
    void testSafeGetMethod_overloadsDoNotCollide() {
        Method withObject = FirearmAdapter.safeGetMethod(
                FirearmAdapterSafeGetCacheTest.class, "over", Object.class);
        Method withString = FirearmAdapter.safeGetMethod(
                FirearmAdapterSafeGetCacheTest.class, "over", String.class);
        assertNotNull(withObject);
        assertNotNull(withString);
        // 两者参数类型不同，必须是不同的 Method 对象
        assertEquals(Object.class, withObject.getParameterTypes()[0]);
        assertEquals(String.class, withString.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("safeGetMethod: 查不到的方法返回 null，且重复查询仍为 null (null 被缓存)")
    void testSafeGetMethod_missingMethodReturnsNullRepeatedly() {
        assertNull(FirearmAdapter.safeGetMethod(FirearmAdapterSafeGetCacheTest.class, "noSuchMethod"));
        assertNull(FirearmAdapter.safeGetMethod(FirearmAdapterSafeGetCacheTest.class, "noSuchMethod"));
    }

    @Test
    @DisplayName("safeGetField: 相同入参返回同一实例，缺失字段返回 null")
    void testSafeGetField() {
        Field first = FirearmAdapter.safeGetField(FirearmAdapterSafeGetCacheTest.class, "staticField");
        Field second = FirearmAdapter.safeGetField(FirearmAdapterSafeGetCacheTest.class, "staticField");
        assertNotNull(first);
        assertSame(first, second);
        assertNull(FirearmAdapter.safeGetField(FirearmAdapterSafeGetCacheTest.class, "noSuchField"));
        assertNull(FirearmAdapter.safeGetField(FirearmAdapterSafeGetCacheTest.class, "noSuchField"));
    }

    @Test
    @DisplayName("safeGetClass: 已存在的类返回同一实例，不存在的返回 null 且可重复")
    void testSafeGetClass() {
        Class<?> a = FirearmAdapter.safeGetClass("java.lang.String");
        Class<?> b = FirearmAdapter.safeGetClass("java.lang.String");
        assertNotNull(a);
        assertSame(a, b);
        assertNull(FirearmAdapter.safeGetClass("does.not.Exist$AtAll"));
        assertNull(FirearmAdapter.safeGetClass("does.not.Exist$AtAll"));
    }

    @Test
    @DisplayName("safeGet*: 传入 null 类时返回 null 且不抛异常")
    void testNullOrClass() {
        assertNull(FirearmAdapter.safeGetMethod(null, "anything"));
        assertNull(FirearmAdapter.safeGetField(null, "anything"));
    }
}
