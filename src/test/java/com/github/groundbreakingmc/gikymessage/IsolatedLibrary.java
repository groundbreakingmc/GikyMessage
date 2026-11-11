package com.github.groundbreakingmc.gikymessage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Loads a second, private copy of the library from its compiled classes, the way the class
 * loader of an application that embeds the library does. Every other class comes from the
 * test class path, except those hidden from the copy to stand in for an older dependency.
 * The copy is reached through reflection, because its types are not the ones the tests
 * were compiled against.
 */
final class IsolatedLibrary extends URLClassLoader {

    private static final String PACKAGE = Text.class.getPackageName() + '.';

    private final String[] hidden;

    /**
     * @param hidden prefixes of class names that the copy must find absent; classes outside
     *               the copy still see them
     */
    IsolatedLibrary(String... hidden) {
        super(new URL[]{Text.class.getProtectionDomain().getCodeSource().getLocation()},
                IsolatedLibrary.class.getClassLoader());
        this.hidden = hidden;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith(PACKAGE)) {
            for (final String prefix : this.hidden) {
                if (name.startsWith(prefix)) throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> type = this.findLoadedClass(name);
            if (type == null) type = this.findClass(name);
            if (resolve) this.resolveClass(type);
            return type;
        }
    }

    Class<?> type(String simpleName) throws ClassNotFoundException {
        return this.loadClass(PACKAGE + simpleName);
    }

    /** Calls {@code Text.of(raw)} of this copy. */
    Object compile(String raw) throws Exception {
        return unwrap(() -> this.type("Text").getMethod("of", String.class).invoke(null, raw));
    }

    /** Calls {@code Text.of} or {@code Text.cacheableOf} of this copy; every handler reports its own key as text. */
    Object compileBound(String raw, boolean cacheable) throws Exception {
        final Class<?> factoryType = this.type("ResolverFactory");
        final Class<?> resolverType = this.type("ValueResolver");
        final Class<?> valueType = this.type("ValueResolver$Value");
        final Object factory = Proxy.newProxyInstance(this, new Class<?>[]{factoryType}, (self, method, arguments) -> {
            final Object key = arguments[0];
            return Proxy.newProxyInstance(this, new Class<?>[]{resolverType}, (handler, call, values) ->
                    valueType.getMethod("text", String.class).invoke(values[1], key));
        });
        return unwrap(() -> this.type("Text").getMethod(cacheable ? "cacheableOf" : "of", String.class, factoryType)
                .invoke(null, raw, factory));
    }

    /** Calls {@code text.render(null)}, which runs the bound handlers. */
    Object render(Object text) throws Exception {
        return unwrap(() -> this.type("Text").getMethod("render", Object.class).invoke(text, new Object[]{null}));
    }

    /** Calls {@code text.bind(null).render()}. */
    Object renderBound(Object text) throws Exception {
        return unwrap(() -> {
            final Object binding = this.type("Text").getMethod("bind", Object.class).invoke(text, new Object[]{null});
            return this.type("BoundText").getMethod("render").invoke(binding);
        });
    }

    private static Object unwrap(Call call) throws Exception {
        try {
            return call.run();
        } catch (InvocationTargetException failure) {
            // Report what the library threw, not the reflective wrapper around it.
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    @FunctionalInterface
    private interface Call {
        Object run() throws Exception;
    }
}
