package edu.stanford.nlp.util;

import com.gs.collections.impl.map.mutable.UnifiedMap;
import com.gs.collections.impl.set.mutable.UnifiedSet;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;
import java.util.Set;


public class DefaultCoreMap extends UnifiedMap<Class<?>, Object> implements CoreMap {

    public DefaultCoreMap(Map<? extends Class<?>, ?> map) {
        super(map);
    }

    public DefaultCoreMap() {
        super();
    }

    public DefaultCoreMap(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Copies the map, but not a deep copy.
     *
     * @return The copy
     */
    public Annotation copy() {
        return new Annotation((Map) this);
    }

    /**
     * The basic toString() method of an Annotation simply
     * prints out the text over which any annotations have
     * been made (TextAnnotation). To print all the
     * Annotation keys, use {@code toShorterString();}.
     *
     * @return The text underlying this Annotation
     */
    @Override
    public String toString() {
        Object o = this.get(CoreAnnotations.TextAnnotation.class);
        if (o == null)
            return "null";

        return o.toString();
    }


    public String toShorterString(final char sep, String... what) {
        final StringBuilder s = new StringBuilder();
        s.append('[');
        final Set<String> whatSet = UnifiedSet.newSetWith(what);
        forEachKeyValue((klass, v) -> {

            String name = ArrayCoreMap.shortNames.get(klass);

            if (name == null) {
                name = klass.getSimpleName();
                int annoIdx = name.lastIndexOf("Annotation");
                if (annoIdx >= 0) {
                    name = name.substring(0, annoIdx);
                }
                ArrayCoreMap.shortNames.put(klass, name);
            }


            if (whatSet.contains(name)) {
                if (s.length() > 1) {
                    s.append(sep);
                }
                s.append(name);
                s.append('=');
                s.append(v);
            }


        });
        s.append(']');


        return s.toString();
    }

    @Override
    public String toShorterString(String... what) {
        return toShorterString('/', what);
    }

    @Override
    public void prettyLog(Redwood.RedwoodChannels channels, String description) {

    }

    @Override
    public <VALUE> boolean has(Class<? extends Key<VALUE>> key) {
        return containsKey(key);
    }

    public <X> X get(Class<? extends Key<X>> c) {
        return (X) super.get(c);
    }

    @Override
    public <VALUE> VALUE set(Class<? extends Key<VALUE>> key, VALUE value) {
        if (value == null) {
            return (VALUE) super.remove(key);
        } else
            return (VALUE) super.put(key, value);
    }

    @Override
    public <VALUE> VALUE remove(Class<? extends Key<VALUE>> key) {
        return (VALUE) super.remove(key);
    }

    @Override
    public <VALUE> boolean containsKey(Class<? extends Key<VALUE>> key) {
        return super.containsKey(key);
    }

    public String toShortString(String... s) {
        return toShortString('/', s);
    }

    public String toShortString(char sep, String... s) {
        return toShorterString(sep, s);
    }
}
