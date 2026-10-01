package org.embeddedt.embeddium.opticore.util;

import net.minecraftforge.fml.ModList;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches the answers to "is mod X present" once at class-init, since {@link ModList} lookups are a
 * binary search and the render thread should not pay for them every frame.
 *
 * <p>Nothing here has a hard compile-time dependency on the optional mods, so a missing mod cannot
 * cause a {@code NoClassDefFoundError}.
 */
public final class ModCompat {
    private static final AtomicReference<Boolean> IRIS = new AtomicReference<>();
    private static final AtomicReference<Boolean> OCULUS = new AtomicReference<>();

    private ModCompat() {}

    /** Iris proper (Fabric builds loaded through a compatibility layer). */
    public static boolean isIrisPresent() {
        return isLoaded("iris");
    }

    /**
     * Oculus is the Forge port of Iris and shares its shader pipeline. Both matter because each
     * renders a shadow pass, where entities must not be culled by the main camera's frustum.
     */
    public static boolean isOculusPresent() {
        return isLoaded("oculus");
    }

    /** True when a shader pack could be altering the render pipeline. */
    public static boolean isShaderStackPresent() {
        return isIrisPresent() || isOculusPresent();
    }

    public static boolean isModLoaded(String modId) {
        return queryLoaded(modId);
    }

    private static boolean isLoaded(String modId) {
        AtomicReference<Boolean> cache = "oculus".equals(modId) ? OCULUS : IRIS;
        Boolean cached = cache.get();
        if (cached != null) {
            return cached;
        }

        boolean present = queryLoaded(modId);

        cache.set(present);
        return present;
    }

    private static boolean queryLoaded(String modId) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }
}
