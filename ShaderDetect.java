package dev.fpsgov;

import java.lang.reflect.Method;

/** Deteksi shader pack Iris lewat refleksi (tanpa dependensi compile ke Iris). */
final class ShaderDetect {
    private static boolean resolved;
    private static Object api;
    private static Method inUse;

    private ShaderDetect() {}

    static boolean active() {
        try {
            if (!resolved) {
                resolved = true;
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            }
            if (api == null || inUse == null) return false;
            return (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            api = null;
            inUse = null;
            return false;
        }
    }
}
