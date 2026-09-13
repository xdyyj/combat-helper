package com.xdyyj.autoattacker.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FirearmAdapter.safeInvoke 三个重载的行为契约测试。
 *
 * 背景：safeInvoke 原有可变参数单版本，后来为消除「无参/单参调用点每次都分配
 * Object[]」的开销而拆出两个专用重载(本类中有 53 个无参、63 个单参调用点，
 * 位于每帧反复执行的瞄准回路上)。重载拆分必须保证语义与拆分前完全一致 ——
 * 否则会静默改变 116 个调用点的异常行为。本测试即固定该契约。
 */
class FirearmAdapterSafeInvokeTest {

    /** 供反射调用的样本方法：无参 */
    public String noArg() {
        return "noarg";
    }

    /** 供反射调用的样本方法：单参 */
    public String oneArg(String s) {
        return "one:" + s;
    }

    /** 供反射调用的样本方法：双参 */
    public String twoArg(String a, int b) {
        return a + ":" + b;
    }

    /** 供反射调用的样本方法：抛异常 */
    public String boom() {
        throw new IllegalStateException("boom");
    }

    private static Method m(String name, Class<?>... params) throws Exception {
        return FirearmAdapterSafeInvokeTest.class.getMethod(name, params);
    }

    @Test
    @DisplayName("无参重载：正常返回")
    void testNoArgOverload_returnsValue() throws Exception {
        assertEquals("noarg", FirearmAdapter.safeInvoke(m("noArg"), this));
    }

    @Test
    @DisplayName("单参重载：正常返回")
    void testOneArgOverload_returnsValue() throws Exception {
        assertEquals("one:x", FirearmAdapter.safeInvoke(m("oneArg", String.class), this, "x"));
    }

    @Test
    @DisplayName("可变参数重载：正常返回")
    void testVarargsOverload_returnsValue() throws Exception {
        assertEquals("a:1", FirearmAdapter.safeInvoke(m("twoArg", String.class, int.class), this, "a", 1));
    }

    @Test
    @DisplayName("三个重载：method 为 null 时均返回 null，且不抛异常")
    void testAllOverloads_nullMethodReturnsNull() {
        assertNull(FirearmAdapter.safeInvoke((Method) null, this));
        assertNull(FirearmAdapter.safeInvoke((Method) null, this, "x"));
        assertNull(FirearmAdapter.safeInvoke((Method) null, this, "a", 1));
    }

    @Test
    @DisplayName("三个重载：被调方法抛异常时均返回 null，不向上传播")
    void testAllOverloads_exceptionSwallowed() throws Exception {
        Method boom = m("boom");
        assertNull(FirearmAdapter.safeInvoke(boom, this));
        assertNull(FirearmAdapter.safeInvoke(boom, this, "x"));
        assertNull(FirearmAdapter.safeInvoke(boom, this, "a", 1));
    }

    @Test
    @DisplayName("可变参数重载：调用方显式传 Object[] 时行为与展开传参一致")
    void testVarargsExplicitArrayMatchesExpanded() throws Exception {
        Method two = m("twoArg", String.class, int.class);
        Object[] args = new Object[]{"a", 1};
        String expanded = FirearmAdapter.safeInvoke(two, this, "a", 1);
        String arrayForm = FirearmAdapter.safeInvoke(two, this, args);
        assertEquals(expanded, arrayForm);
    }
}
