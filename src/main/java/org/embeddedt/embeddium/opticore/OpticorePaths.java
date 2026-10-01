package org.embeddedt.embeddium.opticore;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Resolves the game's config directory without depending on a mod loader abstraction, so this
 * package stays usable from mixin plugins that run before the mod container is fully initialised.
 */
public final class OpticorePaths {
    private OpticorePaths() {}

    public static Path configDir() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            return Path.of("config");
        }
    }
}
