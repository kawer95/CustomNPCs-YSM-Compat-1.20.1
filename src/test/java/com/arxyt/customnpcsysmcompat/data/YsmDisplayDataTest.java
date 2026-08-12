package com.arxyt.customnpcsysmcompat.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YsmDisplayDataTest {
    @Test
    void disabledWhenModelIdIsBlank() {
        assertFalse(new YsmDisplayData(true, "  ").enabled());
        assertEquals("", new YsmDisplayData(true, null).modelId());
    }

    @Test
    void trimsAndLimitsModelId() {
        assertEquals("pack/model", new YsmDisplayData(true, " pack/model ").modelId());
        String value = "x".repeat(YsmDisplayData.MAX_MODEL_ID_LENGTH + 20);
        assertEquals(YsmDisplayData.MAX_MODEL_ID_LENGTH, new YsmDisplayData(true, value).modelId().length());
    }

    @Test
    void searchMatchesNameAndIdIgnoringCase() {
        assertTrue(YsmDisplayData.matches("MAID", "pack/maid", "Example"));
        assertTrue(YsmDisplayData.matches("fox", "pack/model", "Wine Fox"));
        assertFalse(YsmDisplayData.matches("cat", "pack/model", "Wine Fox"));
    }

    @Test
    void nbtDefaultsAndRoundTrips() {
        assertEquals(YsmDisplayData.DISABLED, YsmNbtCodec.read(new CompoundTag()));

        CompoundTag root = new CompoundTag();
        YsmDisplayData selected = new YsmDisplayData(true, "pack/static_maid");
        YsmNbtCodec.write(root, selected);
        assertEquals(selected, YsmNbtCodec.read(root));
    }
}
