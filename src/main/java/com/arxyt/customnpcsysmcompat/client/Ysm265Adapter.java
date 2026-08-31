package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakKind;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Vector2f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

/** The only class allowed to know YSM 2.6.5's obfuscated binary names. */
public final class Ysm265Adapter {
    private static final String REGISTRY = "com.elfmcys.yesstevemodel.o0OooO00ooo0OO000O0OoOoO";
    private static final String PLAYER_CAP = "com.elfmcys.yesstevemodel.O0OooOo0oOOoOoOoOooO000o";
    private static final String PLAYER_ANIMATABLE = "com.elfmcys.yesstevemodel.o0OOO0o0o0OOo000oO00o00O";
    private static final String PLAYER_SYNC_DATA = "com.elfmcys.yesstevemodel.ooOO000o0O0OOOoO0Oo0o0Oo";
    private static final String YSM_RENDER_TYPE = "com.elfmcys.yesstevemodel.o0oOo0ooO00oOoooOOOoOOo0";
    private static final String CONFIG_FORM = "com.elfmcys.yesstevemodel.O000OO0OoOoo0ooO0o000000";
    private static final String RANGE_FORM = "com.elfmcys.yesstevemodel.oOooooooo00OO0OOO0oO00o0";
    private static final String RADIO_FORM = "com.elfmcys.yesstevemodel.oO0oOOoO0ooo0O0OO0oo0oo0";
    private static final String ANIMATABLE_BASE = "com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo";
    private static final String EXPRESSION_PARSER = "com.elfmcys.yesstevemodel.O00o0ooOoo00o00o0OOOO0o0";
    private static final String PARSED_EXPRESSION = "com.elfmcys.yesstevemodel.O0o0OOO00000oO00O00oOOo0";
    private static final String EXPRESSION_PACKET = "com.elfmcys.yesstevemodel.oOooOooO0Oo0oo0o0o00O0Oo";
    private static final String NETWORK = "com.elfmcys.yesstevemodel.OO00OoOOOOooO0ooOoOoOooO";
    private static final String OBF = "Oo0Oo0o00O00Oo0OOoOOoooo";
    private static final Pattern SIMPLE_ASSIGNMENT = Pattern.compile(
            "^\\s*((?:v|variable)\\.[A-Za-z0-9_.]+)\\s*=\\s*"
                    + "([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?)\\s*;?\\s*$");
    private static final AtomicBoolean ERROR_REPORTED = new AtomicBoolean();
    private static final Set<String> TWEAK_WARNINGS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static volatile Bindings bindings;

    private Ysm265Adapter() {
    }

    public static List<YsmModelEntry> models() {
        try {
            Map<?, ?> models = (Map<?, ?>) bindings().modelRegistry.invoke(null);
            List<YsmModelEntry> result = new ArrayList<>(models.size());
            for (Map.Entry<?, ?> entry : models.entrySet()) {
                String id = String.valueOf(entry.getKey());
                result.add(new YsmModelEntry(id, displayName(entry.getValue(), id)));
            }
            result.sort(Comparator.comparing(YsmModelEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(YsmModelEntry::id));
            return List.copyOf(result);
        } catch (Throwable error) {
            report(error);
            return List.of();
        }
    }

    public static boolean hasModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> models = (Map<?, ?>) bindings().modelRegistry.invoke(null);
            return models.containsKey(modelId);
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    /** Returns the YSM model currently selected for a real player, or an empty string. */
    public static String playerModelId(Player player) {
        if (player == null) return "";
        try {
            Bindings b = bindings();
            Optional<Object> animatable = playerAnimatable(player, b);
            if (animatable.isEmpty()) return "";
            return String.valueOf(b.getPlayerModelId.invoke(animatable.get())).trim();
        } catch (Throwable error) {
            report(error);
            return "";
        }
    }

    /**
     * Converts one expression submitted by YSM's own config page into a safe, declarative
     * stored selection.  Expressions outside formal config_forms are intentionally ignored.
     */
    public static Optional<CapturedPlayerTweak> capturePlayerTweak(Player player, String expression) {
        if (player == null || expression == null || expression.isBlank()) return Optional.empty();
        try {
            String modelId = playerModelId(player);
            return captureTweak(modelId, expression);
        } catch (Throwable error) {
            report(error);
            return Optional.empty();
        }
    }

    /** Converts a formal config-form expression for a known model into a stored selection. */
    public static Optional<CapturedPlayerTweak> captureTweak(String modelId, String expression) {
        if (modelId == null || modelId.isBlank() || expression == null || expression.isBlank()) {
            return Optional.empty();
        }
        try {
            ModelTweakConfig config = tweakConfig(modelId, bindings());
            String normalized = expression.trim();

            // Radio choices can contain arbitrary multi-assignment Molang. Match the model's
            // locally installed expression exactly; never save that expression itself.
            for (LoadedTweakForm form : config.forms().values()) {
                if (form.form().kind() != YsmTweakKind.RADIO) continue;
                for (Map.Entry<String, String> choice : form.radioExpressions().entrySet()) {
                    if (normalized.equals(choice.getValue().trim())) {
                        return Optional.of(new CapturedPlayerTweak(modelId,
                                YsmTweakEntry.radio(form.buttonId(), form.form().index(),
                                        form.form().variable(), choice.getKey(), 0L)));
                    }
                }
            }

            Matcher matcher = SIMPLE_ASSIGNMENT.matcher(normalized);
            if (!matcher.matches()) return Optional.empty();
            double numeric;
            try {
                numeric = Double.parseDouble(matcher.group(2));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
            if (!Double.isFinite(numeric)) return Optional.empty();
            String variable = matcher.group(1);
            for (LoadedTweakForm form : config.forms().values()) {
                if (!variable.equals(form.form().variable())) continue;
                YsmTweakEntry entry = switch (form.form().kind()) {
                    case CHECKBOX -> (numeric == 0.0D || numeric == 1.0D)
                            ? YsmTweakEntry.checkbox(form.buttonId(), form.form().index(), variable,
                            numeric == 1.0D, 0L)
                            : null;
                    case RANGE -> YsmTweakEntry.range(form.buttonId(), form.form().index(), variable,
                            normalizeRange((YsmTweakForm.Range) form.form(), numeric), 0L);
                    case RADIO -> null;
                };
                if (entry != null) return Optional.of(new CapturedPlayerTweak(modelId, entry));
            }
        } catch (Throwable error) {
            report(error);
        }
        return Optional.empty();
    }

    /** Returns the formal model configuration forms shown by YSM's animation roulette. */
    public static List<YsmTweakGroup> tweakGroups(String modelId) {
        if (modelId == null || modelId.isBlank()) return List.of();
        try {
            return tweakConfig(modelId, bindings()).groups();
        } catch (Throwable error) {
            report(error);
            return List.of();
        }
    }

    /**
     * Replays stored choices against this proxy's local YSM animatable.  The stored data
     * contains only selections; radio expressions remain in the locally installed model.
     */
    public static TweakApplyResult applyPlayerTweaks(Player player, String modelId,
                                                      YsmTweakProfile profile) {
        return applyPlayerTweaks(player, modelId, profile, false);
    }

    /**
     * Applies a saved profile.  {@code synchronize} is only for the local, real player:
     * it mirrors YSM's own config-form packet behavior so other players receive the same
     * values while its deliberately-local {@code v.roaming.*} expressions remain local.
     */
    public static TweakApplyResult applyPlayerTweaks(Player player, String modelId,
                                                      YsmTweakProfile profile, boolean synchronize) {
        if (profile == null || profile.isEmpty() || modelId == null || modelId.isBlank()) {
            return TweakApplyResult.NONE;
        }
        try {
            Bindings b = bindings();
            Optional<Object> animatable = playerAnimatable(player, b);
            if (animatable.isEmpty()) return TweakApplyResult.NONE;
            return applyTweaks(animatable.get(), modelId, profile, synchronize ? player : null, b);
        } catch (Throwable error) {
            report(error);
            return TweakApplyResult.NONE;
        }
    }

    /** Applies a formal profile to any YSM animatable, including YSM's maid proxy. */
    public static TweakApplyResult applyTweaks(Object animatable, String modelId, YsmTweakProfile profile) {
        if (animatable == null || profile == null || profile.isEmpty()
                || modelId == null || modelId.isBlank()) return TweakApplyResult.NONE;
        try {
            return applyTweaks(animatable, modelId, profile, null, bindings());
        } catch (Throwable error) {
            report(error);
            return TweakApplyResult.NONE;
        }
    }

    private static TweakApplyResult applyTweaks(Object animatable, String modelId,
                                                 YsmTweakProfile profile, Player synchronizePlayer,
                                                 Bindings b) throws ReflectiveOperationException {
        ModelTweakConfig config = tweakConfig(modelId, b);
        int applied = 0;
        int skipped = 0;
        for (YsmTweakEntry entry : profile.entries()) {
            LoadedTweakForm form = config.forms().get(entry.identity());
            if (form == null) {
                tweakWarning(modelId, entry, "form no longer exists");
                skipped++;
                continue;
            }
            if (form.form().kind() != entry.kind()
                    || !form.form().variable().equals(entry.variable())) {
                tweakWarning(modelId, entry, "form type or variable changed");
                skipped++;
                continue;
            }
            String expression = expressionFor(entry, form, modelId);
            if (expression == null) {
                skipped++;
                continue;
            }
            try {
                Object parsed = b.parseExpression.invoke(null, expression);
                b.applyExpression.invoke(animatable, parsed, true, false, null);
                if (synchronizePlayer != null && Minecraft.getInstance().player == synchronizePlayer
                        && !(boolean) b.isLocalOnlyExpression.invoke(null, expression)) {
                    Object packet = b.expressionPacket.newInstance(expression, synchronizePlayer.getId());
                    b.sendYsmPacket.invoke(null, packet);
                }
                applied++;
            } catch (Throwable error) {
                tweakWarning(modelId, entry, "expression rejected: " + error.getClass().getSimpleName());
                    skipped++;
            }
        }
        return new TweakApplyResult(applied, skipped);
    }

    /** Normalizes a range value exactly as the NPC page does before it enters YSM. */
    public static double normalizeRange(YsmTweakForm.Range form, double value) {
        if (form == null || !Double.isFinite(value)) return 0.0D;
        double min = Math.min(form.min(), form.max());
        double max = Math.max(form.min(), form.max());
        double normalized = Math.max(min, Math.min(max, value));
        if (Double.isFinite(form.step()) && form.step() > 0.0D) {
            normalized = min + Math.round((normalized - min) / form.step()) * form.step();
            normalized = Math.max(min, Math.min(max, normalized));
        }
        return normalized;
    }

    public static boolean setPlayerModel(Player player, String modelId) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            if (value.isEmpty()) {
                return false;
            }
            b.setPlayerModel.invoke(value.get(), modelId, "");
            return (boolean) b.isPlayerModelValid.invoke(value.get());
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    private static String expressionFor(YsmTweakEntry entry, LoadedTweakForm loaded, String modelId) {
        YsmTweakForm form = loaded.form();
        return switch (entry.kind()) {
            case CHECKBOX -> form.variable() + '=' + (entry.booleanValue() ? '1' : '0');
            case RANGE -> {
                double value = normalizeRange((YsmTweakForm.Range) form, entry.numberValue());
                yield form.variable() + '=' + Double.toString(value);
            }
            case RADIO -> {
                String expression = loaded.radioExpressions().get(entry.choice());
                if (expression == null || expression.isBlank()) {
                    tweakWarning(modelId, entry, "radio choice no longer exists");
                    yield null;
                }
                yield expression;
            }
        };
    }

    private static ModelTweakConfig tweakConfig(String modelId, Bindings b) throws ReflectiveOperationException {
        Map<?, ?> registry = (Map<?, ?>) b.modelRegistry.invoke(null);
        Object resource = registry.get(modelId);
        if (resource == null) return ModelTweakConfig.EMPTY;
        Object metadata = resource.getClass().getMethod("Ooooo0oooO0oooOOOoO0000O").invoke(resource);
        Object properties = metadata.getClass().getMethod("o0OOooo0o0OO00OoOOOo0o0O").invoke(metadata);
        Object rawButtons = properties.getClass().getMethod("Ooooo0oooO0oooOOOoO0000O").invoke(properties);
        if (!(rawButtons instanceof Map<?, ?> buttons)) return ModelTweakConfig.EMPTY;

        List<YsmTweakGroup> groups = new ArrayList<>();
        Map<String, LoadedTweakForm> forms = new LinkedHashMap<>();
        for (Map.Entry<?, ?> mapEntry : buttons.entrySet()) {
            Object button = mapEntry.getValue();
            if (button == null) continue;
            String buttonId = stringValue(mapEntry.getKey());
            String title = stringValue(button.getClass().getMethod("o0OOooo0o0OO00OoOOOo0o0O").invoke(button));
            String description = stringValue(button.getClass().getMethod("O00OOOooOoooOoo0o0o0oO0O").invoke(button));
            Object rawForms = button.getClass().getMethod("oOOOo0OOO0ooooo0O00OO0o0").invoke(button);
            if (rawForms == null || !rawForms.getClass().isArray()) continue;

            List<YsmTweakForm> groupForms = new ArrayList<>();
            int length = Array.getLength(rawForms);
            for (int index = 0; index < length; index++) {
                Object rawForm = Array.get(rawForms, index);
                LoadedTweakForm loaded = readForm(buttonId, index, rawForm, b);
                if (loaded == null) continue;
                groupForms.add(loaded.form());
                forms.put(buttonId + '#' + index, loaded);
            }
            if (!groupForms.isEmpty()) {
                groups.add(new YsmTweakGroup(buttonId, title.isBlank() ? buttonId : title, description, groupForms));
            }
        }
        return new ModelTweakConfig(List.copyOf(groups), Map.copyOf(forms));
    }

    @SuppressWarnings("unchecked")
    private static LoadedTweakForm readForm(String buttonId, int index, Object rawForm, Bindings b)
            throws ReflectiveOperationException {
        if (rawForm == null || !b.configForm.isInstance(rawForm)) return null;
        String type = stringValue(b.formType.invoke(rawForm));
        String title = stringValue(b.formTitle.invoke(rawForm));
        String description = stringValue(b.formDescription.invoke(rawForm));
        String variable = stringValue(b.formVariable.invoke(rawForm));
        return switch (type) {
            case "checkbox" -> new LoadedTweakForm(
                    buttonId, new YsmTweakForm.Checkbox(index, title, description, variable), Map.of());
            case "range" -> {
                if (!b.rangeForm.isInstance(rawForm)) yield null;
                yield new LoadedTweakForm(buttonId, new YsmTweakForm.Range(index, title, description, variable,
                        ((Number) b.rangeStep.invoke(rawForm)).doubleValue(),
                        ((Number) b.rangeMin.invoke(rawForm)).doubleValue(),
                        ((Number) b.rangeMax.invoke(rawForm)).doubleValue()), Map.of());
            }
            case "radio" -> {
                if (!b.radioForm.isInstance(rawForm)) yield null;
                Object rawLabels = b.radioLabels.invoke(rawForm);
                if (!(rawLabels instanceof Map<?, ?> labels)) yield null;
                Map<String, String> expressions = new LinkedHashMap<>();
                for (Map.Entry<?, ?> label : labels.entrySet()) {
                    String choice = stringValue(label.getKey());
                    String expression = stringValue(label.getValue());
                    if (!choice.isBlank() && !expression.isBlank()) expressions.put(choice, expression);
                }
                yield new LoadedTweakForm(buttonId, new YsmTweakForm.Radio(index, title, description, variable,
                        List.copyOf(expressions.keySet())), Map.copyOf(expressions));
            }
            default -> null;
        };
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void tweakWarning(String modelId, YsmTweakEntry entry, String message) {
        String key = modelId + '|' + entry.identity() + '|' + message;
        if (TWEAK_WARNINGS.add(key)) {
            CustomNpcsYsmCompat.LOGGER.warn("Skipping saved YSM tweak for model {} form {}: {}",
                    modelId, entry.identity(), message);
        }
    }

    public static boolean isPlayerModelReady(Player player) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            return value.isPresent() && (boolean) b.isPlayerModelValid.invoke(value.get());
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    /** Normalizes YSM's own fake-player sync cache; it otherwise defaults food_level to zero. */
    public static int normalizePlayerState(Player player) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            if (value.isPresent()) {
                Object syncData = b.getPlayerSyncData.invoke(value.get());
                b.foodLevel.setInt(syncData, 20);
                return b.foodLevel.getInt(syncData);
            }
        } catch (Throwable error) {
            report(error);
        }
        return -1;
    }

    /** Mirrors the YSM capability tick normally received only by players loaded in a level. */
    public static void advancePlayerAnimation(Player player) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            if (value.isPresent()) b.advancePlayerAnimation.invoke(value.get());
        } catch (Throwable error) {
            report(error);
        }
    }

    /** Complete primitive/vector snapshot used only inside a bounded hurt diagnostic window. */
    public static String diagnosticSnapshot(Player player) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            if (value.isEmpty()) return "capability=missing";
            Object animatable = value.get();
            Object syncData = b.getPlayerSyncData.invoke(animatable);
            return "cap{" + shallowFields(animatable) + "},sync{" + shallowFields(syncData) + "}";
        } catch (Throwable error) {
            return "diagnostic-error=" + error.getClass().getName() + ":" + error.getMessage();
        }
    }

    private static String shallowFields(Object value) throws IllegalAccessException {
        StringBuilder out = new StringBuilder();
        Class<?> type = value.getClass();
        boolean first = true;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                String rendered = diagnosticValue(fieldValue);
                if (rendered == null) continue;
                if (!first) out.append(',');
                first = false;
                out.append(type.getSimpleName()).append('.').append(field.getName()).append('=').append(rendered);
            }
            type = type.getSuperclass();
        }
        return out.toString();
    }

    private static String diagnosticValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean || value instanceof Character
                || value instanceof String || value instanceof Enum<?> || value instanceof Vec3
                || value instanceof Vector2f || value instanceof Vector3f) return String.valueOf(value);
        if (value instanceof Collection<?> collection) return value.getClass().getSimpleName() + "[" + collection.size() + "]";
        if (value instanceof Map<?, ?> map) return value.getClass().getSimpleName() + "[" + map.size() + "]";
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> playerAnimatable(Player player, Bindings bindings) {
        return (Optional<Object>) player.getCapability((Capability<Object>) bindings.playerCapability).resolve();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean renderPlayer(RemotePlayer player, float yaw, float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffers, int packedLight) {
        try {
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
            renderer.render(player, yaw, partialTick, poseStack, buffers, packedLight);
            return true;
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    public static RenderType selectRenderType(ResourceLocation texture, boolean visible,
                                              boolean glowing, boolean customLayer,
                                              boolean partialVisibility) {
        RenderType result;
        if (partialVisibility && visible && !glowing) {
            result = RenderType.entityTranslucent(texture);
        } else if (visible) {
            if (!customLayer) {
                result = RenderType.entityCutoutNoCull(texture);
            } else {
                try {
                    result = (RenderType) bindings().customRenderType.invoke(null, texture);
                } catch (Throwable error) {
                    report(error);
                    result = RenderType.entityCutoutNoCull(texture);
                }
            }
        } else {
            result = glowing ? RenderType.outline(texture) : null;
        }
        ProxyVisibilityContext.traceRenderType(texture, visible, glowing, customLayer, result);
        return result;
    }

    private static Bindings bindings() throws ReflectiveOperationException {
        Bindings result = bindings;
        if (result != null) {
            return result;
        }
        synchronized (Ysm265Adapter.class) {
            if (bindings == null) {
                Class<?> registry = Class.forName(REGISTRY);
                Method modelRegistry = registry.getMethod("o0OOooo0o0OO00OoOOOo0o0O");

                Class<?> capHolder = Class.forName(PLAYER_CAP);
                Field capField = capHolder.getField(OBF);
                Capability<?> capability = (Capability<?>) capField.get(null);

                Class<?> animatable = Class.forName(PLAYER_ANIMATABLE);
                Method setModel = animatable.getMethod(OBF, String.class, String.class);
                Method isValid = animatable.getMethod("o0ooooOo0o000OOo0oO00OoO");
                Method getPlayerSyncData = animatable.getMethod(OBF);
                Method advancePlayerAnimation = animatable.getMethod("oOo0o0000OOOO0OooooO00oo");
                Method getPlayerModelId = animatable.getMethod("ooooO0o00oO0Oo0OOo0O0O0o");
                Field foodLevel = Class.forName(PLAYER_SYNC_DATA)
                        .getDeclaredField("o0OOO0o0o0OOo000oO00o00O");
                foodLevel.setAccessible(true);
                Method customRenderType = Class.forName(YSM_RENDER_TYPE)
                        .getMethod(OBF, ResourceLocation.class);
                Class<?> configForm = Class.forName(CONFIG_FORM);
                Class<?> rangeForm = Class.forName(RANGE_FORM);
                Class<?> radioForm = Class.forName(RADIO_FORM);
                Method formType = configForm.getMethod(OBF);
                Method formTitle = configForm.getMethod("o0OOooo0o0OO00OoOOOo0o0O");
                Method formDescription = configForm.getMethod("O00OOOooOoooOoo0o0o0oO0O");
                Method formVariable = configForm.getMethod("oOOOo0OOO0ooooo0O00OO0o0");
                Method rangeStep = rangeForm.getMethod("OOOOo0O0oO0OOo0O0O0Oo0O0");
                Method rangeMin = rangeForm.getMethod("Ooooo0oooO0oooOOOoO0000O");
                Method rangeMax = rangeForm.getMethod("oo0OoO00oOoo000O0000o0oo");
                Method radioLabels = radioForm.getMethod("OOOOo0O0oO0OOo0O0O0Oo0O0");
                Class<?> expression = Class.forName(PARSED_EXPRESSION);
                Class<?> expressionParser = Class.forName(EXPRESSION_PARSER);
                Method parseExpression = expressionParser.getMethod(OBF, String.class);
                Method isLocalOnlyExpression = expressionParser.getMethod("o0OOooo0o0OO00OoOOOo0o0O", String.class);
                Method applyExpression = Class.forName(ANIMATABLE_BASE)
                        .getMethod(OBF, expression, boolean.class, boolean.class, Consumer.class);
                Constructor<?> expressionPacket = Class.forName(EXPRESSION_PACKET)
                        .getConstructor(String.class, int.class);
                Method sendYsmPacket = Class.forName(NETWORK).getMethod(OBF, Object.class);
                bindings = new Bindings(modelRegistry, capability, setModel, isValid,
                        getPlayerSyncData, advancePlayerAnimation, getPlayerModelId, foodLevel, customRenderType,
                        configForm, rangeForm, radioForm, formType, formTitle, formDescription, formVariable,
                        rangeStep, rangeMin, rangeMax, radioLabels, parseExpression, applyExpression,
                        isLocalOnlyExpression, expressionPacket, sendYsmPacket);
            }
            return bindings;
        }
    }

    private static String displayName(Object resource, String fallback) {
        try {
            Object metadata = resource.getClass().getMethod("Ooooo0oooO0oooOOOoO0000O").invoke(resource);
            Object basic = metadata.getClass().getMethod(OBF).invoke(metadata);
            String name = (String) basic.getClass().getMethod(OBF).invoke(basic);
            return name == null || name.isBlank() ? fallback : name;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void report(Throwable error) {
        if (ERROR_REPORTED.compareAndSet(false, true)) {
            CustomNpcsYsmCompat.LOGGER.error("YSM compatibility adapter failed; affected NPCs will use their CustomNPCs model", error);
        }
    }

    private record Bindings(Method modelRegistry, Capability<?> playerCapability,
                            Method setPlayerModel, Method isPlayerModelValid,
                            Method getPlayerSyncData, Method advancePlayerAnimation, Method getPlayerModelId,
                            Field foodLevel, Method customRenderType,
                            Class<?> configForm, Class<?> rangeForm, Class<?> radioForm,
                            Method formType, Method formTitle, Method formDescription, Method formVariable,
                            Method rangeStep, Method rangeMin, Method rangeMax, Method radioLabels,
                            Method parseExpression, Method applyExpression, Method isLocalOnlyExpression,
                            Constructor<?> expressionPacket, Method sendYsmPacket) {
    }

    /** Called after resource reload/world replacement so stale model warnings may be reported again. */
    public static void clearTweakDiagnostics() {
        TWEAK_WARNINGS.clear();
    }

    public record TweakApplyResult(int applied, int skipped) {
        public static final TweakApplyResult NONE = new TweakApplyResult(0, 0);
    }

    public record CapturedPlayerTweak(String modelId, YsmTweakEntry entry) {
    }

    private record LoadedTweakForm(String buttonId, YsmTweakForm form, Map<String, String> radioExpressions) {
    }

    private record ModelTweakConfig(List<YsmTweakGroup> groups, Map<String, LoadedTweakForm> forms) {
        private static final ModelTweakConfig EMPTY = new ModelTweakConfig(List.of(), Map.of());
    }
}
