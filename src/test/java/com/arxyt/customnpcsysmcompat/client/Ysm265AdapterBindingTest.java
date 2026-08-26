package com.arxyt.customnpcsysmcompat.client;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Locks the private YSM 2.6.5 symbols used by the reflection-only adapter. */
class Ysm265AdapterBindingTest {
    @Test
    void configFormReflectionSymbolsExistInPinnedYsmVersion() {
        assertDoesNotThrow(() -> {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> form = Class.forName("com.elfmcys.yesstevemodel.O000OO0OoOoo0ooO0o000000", false, loader);
            Class<?> range = Class.forName("com.elfmcys.yesstevemodel.oOooooooo00OO0OOO0oO00o0", false, loader);
            Class<?> radio = Class.forName("com.elfmcys.yesstevemodel.oO0oOOoO0ooo0O0OO0oo0oo0", false, loader);
            Class<?> expression = Class.forName("com.elfmcys.yesstevemodel.O0o0OOO00000oO00O00oOOo0", false, loader);
            Class<?> animatable = Class.forName("com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo", false, loader);
            Class<?> parser = Class.forName("com.elfmcys.yesstevemodel.O00o0ooOoo00o00o0OOOO0o0", false, loader);
            Class<?> playerAnimatable = Class.forName("com.elfmcys.yesstevemodel.o0OOO0o0o0OOo000oO00o00O", false, loader);
            Class<?> expressionPacket = Class.forName("com.elfmcys.yesstevemodel.oOooOooO0Oo0oo0o0o00O0Oo", false, loader);
            Class<?> network = Class.forName("com.elfmcys.yesstevemodel.OO00OoOOOOooO0ooOoOoOooO", false, loader);
            Class<?> resource = Class.forName("com.elfmcys.yesstevemodel.oOo0oO0OOo0ooOoo0oOo0oOo", false, loader);
            Class<?> metadata = Class.forName("com.elfmcys.yesstevemodel.o0OoOo0OOOo0oOo0oOO0O0oO", false, loader);
            Class<?> properties = Class.forName("com.elfmcys.yesstevemodel.oO00O0OOOoO0000oOO0OOO00", false, loader);
            Class<?> button = Class.forName("com.elfmcys.yesstevemodel.OOoOO0ooooOO00000oO0oOo0", false, loader);

            form.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo");
            form.getMethod("o0OOooo0o0OO00OoOOOo0o0O");
            form.getMethod("O00OOOooOoooOoo0o0o0oO0O");
            form.getMethod("oOOOo0OOO0ooooo0O00OO0o0");
            range.getMethod("OOOOo0O0oO0OOo0O0O0Oo0O0");
            range.getMethod("Ooooo0oooO0oooOOOoO0000O");
            range.getMethod("oo0OoO00oOoo000O0000o0oo");
            radio.getMethod("OOOOo0O0oO0OOo0O0O0Oo0O0");
            parser.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", String.class);
            parser.getMethod("o0OOooo0o0OO00OoOOOo0o0O", String.class);
            playerAnimatable.getMethod("ooooO0o00oO0Oo0OOo0O0O0o");
            expressionPacket.getConstructor(String.class, int.class);
            network.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", Object.class);
            animatable.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", expression,
                    boolean.class, boolean.class, Consumer.class);
            resource.getMethod("Ooooo0oooO0oooOOOoO0000O");
            metadata.getMethod("o0OOooo0o0OO00OoOOOo0o0O");
            properties.getMethod("Ooooo0oooO0oooOOOoO0000O");
            button.getMethod("o0OOooo0o0OO00OoOOOo0o0O");
            button.getMethod("O00OOOooOoooOoo0o0o0oO0O");
            button.getMethod("oOOOo0OOO0ooooo0O00OO0o0");
        });
    }

    @Test
    void rangeNormalizesBoundsAndStep() {
        YsmTweakForm.Range form = new YsmTweakForm.Range(0, "", "", "v.size", 0.2D, 0.2D, 5.0D);
        assertEquals(0.2D, Ysm265Adapter.normalizeRange(form, -10.0D));
        assertEquals(5.0D, Ysm265Adapter.normalizeRange(form, 99.0D));
        assertEquals(1.2D, Ysm265Adapter.normalizeRange(form, 1.13D));
    }

    private static void assertEquals(double expected, double actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, 0.000001D);
    }
}
