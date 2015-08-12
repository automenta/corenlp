// Stanford JavaNLP support classes
// Copyright (c) 2001-2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/

package edu.stanford.nlp.stats;

import com.gs.collections.api.collection.primitive.MutableDoubleCollection;
import com.gs.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;


/**
 * A specialized kind of hash table (or map) for storing numeric counts for
 * objects. It works like a Map,
 * but with different methods for easily getting/setting/incrementing counts
 * for objects and computing various functions with the counts.
 * The Counter constructor
 * and <tt>addAll</tt> method can be used to copy another Counter's contents
 * over.
 * <p/>
 * <i>Implementation notes:</i>
 * You shouldn't casually add further methods to
 * this interface. Rather, they should be added to the {@link Counters} class.
 * Note that this class stores a
 * <code>totalCount</code> field as well as the map.  This makes certain
 * operations much more efficient, but means that any methods that change the
 * map must also update <code>totalCount</code> appropriately. If you use the
 * <code>setCount</code> method, then you cannot go wrong.
 * This class is not threadsafe: If multiple threads are accessing the same
 * counter, then access should be synchronized externally to this class.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Teg Grenager
 * @author Galen Andrew
 * @author Christopher Manning
 * @author Kayur Patel (kdpatel@cs)
 */
public class DefaultCounter<E> implements Serializable, Counter<E>, Iterable<E> {

  public final ObjectDoubleHashMap<E> map;  // accessed by DeltaCounter
  //private final MapFactory<E, MutableDouble> mapFactory;
  private double totalCount; // = 0.0
  private double defaultValue; // = 0.0;


  private static final long serialVersionUID = 4L;

  // CONSTRUCTORS



  public DefaultCounter(int initialCapacity) {
    this.map = new ObjectDoubleHashMap(initialCapacity);
  }

  /**
   * Pass in a MapFactory and the map it vends will back your Counter.
   *
   * @param mapFactory The Map this factory vends will back your Counter.
   */
  public DefaultCounter() {
    this.map = new ObjectDoubleHashMap();
  }



  /**
   * Constructs a new Counter with the contents of the given Counter.
   * <i>Implementation note:</i> A new Counter is allocated with its
   * own counts, but keys will be shared and should be an immutable class.
   *
   * @param c The Counter which will be copied.
   */
  public DefaultCounter(Counter<E> c) {
    this();
    Counters.addInPlace(this, c);
    setDefaultReturnValue(c.defaultReturnValue());
  }
  public DefaultCounter(DefaultCounter<E> c) {
    this();
    map.putAll(c.map);
    setDefaultReturnValue(c.defaultReturnValue());
  }


  /**
   * Constructs a new Counter by counting the elements in the given Collection.
   * The Counter is backed by a HashMap.
   *
   * @param collection Each item in the Collection is made a key in the
   *     Counter with count being its multiplicity in the Collection.
   */
  public DefaultCounter(Collection<E> collection) {
    this();
    for (E key : collection) {
      incrementCount(key);
    }
  }

//  public static <E> ClassicCounter<E> identityHashMapCounter() {
//    return new ClassicCounter(MapFactory.<E, MutableDouble>identityHashMapFactory());
//  }


  // STANDARD ACCESS MODIFICATION METHODS

  /** Get the MapFactory for this Counter.
   *  This method is needed by the DeltaCounter implementation.
   *
   *  @return The MapFactory
   */
//  MapFactory<E,MutableDouble> getMapFactory() {
//    return mapFactory;
//  }


  // METHODS NEEDED BY THE Counter INTERFACE

//  /** {@inheritDoc} */
//  @Override
//  public Factory<Counter<E>> getFactory() {
//    return new ClassicCounterFactory<>(getMapFactory());
//  }

//  private static class ClassicCounterFactory<E> implements Factory<Counter<E>> {
//
//    private static final long serialVersionUID = 1L;
//
//    private final MapFactory<E,MutableDouble> mf;
//
//    private ClassicCounterFactory(MapFactory<E,MutableDouble> mf) {
//      this.mf = mf;
//    }
//
//    @Override
//    public Counter<E> create() {
//      return new ClassicCounter<>(mf);
//    }
//  }



  /** {@inheritDoc} */
  @Override
  public final void setDefaultReturnValue(double rv) { defaultValue = rv; }

  /** {@inheritDoc} */
  @Override
  public double defaultReturnValue() { return defaultValue; }


  /** {@inheritDoc} */
  @Override
  public double getCount(Object key) {

    double count = map.getIfAbsent(key, Double.NaN);
    if (!Double.isFinite(count)) {
      return defaultValue; // haven't seen this object before -> default count
    }
    return count;
  }

  /** {@inheritDoc} */
  @Override public void setCount(final E key, final double count) {
    final double old = map.getIfAbsent(key, 0);
    if (old == count) return;

    map.put(key, count);

    totalCount += count - old;
  }


  /** {@inheritDoc} */
  @Override
  public double logIncrementCount(E key, double count) {

    final double old = map.getIfAbsent(key, 0);

    double newValue = SloppyMath.logAdd(old, count);
    map.put(key, newValue);

    totalCount += newValue - old;

    return newValue;
  }

  /** {@inheritDoc} */
  @Override
  public double incrementCount(E key, double count) {
    final double old = map.getIfAbsent(key, 0);

    final double newValue = old + count;
    map.put(key, newValue);

    totalCount += count;

    return newValue;
  }

  /** {@inheritDoc} */
  @Override
  public final double incrementCount(E key) {
    return incrementCount(key, 1.0);
  }

  /** {@inheritDoc} */
  @Override
  public double decrementCount(E key, double count) {
    return incrementCount(key, -count);
  }

  /** {@inheritDoc} */
  @Override
  public double decrementCount(E key) {
    return incrementCount(key, -1.0);
  }



  /** {@inheritDoc} */
  @Override
  public void addAll(Counter<E> counter) {
    Counters.addInPlace(this, counter);
  }

  /** {@inheritDoc} */
  @Override
  public double remove(E key) {
    return mutableRemove(key); // this also updates totalCount
  }

  /** {@inheritDoc} */
  @Override
  public boolean containsKey(E key) {
    return map.containsKey(key);
  }

  /** {@inheritDoc} */
  @Override
  public Set<E> keySet() {
    return map.keySet();
  }

  @Override
  public double[] valueArray() {
    return map.toArray();
  }

  @Override
  public double maxValue(double ifEmpty) {
     return map.max();
  }

  @Override
  public Collection<Double> values() {
    throw new RuntimeException("use getValues() or valueArray");
  }

  public MutableDoubleCollection getValues() {
    return map.values();
  }

  @Override
  public Set<Entry<E, Double>> entrySet() {
    throw new RuntimeException("use ObjectHashMap's forEach visitors");
  }

//  /** {@inheritDoc} */
//  @Override
//  public Collection<Double> values() {
//    return new AbstractCollection<Double>() {
//      @Override
//      public Iterator<Double> iterator() {
//        return new Iterator<Double>() {
//          Iterator<MutableDouble> inner = map.values().iterator();
//
//          @Override
//          public boolean hasNext() {
//            return inner.hasNext();
//          }
//
//          @Override
//          public Double next() {
//            // copy so as to give safety to mutable internal representation
//            return Double.valueOf(inner.next().doubleValue());
//          }
//
//          @Override
//          public void remove() {
//            throw new UnsupportedOperationException();
//          }
//        };
//      }
//
//      @Override
//      public int size() {
//        return map.size();
//      }
//
//      @Override
//      public boolean contains(Object v) {
//        return v instanceof Double && map.values().contains(new MutableDouble((Double) v));
//      }
//
//    };
//  }

//  /** {@inheritDoc} */
//  @Override
//  public Set<Map.Entry<E,Double>> entrySet() {
//    return new AbstractSet<Map.Entry<E,Double>>() {
//      @Override
//      public Iterator<Entry<E, Double>> iterator() {
//        return new Iterator<Entry<E,Double>>() {
//          final Iterator<Entry<E,MutableDouble>> inner = map.entrySet().iterator();
//
//          @Override
//          public boolean hasNext() {
//            return inner.hasNext();
//          }
//
//          @Override
//          public Entry<E, Double> next() {
//            return new Entry<E,Double>() {
//              final Entry<E,MutableDouble> e = inner.next();
//
//              public double getDoubleValue() {
//                return e.getValue().doubleValue();
//              }
//
//              public double setValue(double value) {
//                final double old = e.getValue().doubleValue();
//                e.getValue().set(value);
//                totalCount = totalCount - old + value;
//                return old;
//              }
//
//              @Override
//              public E getKey() {
//                return e.getKey();
//              }
//
//              @Override
//              public Double getValue() {
//                return getDoubleValue();
//              }
//
//              @Override
//              public Double setValue(Double value) {
//                return setValue(value.doubleValue());
//              }
//            };
//          }
//
//          @Override
//          public void remove() {
//            throw new UnsupportedOperationException();
//          }
//        };
//      }
//
//      @Override
//      public int size() {
//        return map.size();
//      }
//    };
//  }

  /** {@inheritDoc} */
  @Override
  public void clear() {
    map.clear();
    totalCount = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return map.size();
  }

  /** {@inheritDoc} */
  @Override
  public double totalCount() {
    return totalCount;
  }


  // ADDITIONAL MAP LIKE OPERATIONS (NOT IN Counter INTERFACE)
  // THEIR USE IS DISCOURAGED, BUT THEY HAVEN'T (YET) BEEN REMOVED.

  /** This is a shorthand for keySet.iterator(). It's not really clear that
   *  this method should be here, as the Map interface has no such shortcut,
   *  but it's used in a number of places, and I've left it in for now.
   *  Use is discouraged.
   *
   *  @return An Iterator over the keys in the Counter.
   */
  @Override
  public Iterator<E> iterator() {
    return keySet().iterator();
  }

  /** This is used internally to the class for getting back a
   *  MutableDouble in a remove operation.  Not for public use.
   *
   *  @param key The key to remove
   *  @return Its value as a MutableDouble
   */
  private double mutableRemove(E key) {
    double x = map.removeKeyIfAbsent(key, Double.NaN);
    if (Double.isFinite(x)) {
      totalCount -= x;
      return x;
    }
    else return defaultValue;
  }


  /**
   * Removes all the given keys from this Counter.
   * Keys may be included that are not actually in the
   * Counter - no action is taken in response to those
   * keys.  This behavior should be retained in future
   * revisions of Counter (matches HashMap).
   *
   * @param keys The keys to remove from the Counter. Their values are
   *     subtracted from the total count mass of the Counter.
   */
  public void removeAll(Collection<E> keys) {
    for (E key : keys) {
      mutableRemove(key);
    }
  }

  /** Returns whether a Counter has no keys in it.
   *
   *  @return true iff a Counter has no keys in it.
   */
  public boolean isEmpty() {
    return size() == 0;
  }


  // OBJECT STUFF

  // NOTE: Using @inheritdoc to get back to Object's javadoc doesn't work
  // on a class that implements an interface in 1.6.  Weird, but there you go.

  /** Equality is defined over all Counter implementations.
   *  Two Counters are equal if they have the same keys explicitly stored
   *  with the same values.
   *  <p>
   *  Note that a Counter with a key with value defaultReturnValue will not
   *  be judged equal to a Counter that is lacking that key. In order for
   *  two Counters to be correctly judged equal in such cases, you should
   *  call Counters.retainNonDefaultValues() on both Counters first.
   *
   *  @param o Object to compare for equality
   *  @return Whether this is equal to o
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof Counter)) {
      return false;
    } else if ( ! (o instanceof DefaultCounter)) {
      return Counters.equals(this, (Counter<E>) o);
    }

    final DefaultCounter<E> counter = (DefaultCounter<E>) o;
    return /*totalCount == counter.totalCount &&*/ map.equals(counter.map);
  }


  /** Returns a hashCode which is the underlying Map's hashCode.
   *
   *  @return A hashCode.
   */
  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /** Returns a String representation of the Counter, as formatted by
   *  the underlying Map.
   *
   *  @return A String representation of the Counter.
   */
  @Override
  public String toString() {
    return map.toString();
  }


  // EXTRA I/O METHODS

  /**
   * Returns the Counter over Strings specified by this String.
   * The String is often the whole contents of a file.
   * The file can include comments if each line of comment starts with
   * a hash (#) symbol, and does not contain any TAB characters.
   * Otherwise, the format is one entry per line.  Each line must contain
   * precisely one tab separating a key and a value, giving a format of:
   * <blockquote>
   * StringKey\tdoubleValue\n
   * </blockquote>
   *
   * @param s String representation of a Counter, where entries are one per
   *     line such that each line is either a comment (begins with #)
   *     or key \t value
   * @return The Counter with String keys
   */
  public static DefaultCounter<String> valueOfIgnoreComments(String s) {
      DefaultCounter<String> result = new DefaultCounter<>();
      String[] lines = s.split("\n");
      for (String line : lines) {
        String[] fields = line.split("\t");
        if (fields.length != 2) {
          if (line.startsWith("#")) {
            continue;
          } else {
            throw new RuntimeException("Got unsplittable line: \"" + line + '\"');
          }
        }
        result.setCount(fields[0], Double.parseDouble(fields[1]));
      }
      return result;
    }


  /**
   * Converts from the format printed by the toString method back into
   * a Counter&lt;String&gt;.  The toString() doesn't escape, so this only
   * works providing the keys of the Counter do not have commas or equals signs
   * in them.
   *
   * @param s A String representation of a Counter
   * @return The Counter
   */
  public static DefaultCounter<String> fromString(String s) {
    DefaultCounter<String> result = new DefaultCounter<>();
    if (!s.startsWith("{") || !s.endsWith("}")) {
      throw new RuntimeException("invalid format: ||"+s+"||");
    }
    s = s.substring(1, s.length()-1);
    String[] lines = s.split(", ");
    for (String line : lines) {
      String[] fields = line.split("=");
      if (fields.length!=2) throw new RuntimeException("Got unsplittable line: \"" + line + '\"');
      result.setCount(fields[0], Double.parseDouble(fields[1]));
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, Counters.asMap(this));
  }

}
