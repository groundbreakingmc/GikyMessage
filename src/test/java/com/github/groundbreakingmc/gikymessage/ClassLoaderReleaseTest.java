package com.github.groundbreakingmc.gikymessage;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A thread that compiled or rendered a text keeps running after the application that embeds
 * the library is unloaded, for example a server's main thread after a plugin is disabled.
 * Whatever that thread still holds must not keep the library's class loader alive.
 */
class ClassLoaderReleaseTest {

    @Test
    void isolatedCopyIsSeparateAndRendersTheSameText() throws Exception {
        try (IsolatedLibrary library = new IsolatedLibrary()) {
            assertNotSame(Text.class, library.type("Text"));
            assertEquals(Text.of("&aHello &b{name}").render(), library.render(library.compile("&aHello &b{name}")));
        }
    }

    @Test
    void compilingStaticContentReleasesTheLibrary() throws Exception {
        // Static children are rendered once, while compiling, through the per-thread child buffers.
        assertReleased(library -> library.compile("&aHello &bWorld [gradient](gradient:#ff0000-#0000ff)"));
    }

    @Test
    void renderingWithHandlersReleasesTheLibrary() throws Exception {
        assertReleased(library -> library.render(library.compileBound("&a{name} &b{health}", false)));
    }

    @Test
    void lastResultCacheReleasesTheLibrary() throws Exception {
        assertReleased(library -> {
            final Object text = library.compileBound("&a{name} &b{health}", true);
            library.render(text);
            library.render(text);
        });
    }

    @Test
    void bindingReleasesTheLibrary() throws Exception {
        assertReleased(library -> library.renderBound(library.compileBound("[{name}](gradient:#ff0000-#0000ff)", false)));
    }

    private static void assertReleased(Use use) throws Exception {
        final WeakReference<ClassLoader> library = load(use);
        for (int attempt = 0; attempt < 100 && library.get() != null; attempt++) {
            System.gc();
            Thread.sleep(10);
        }
        assertNull(library.get(), "this thread still keeps the library's class loader reachable");
    }

    // A frame of its own, so that no local variable of the test refers to the loader afterwards.
    private static WeakReference<ClassLoader> load(Use use) throws Exception {
        try (IsolatedLibrary library = new IsolatedLibrary()) {
            use.accept(library);
            return new WeakReference<>(library);
        }
    }

    @FunctionalInterface
    private interface Use {
        void accept(IsolatedLibrary library) throws Exception;
    }
}
