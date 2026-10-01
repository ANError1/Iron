package org.embeddedt.embeddium_integrity;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class TaintExplainer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Embeddium-TaintExplainer");

    private static final String CONFIG_FILE_NAME = "explainium.properties";
    private static final String KEY_ENABLED = "explainer.enabled";
    private static final String KEY_DEBUG = "explainer.debugLogging";

    private static volatile Boolean enabled;
    private static volatile Boolean debugLogging;

    private TaintExplainer() {
    }

    /**
     * {@return Whether the interpreter is enabled (falls back to the original brief warning message when false)}
     */
    public static boolean isEnabled() {
        if(enabled == null) {
            synchronized(TaintExplainer.class) {
                if(enabled == null) {
                    loadConfig();
                }
            }
        }
        return enabled;
    }

    /**
     * {@return Whether to output debugging logs (injection of whitelist mods, package name resolution, and dependency determination details)}
     */
    public static boolean isDebugEnabled() {
        if(debugLogging == null) {
            isEnabled(); // cofig loaded?
        }
        return Boolean.TRUE.equals(debugLogging);
    }

    /**
     * For a specific internal class of Embeddium, output the attribution messages grouped by mod. One message per mod:
     * Includes the mod display name/mod ID/version, mixin class name, mixin configuration file, and the reason for detection.
     * This method is called during the mixin application phase and must never throw an exception.
     *
     * @param targetClassName The name of the internal class of Embeddium being injected
     * @param attributions All mixins injected into this class and their attribution information
     */
    public static void explain(String targetClassName, List<MixinTaintDetector.MixinAttribution> attributions) {
        if(!isEnabled()) {
            return;
        }
        try {
            Map<String, Group> groups = new LinkedHashMap<>();
            for(MixinTaintDetector.MixinAttribution attribution : attributions) {
                if(attribution.status() == MixinTaintDetector.MixinSourceStatus.WHITELISTED && !isDebugEnabled()) {
                    continue;
                }
                Group group = groups.computeIfAbsent(attribution.modId() + "|" + attribution.status() + "|" + attribution.reason(),
                        k -> new Group(attribution.modId(), attribution.status(), attribution.reason(), new ArrayList<>()));
                group.mixins().add(attribution.mixin());
            }

            for(Group group : groups.values()) {
                StringBuilder message = new StringBuilder(buildHeader(group, targetClassName));
                for(IMixinInfo mixin : group.mixins()) {
                    message.append("\n    mixin ").append(readableClassName(mixin))
                            .append(" (config: ").append(configName(mixin)).append(')');
                }
                if(group.status() == MixinTaintDetector.MixinSourceStatus.TAINT) {
                    message.append("\n    reason: ").append(group.reason());
                }
                switch(group.status()) {
                    case TAINT -> LOGGER.warn("{}", message);
                    case WHITELISTED -> LOGGER.info("[Debug] {}", message);
                    default -> LOGGER.info("{}", message);
                }
            }
        } catch(Throwable t) {
            LOGGER.error("Failed to generate taint explanation for {}", targetClassName, t);
        }
    }

    private static String buildHeader(Group group, String targetClassName) {
        String modDescription = describeMod(group.modId());
        return switch(group.status()) {
            case TAINT -> modDescription + " is TAINTING Embeddium class " + targetClassName + ":";
            case ALLOWED -> modDescription + " modifies Embeddium class " + targetClassName + " (pinned dependency declared, allowed):";
            case WHITELISTED -> modDescription + " modifies Embeddium class " + targetClassName + " (whitelisted):";
            case UNKNOWN -> "An unidentifiable mod modifies Embeddium class " + targetClassName + " (could not locate the owning mod):";
        };
    }

    /**
     * {@return mod's readable description: display name (modID version); fallback to bare modID when query fails}
     */
    private static String describeMod(String modId) {
        try {
            if(!"[unknown]".equals(modId)) {
                ModFileInfo file = LoadingModList.get().getModFileById(modId);
                if(file != null && !file.getMods().isEmpty()) {
                    IModInfo info = file.getMods().stream()
                            .filter(mod -> mod.getModId().equals(modId))
                            .findFirst()
                            .orElse(file.getMods().get(0));
                    String version;
                    try {
                        version = info.getVersion().toString();
                    } catch(RuntimeException e) {
                        version = "unknown version";
                    }
                    return info.getDisplayName() + " (" + modId + " " + version + ")";
                }
            }
        } catch(RuntimeException e) {
            // return modID
        }
        return "'" + modId + "'";
    }

    private static String readableClassName(IMixinInfo mixin) {
        return mixin.getClassName().replace('/', '.');
    }

    private static String configName(IMixinInfo mixin) {
        try {
            IMixinConfig config = mixin.getConfig();
            return config != null ? config.getName() : "unknown";
        } catch(RuntimeException e) {
            return "unknown";
        }
    }

    private static void loadConfig() {
        boolean enabledValue = true;
        boolean debugValue = false;
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
            if(Files.notExists(path)) {
                writeDefaultConfig(path);
            }
            Properties props = new Properties();
            try(Reader reader = Files.newBufferedReader(path)) {
                props.load(reader);
            }
            enabledValue = parseBoolean(props.getProperty(KEY_ENABLED), true, KEY_ENABLED);
            debugValue = parseBoolean(props.getProperty(KEY_DEBUG), false, KEY_DEBUG);
        } catch(IOException | RuntimeException e) {
            LOGGER.error("Could not load {}, falling back to defaults", CONFIG_FILE_NAME, e);
        }
        enabled = enabledValue;
        debugLogging = debugValue;
    }

    private static void writeDefaultConfig(Path path) throws IOException {
        try(Writer writer = Files.newBufferedWriter(path)) {
            writer.write("# This is the configuration file for the Embeddium taint explainer.\n");
            writer.write("#\n");
            writer.write("# explainer.enabled\n");
            writer.write("#   Replaces Embeddium's generic taint warning with attribution-rich messages\n");
            writer.write("#   naming each mod that injects into Embeddium internals, and the classes\n");
            writer.write("#   involved. Set to false to restore the original short warning.\n");
            writer.write("#   Valid values: true, false\n");
            writer.write("#\n");
            writer.write("# explainer.debugLogging\n");
            writer.write("#   Enables extra INFO-level output: injections from whitelisted mods,\n");
            writer.write("#   mixin package to mod resolution, and dependency checks.\n");
            writer.write("#   Valid values: true, false\n");
            writer.write("#\n");
            writer.write(KEY_ENABLED + "=true\n");
            writer.write(KEY_DEBUG + "=false\n");
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue, String key) {
        if(value == null) {
            return defaultValue;
        }
        value = value.trim();
        if(value.equalsIgnoreCase("true")) {
            return true;
        } else if(value.equalsIgnoreCase("false")) {
            return false;
        }
        LOGGER.warn("Invalid value '{}' for key '{}' in {}, using default {}", value, key, CONFIG_FILE_NAME, defaultValue);
        return defaultValue;
    }

    /**
     *  A set of mixins with the same mod, the same judgment result, and the same cause.
     */
    private record Group(String modId, MixinTaintDetector.MixinSourceStatus status, String reason, List<IMixinInfo> mixins) {
    }
}
