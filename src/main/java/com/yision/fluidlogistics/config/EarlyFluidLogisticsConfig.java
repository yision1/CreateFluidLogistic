package com.yision.fluidlogistics.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;

/**
 * Minimal early-stage config reader for mixin plugin decisions.
 * Only reads {@code advancedLogisticsNetworkEnabled} from the common config.
 * Must not reference mod main class, registry classes, or Minecraft classes.
 */
public final class EarlyFluidLogisticsConfig {

    private static final String CONFIG_FILE = "config/fluidlogistics-common.toml";
    private static final String KEY = "advancedLogisticsNetworkEnabled";
    private static final boolean DEFAULT = true;

    private static boolean cachedValue = DEFAULT;
    private static boolean initialized = false;

    private EarlyFluidLogisticsConfig() {
    }

    public static boolean advancedLogisticsNetworkEnabled() {
        if (!initialized) {
            cachedValue = readFromDisk();
            initialized = true;
        }
        return cachedValue;
    }

    private static boolean readFromDisk() {
        Path path = Path.of(CONFIG_FILE);
        if (!Files.isRegularFile(path)) {
            return DEFAULT;
        }
        try (var reader = Files.newBufferedReader(path)) {
            Config config = new TomlParser().parse(reader);
            Boolean value = config.getOrElse(KEY, DEFAULT);
            return value != null ? value : DEFAULT;
        } catch (IOException | ParsingException | IllegalStateException e) {
            return DEFAULT;
        }
    }
}
