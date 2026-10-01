package org.embeddedt.embeddium.opticore.culling;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Detects whether a shader pack is currently rendering a shadow pass.
 *
 * <p>This matters because Oculus and Iris render the world a second time from the light's point of
 * view. Our frustum describes the player's view, so culling entities against it during a shadow
 * pass would delete their shadows while leaving the entity visible.
 *
 * <p>Detection goes through reflection against the public Iris API because neither Oculus nor Iris
 * is a compile-time dependency here, and because both expose the same
 * {@code net.irisshaders.iris.api.v0.IrisApi} surface. If the API is missing or changes shape, the
 * answer defaults to "not a shadow pass", which disables the suspension rather than permanently
 * disabling culling.
 */
public final class ShaderPassDetector {
    private static volatile boolean resolved;

    private static Object apiInstance;
    private static Method isShadowPassMethod;
    private static Method isShaderPackInUseMethod;
    private static Method isRenderingShadowPassMethod;

    private ShaderPassDetector() {}

    public static boolean isShadowPass() {
        ensureResolved();

        try {
            if (isShadowPassMethod != null && apiInstance != null) {
                Object result = isShadowPassMethod.invoke(apiInstance);
                if (result instanceof Boolean value) {
                    return value;
                }
            }
            // Fall back to the older method name used by early Oculus builds.
            if (isRenderingShadowPassMethod != null && apiInstance != null) {
                Object result = isRenderingShadowPassMethod.invoke(apiInstance);
                if (result instanceof Boolean value) {
                    return value;
                }
            }
        } catch (Throwable t) {
            // Deliberately silent: this runs every frame and a broken shader mod must not spam logs.
            isShadowPassMethod = null;
            isRenderingShadowPassMethod = null;
        }

        return false;
    }

    public static boolean isShaderPackActive() {
        ensureResolved();

        try {
            if (isShaderPackInUseMethod != null && apiInstance != null) {
                Object result = isShaderPackInUseMethod.invoke(apiInstance);
                if (result instanceof Boolean value) {
                    return value;
                }
            }
        } catch (Throwable t) {
            isShaderPackInUseMethod = null;
        }

        return false;
    }

    /** @return true when a shader stack is installed and its API could be reached */
    public static boolean isApiAvailable() {
        ensureResolved();
        return apiInstance != null;
    }

    private static void ensureResolved() {
        if (resolved) {
            return;
        }

        synchronized (ShaderPassDetector.class) {
            if (resolved) {
                return;
            }

            try {
                boolean modPresent;
                try {
                    modPresent = ModList.get() != null
                            && (ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris"));
                } catch (Throwable t) {
                    modPresent = false;
                }

                if (modPresent) {
                    Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    Method getInstance = apiClass.getMethod("getInstance");
                    apiInstance = getInstance.invoke(null);

                    isShadowPassMethod = findMethod(apiClass, "isRenderingShadowPass");
                    isRenderingShadowPassMethod = findMethod(apiClass, "isShadowPass");
                    isShaderPackInUseMethod = findMethod(apiClass, "isShaderPackInUse");
                }
            } catch (Throwable t) {
                apiInstance = null;
                isShadowPassMethod = null;
                isRenderingShadowPassMethod = null;
                isShaderPackInUseMethod = null;
            } finally {
                resolved = true;
            }
        }
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void reset() {
        resolved = false;
        apiInstance = null;
        isShadowPassMethod = null;
        isRenderingShadowPassMethod = null;
        isShaderPackInUseMethod = null;
    }
}
