package com.itmecasey.aeroflux.core.script;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Gives ModJS scripts a global {@code AeroFlux} (the {@link AeroFluxApi}). ModJS is optional, so everything here goes through
 * reflection: ModJS offers {@code ExtraBindings.register(BindingProvider)} and the provider's {@code install(type, cx, scope)}
 * adds globals to every script scope. Without ModJS nothing is loaded and nothing fails.
 *
 * <p>Client scripts get no global: AeroFlux has nothing to read or listen to on the client. Each time a type's scripts load,
 * the listeners of the type's previous run are dropped first.
 */
public final class ModJsBridge {
    private static final Logger LOG = Logger.getLogger("aeroflux");

    private ModJsBridge() {
    }

    /** Whether a ModJS script type (its enum constant) is the client one. */
    public static boolean clientScript(Object type) {
        return type != null && "CLIENT".equals(String.valueOf(type));
    }

    /**
     * Installs the binding when ModJS is on the classpath; returns whether it did.
     *
     * @param apiForType makes the API object for a script type name ("startup" or "server")
     */
    public static boolean install(Function<String, AeroFluxApi> apiForType) {
        return install(ModJsBridge.class.getClassLoader(), apiForType);
    }

    /** Like {@link #install(Function)}, looking for ModJS in one class loader; a loader that cannot see ModJS gives {@code false}. */
    static boolean install(ClassLoader loader, Function<String, AeroFluxApi> apiForType) {
        try {
            Class<?> provider = Class.forName("com.itmecasey.modjs.script.BindingProvider", true, loader);
            Class<?> extra = Class.forName("com.itmecasey.modjs.script.ExtraBindings", true, loader);
            Class<?> context = Class.forName("org.mozilla.javascript.Context", true, loader);
            Class<?> scriptable = Class.forName("org.mozilla.javascript.Scriptable", true, loader);
            Class<?> scriptableObject = Class.forName("org.mozilla.javascript.ScriptableObject", true, loader);
            Method javaToJs = context.getMethod("javaToJS", Object.class, scriptable);
            Method put = scriptableObject.getMethod("putProperty", scriptable, String.class, Object.class);
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("install") && args != null && args.length == 3) {
                    try {
                        if (clientScript(args[0])) {
                            return null;
                        }
                        String type = String.valueOf(args[0]).toLowerCase(Locale.ROOT);
                        ScriptEvents.clear(type);
                        put.invoke(null, args[2], "AeroFlux", javaToJs.invoke(null, apiForType.apply(type), args[2]));
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        LOG.warning("AeroFlux: could not add the AeroFlux global to a script: " + e);
                    }
                    return null;
                }
                if (name.equals("toString")) {
                    return "AeroFlux binding";
                }
                if (name.equals("hashCode")) {
                    return System.identityHashCode(proxy);
                }
                if (name.equals("equals")) {
                    return proxy == args[0];
                }
                return null;
            };
            Object binding = Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[] {provider}, handler);
            extra.getMethod("register", provider).invoke(null, binding);
            LOG.info("AeroFlux: ModJS scripts can use the AeroFlux global");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warning("AeroFlux: could not add the AeroFlux global to ModJS: " + e);
            return false;
        }
    }
}
