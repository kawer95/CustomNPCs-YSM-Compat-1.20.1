package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NpcAmmoItemHandlerTest {
    @Test
    void mapsProjectileAndEightDropSlotsOnly() {
        assertEquals(5, NpcAmmoItemHandler.inventorySlot(0));
        for (int slot = 1; slot <= 8; slot++) {
            assertEquals(slot + 6, NpcAmmoItemHandler.inventorySlot(slot));
        }
    }

    @Test
    void rejectsSlotsOutsideTheAmmoView() {
        assertThrows(RuntimeException.class, () -> NpcAmmoItemHandler.inventorySlot(-1));
        assertThrows(RuntimeException.class, () -> NpcAmmoItemHandler.inventorySlot(9));
    }
}
