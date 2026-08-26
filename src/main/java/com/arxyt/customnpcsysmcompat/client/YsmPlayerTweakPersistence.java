package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakKind;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side persistence for the local player's formal YSM config_forms choices.
 *
 * <p>YSM intentionally does not save these values. This store observes only expressions
 * emitted by its config-form page, converts them back to a model/form/choice selection,
 * and restores that selection once the player's YSM capability is ready. It never stores
 * raw Molang.</p>
 */
@Mod.EventBusSubscriber(modid = CustomNpcsYsmCompat.MOD_ID, value = Dist.CLIENT)
public final class YsmPlayerTweakPersistence {
    private static final String FILE_NAME = "customnpcs_ysm_compat_player_tweaks.json";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ACCOUNTS = 32;
    private static final long SAVE_DELAY_TICKS = 20L;

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    private static final Map<UUID, Map<String, YsmTweakProfile>> PROFILES = new LinkedHashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    private static long ticks;
    private static long writeAtTick = Long.MAX_VALUE;
    private static UUID appliedAccount;
    private static String appliedModelId = "";
    private static YsmTweakProfile appliedProfile = YsmTweakProfile.EMPTY;

    private YsmPlayerTweakPersistence() {
    }

    /** Called by the YSM roulette mixin before YSM evaluates a config-form expression. */
    public static void captureRouletteExpression(String expression) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ensureLoaded();
        Ysm265Adapter.capturePlayerTweak(player, expression).ifPresent(captured -> {
            UUID account = player.getUUID();
            String modelId = YsmDisplayData.normalizeModelId(captured.modelId());
            if (modelId.isEmpty()) return;
            YsmTweakProfile before = profileFor(account, modelId);
            YsmTweakProfile updated = before.with(captured.entry());
            if (updated.equals(before)) return;
            profilesFor(account).put(modelId, updated);
            markDirty();

            // YSM is about to apply this exact expression itself. Mark it as already
            // applied so the next client tick does not replay and re-send it.
            if (account.equals(appliedAccount) && modelId.equals(appliedModelId)) {
                appliedProfile = updated;
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        ensureLoaded();
        restoreIfReady();
        if (dirty && ticks >= writeAtTick) flushNow();
    }

    /** Leaves data on disk intact but forces a fresh restore after a world/resource replacement. */
    public static void resetSession() {
        appliedAccount = null;
        appliedModelId = "";
        appliedProfile = YsmTweakProfile.EMPTY;
    }

    public static void flushNow() {
        if (!dirty) return;
        ensureLoaded();
        JsonObject root = encode();
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(root.toString());
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            writeAtTick = Long.MAX_VALUE;
        } catch (IOException error) {
            CustomNpcsYsmCompat.LOGGER.warn("Unable to save local YSM player tweak profiles to {}", FILE, error);
        }
    }

    private static void restoreIfReady() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            resetSession();
            return;
        }
        String modelId = YsmDisplayData.normalizeModelId(Ysm265Adapter.playerModelId(player));
        if (modelId.isEmpty() || !Ysm265Adapter.isPlayerModelReady(player)) return;

        UUID account = player.getUUID();
        YsmTweakProfile profile = profileFor(account, modelId);
        if (account.equals(appliedAccount) && modelId.equals(appliedModelId) && profile.equals(appliedProfile)) {
            return;
        }

        Ysm265Adapter.TweakApplyResult result = Ysm265Adapter.applyPlayerTweaks(player, modelId, profile, true);
        if (result.applied() > 0) Ysm265Adapter.advancePlayerAnimation(player);
        appliedAccount = account;
        appliedModelId = modelId;
        appliedProfile = profile;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonElement document = JsonParser.parseReader(reader);
            if (!document.isJsonObject()) throw new IOException("root is not an object");
            decode(document.getAsJsonObject(), PROFILES);
        } catch (Exception error) {
            PROFILES.clear();
            CustomNpcsYsmCompat.LOGGER.warn("Unable to read local YSM player tweak profiles from {}; defaults will be used", FILE, error);
        }
    }

    private static Map<String, YsmTweakProfile> profilesFor(UUID account) {
        Map<String, YsmTweakProfile> found = PROFILES.get(account);
        if (found != null) return found;
        if (PROFILES.size() >= MAX_ACCOUNTS) {
            UUID oldest = PROFILES.keySet().iterator().next();
            PROFILES.remove(oldest);
        }
        Map<String, YsmTweakProfile> created = new LinkedHashMap<>();
        PROFILES.put(account, created);
        return created;
    }

    private static YsmTweakProfile profileFor(UUID account, String modelId) {
        return PROFILES.getOrDefault(account, Map.of()).getOrDefault(modelId, YsmTweakProfile.EMPTY);
    }

    private static void markDirty() {
        dirty = true;
        writeAtTick = ticks + SAVE_DELAY_TICKS;
    }

    private static JsonObject encode() {
        JsonObject root = new JsonObject();
        root.addProperty("Format", FORMAT_VERSION);
        JsonObject accounts = new JsonObject();
        for (Map.Entry<UUID, Map<String, YsmTweakProfile>> account : PROFILES.entrySet()) {
            JsonObject modelProfiles = new JsonObject();
            for (Map.Entry<String, YsmTweakProfile> model : account.getValue().entrySet()) {
                if (model.getValue() == null || model.getValue().isEmpty()) continue;
                JsonArray entries = new JsonArray();
                for (YsmTweakEntry entry : model.getValue().entries()) {
                    JsonObject value = new JsonObject();
                    value.addProperty("ButtonId", entry.buttonId());
                    value.addProperty("FormIndex", entry.formIndex());
                    value.addProperty("Kind", entry.kind().name());
                    value.addProperty("Variable", entry.variable());
                    value.addProperty("BooleanValue", entry.booleanValue());
                    value.addProperty("NumberValue", entry.numberValue());
                    value.addProperty("Choice", entry.choice());
                    value.addProperty("Order", entry.order());
                    entries.add(value);
                }
                if (!entries.isEmpty()) modelProfiles.add(model.getKey(), entries);
            }
            if (modelProfiles.size() > 0) accounts.add(account.getKey().toString(), modelProfiles);
        }
        root.add("Accounts", accounts);
        return root;
    }

    private static void decode(JsonObject root, Map<UUID, Map<String, YsmTweakProfile>> into) {
        JsonElement accountsElement = root.get("Accounts");
        if (accountsElement == null || !accountsElement.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> accountEntry : accountsElement.getAsJsonObject().entrySet()) {
            if (into.size() >= MAX_ACCOUNTS) break;
            UUID account;
            try {
                account = UUID.fromString(accountEntry.getKey());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (!accountEntry.getValue().isJsonObject()) continue;
            Map<String, YsmTweakProfile> models = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> modelEntry : accountEntry.getValue().getAsJsonObject().entrySet()) {
                if (models.size() >= YsmDisplayData.MAX_TWEAK_PROFILES) break;
                String modelId = YsmDisplayData.normalizeModelId(modelEntry.getKey());
                if (modelId.isEmpty() || !modelEntry.getValue().isJsonArray()) continue;
                YsmTweakProfile profile = decodeProfile(modelEntry.getValue().getAsJsonArray());
                if (!profile.isEmpty()) models.put(modelId, profile);
            }
            if (!models.isEmpty()) into.put(account, models);
        }
    }

    private static YsmTweakProfile decodeProfile(JsonArray entries) {
        java.util.List<YsmTweakEntry> loadedEntries = new java.util.ArrayList<>();
        for (JsonElement raw : entries) {
            if (loadedEntries.size() >= YsmTweakProfile.MAX_ENTRIES || !raw.isJsonObject()) break;
            try {
                JsonObject value = raw.getAsJsonObject();
                YsmTweakKind kind = YsmTweakKind.valueOf(readString(value, "Kind"));
                double number = readDouble(value, "NumberValue");
                if (!Double.isFinite(number)) continue;
                YsmTweakEntry entry = new YsmTweakEntry(readString(value, "ButtonId"),
                        readInt(value, "FormIndex"), kind, readString(value, "Variable"),
                        readBoolean(value, "BooleanValue"), number, readString(value, "Choice"),
                        readLong(value, "Order"));
                if (entry.valid()) loadedEntries.add(entry);
            } catch (RuntimeException ignored) {
                // One malformed entry must not discard the remaining settings.
            }
        }
        return new YsmTweakProfile(loadedEntries);
    }

    private static String readString(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static boolean readBoolean(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static int readInt(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    private static long readLong(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsLong() : 0L;
    }

    private static double readDouble(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsDouble() : 0.0D;
    }
}
