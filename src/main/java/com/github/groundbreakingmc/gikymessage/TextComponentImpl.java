package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.ARGBLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TextComponentImpl implements TextComponent {

    static final Component[] EMPTY_CHILDREN = new Component[0];

    private final String content;
    private final Style style;
    private final Component[] children;
    private ChildrenView childrenView;

    public TextComponentImpl(String content, Style style) {
        this(content, style, EMPTY_CHILDREN);
    }

    public TextComponentImpl(String content, Style style, Component[] children) {
        this.content = content;
        this.style = style;
        this.children = children;
    }

    /**
     * Returns the raw backing array — package-private, read-only.
     * Used by {@link Token} to construct parent-with-child components without list allocation.
     */
    Component[] childrenArray() {
        return this.children;
    }

    @Override
    public @NotNull String content() {
        return this.content;
    }

    @Override
    public @NotNull TextComponent content(@NotNull String content) {
        return new TextComponentImpl(content, this.style, Arrays.copyOf(this.children, this.children.length));
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public @NotNull Builder toBuilder() {
        return new BuilderImpl(this);
    }

    @Override
    public @Unmodifiable @NotNull List<Component> children() {
        return this.childrenView != null ? this.childrenView : (this.childrenView = new ChildrenView(this));
    }

    @Override
    public @NotNull TextComponent children(@NotNull List<? extends ComponentLike> children) {
        return new TextComponentImpl(this.content, this.style, asComponent(children).toArray(new Component[0]));
    }

    @Override
    public @NotNull Style style() {
        return this.style;
    }

    @Override
    public @NotNull TextComponent style(@NotNull Style style) {
        return new TextComponentImpl(this.content, style, Arrays.copyOf(this.children, this.children.length));
    }

    static List<Component> asComponent(List<? extends ComponentLike> likes) {
        Objects.requireNonNull(likes, "likes");
        final int size = likes.size();
        if (size == 0) {
            return Collections.emptyList();
        } else {
            ArrayList<Component> components = null;

            for (int i = 0; i < size; ++i) {
                final ComponentLike like = likes.get(i);
                if (like == null) {
                    throw new NullPointerException("likes[" + i + "]");
                }

                final Component component = like.asComponent();
                if (component != Component.empty()) {
                    if (components == null) {
                        components = new ArrayList<>(size);
                    }

                    components.add(component);
                }
            }

            if (components == null) {
                return Collections.emptyList();
            } else {
                components.trimToSize();
                return Collections.unmodifiableList(components);
            }
        }
    }

    static class BuilderImpl implements Builder {

        private String content;
        private List<Component> children = new ArrayList<>();
        private @Nullable Style style;
        private Style.@Nullable Builder styleBuilder;

        BuilderImpl(TextComponent component) {
            this.content = component.content();
            if (!component.children().isEmpty()) {
                this.children = new ArrayList<>(component.children());
            }
            if (component.hasStyling()) {
                this.style = component.style();
            }
        }

        @Override
        public @NotNull String content() {
            return this.content;
        }

        @Override
        public @NotNull Builder content(@NotNull String content) {
            this.content = Objects.requireNonNull(content, "content can't be null");
            return this;
        }

        @Override
        public @NotNull Builder append(@NotNull Component component) {
            this.children.add(component);
            return this;
        }

        @Override
        public @NotNull Builder append(@NotNull Component @NotNull ... components) {
            this.children.addAll(List.of(components));
            return this;
        }

        @Override
        public @NotNull Builder append(@NotNull ComponentLike @NotNull ... components) {
            this.children.addAll(TextComponentImpl.asComponent(List.of(components)));
            return this;
        }

        @Override
        public @NotNull Builder append(@NotNull Iterable<? extends ComponentLike> iterable) {
            for (final ComponentLike componentLike : iterable) {
                this.children.add(componentLike.asComponent());
            }
            return this;
        }

        @Override
        @SuppressWarnings("deprecation")
        public @NotNull Builder applyDeep(@NotNull Consumer<? super ComponentBuilder<?, ?>> action) {
            this.apply(action);
            if (!this.children.isEmpty()) {
                for (int i = 0; i < this.children.size(); i++) {
                    final Component child = this.children.get(i);
                    if (child instanceof BuildableComponent) {
                        final ComponentBuilder<?, ?> childBuilder = ((BuildableComponent<?, ?>) child).toBuilder();
                        childBuilder.applyDeep(action);
                        this.children.set(i, childBuilder.build());
                    }
                }
            }
            return this;
        }

        @Override
        @SuppressWarnings("deprecation")
        public @NotNull Builder mapChildren(@NotNull Function<BuildableComponent<?, ?>, ? extends BuildableComponent<?, ?>> function) {
            if (!this.children.isEmpty()) {
                for (int i = 0; i < this.children.size(); i++) {
                    final Component child = this.children.get(i);
                    if (child instanceof BuildableComponent) {
                        final BuildableComponent<?, ?> mappedChild = Objects.requireNonNull((BuildableComponent<?, ?>) function.apply((BuildableComponent<?, ?>) child), "mappedChild");
                        if (child != mappedChild) {
                            this.children.set(i, mappedChild);
                        }
                    }
                }
            }
            return this;
        }

        @Override
        @SuppressWarnings("deprecation")
        public @NotNull Builder mapChildrenDeep(@NotNull Function<BuildableComponent<?, ?>, ? extends BuildableComponent<?, ?>> function) {
            if (!this.children.isEmpty()) {
                for (int i = 0; i < this.children.size(); i++) {
                    final Component child = this.children.get(i);
                    if (child instanceof BuildableComponent) {
                        final BuildableComponent<?, ?> mappedChild = Objects.requireNonNull((BuildableComponent<?, ?>) function.apply((BuildableComponent<?, ?>) child), "mappedChild");
                        if (mappedChild.children().isEmpty()) {
                            if (child != mappedChild) {
                                this.children.set(i, mappedChild);
                            }
                        } else {
                            final ComponentBuilder<?, ?> builder = mappedChild.toBuilder();
                            builder.mapChildrenDeep(function);
                            this.children.set(i, builder.build());
                        }
                    }
                }
            }
            return this;
        }

        @Override
        public @NotNull List<Component> children() {
            return Collections.unmodifiableList(this.children);
        }

        @Override
        public @NotNull Builder style(@NotNull Style style) {
            this.style = style;
            this.styleBuilder = null;
            return this;
        }

        @Override
        public @NotNull Builder style(@NotNull Consumer<Style.Builder> consumer) {
            consumer.accept(this.styleBuilder());
            return this;
        }

        @Override
        public @NotNull Builder font(@Nullable Key font) {
            this.styleBuilder().font(font);
            return this;
        }

        @Override
        public @NotNull Builder color(@Nullable TextColor color) {
            this.styleBuilder().color(color);
            return this;
        }

        @Override
        public @NotNull Builder colorIfAbsent(@Nullable TextColor color) {
            this.styleBuilder().colorIfAbsent(color);
            return this;
        }

        @Override
        public @NotNull Builder shadowColor(@Nullable ARGBLike argb) {
            this.styleBuilder().shadowColor(argb);
            return this;
        }

        @Override
        public @NotNull Builder shadowColorIfAbsent(@Nullable ARGBLike argb) {
            this.styleBuilder().shadowColorIfAbsent(argb);
            return this;
        }

        @Override
        public @NotNull Builder decoration(@NotNull TextDecoration decoration, TextDecoration.@NotNull State state) {
            this.styleBuilder().decoration(decoration, state);
            return this;
        }

        @Override
        public @NotNull Builder decorationIfAbsent(@NotNull TextDecoration decoration, TextDecoration.@NotNull State state) {
            this.styleBuilder().decorationIfAbsent(decoration, state);
            return this;
        }

        @Override
        public @NotNull Builder clickEvent(@Nullable ClickEvent event) {
            this.styleBuilder().clickEvent(event);
            return this;
        }

        @Override
        public @NotNull Builder hoverEvent(@Nullable HoverEventSource<?> source) {
            this.styleBuilder().hoverEvent(source);
            return this;
        }

        @Override
        public @NotNull Builder insertion(@Nullable String insertion) {
            this.styleBuilder().insertion(insertion);
            return this;
        }

        @Override
        public @NotNull Builder mergeStyle(@NotNull Component that, @NotNull Set<Style.Merge> merges) {
            final Style thatStyle = Objects.requireNonNull(that, "that can't be null").style();
            if (!thatStyle.isEmpty() || !merges.isEmpty()) {
                this.styleBuilder().merge(thatStyle, merges);
            }
            return this;
        }

        @Override
        public @NotNull Builder resetStyle() {
            this.style = null;
            this.styleBuilder = null;
            return this;
        }

        private Style.@NotNull Builder styleBuilder() {
            if (this.styleBuilder == null) {
                if (this.style != null) {
                    this.styleBuilder = this.style.toBuilder();
                    this.style = null;
                } else {
                    this.styleBuilder = Style.style();
                }
            }

            return this.styleBuilder;
        }

        protected final boolean hasStyle() {
            return this.styleBuilder != null || this.style != null;
        }

        protected @NotNull Style buildStyle() {
            if (this.styleBuilder != null) {
                return this.styleBuilder.build();
            } else {
                return this.style != null ? this.style : Style.empty();
            }
        }

        @Override
        public @NotNull TextComponent build() {
            return null;
        }
    }

    static final class ChildrenView implements List<Component> {

        private final TextComponentImpl owner;

        ChildrenView(TextComponentImpl owner) {
            this.owner = owner;
        }

        @Override
        public int size() {
            return this.owner.children.length;
        }

        @Override
        public boolean isEmpty() {
            return this.size() == 0;
        }

        @Override
        public boolean contains(Object o) {
            for (int i = 0; i < this.owner.children.length; i++) {
                if (this.owner.children[i].equals(o)) return true;
            }
            return false;
        }

        @NotNull
        @Override
        public Iterator<Component> iterator() {
            return new Iterator<>() {

                int pos;

                @Override
                public boolean hasNext() {
                    return ChildrenView.this.owner.children.length > this.pos;
                }

                @Override
                public Component next() {
                    return ChildrenView.this.owner.children[this.pos++];
                }
            };
        }

        @Override
        public @NotNull Object @NotNull [] toArray() {
            return Arrays.copyOf(this.owner.children, this.owner.children.length);
        }

        @Override
        @SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
        public @NotNull <T> T[] toArray(@NotNull T @NotNull [] a) {
            final int size = this.size();
            if (a.length < size)
                return (T[]) Arrays.copyOf(this.owner.children, size, a.getClass());
            System.arraycopy(this.owner.children, 0, a, 0, size);
            if (a.length > size)
                a[size] = null;
            return a;
        }

        @Override
        public boolean add(Component component) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            for (final Object o : c) {
                if (!this.contains(o)) return false;
            }
            return true;
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Component> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(int index, @NotNull Collection<? extends Component> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Component get(int index) {
            return this.owner.children[index];
        }

        @Override
        public Component set(int index, Component element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int index, Component element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Component remove(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int indexOf(Object o) {
            for (int i = 0; i < this.owner.children.length; i++) {
                if (this.owner.children[i].equals(o)) return i;
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = this.owner.children.length - 1; i >= 0; i--) {
                if (this.owner.children[i].equals(o)) return i;
            }
            return -1;
        }

        @NotNull
        @Override
        public ListIterator<Component> listIterator() {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public ListIterator<Component> listIterator(int index) {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public List<Component> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }
    }
}
