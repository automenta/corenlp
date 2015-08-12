package edu.stanford.nlp.util;

import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.DataOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * Created by me on 8/11/15.
 */
public interface IPair<T1, T2> extends Comparable<IPair<T1, T2>>,Serializable, PrettyLoggable {
    T1 first();

    T2 second();

    List<Object> asList();

    void save(DataOutputStream out);

    @SuppressWarnings("unchecked")
    int compareTo(IPair<T1, T2> another);

    void prettyLog(Redwood.RedwoodChannels channels, String description);

    public static class MutableInternedPair extends Pair<String, String> {

      public MutableInternedPair(Pair<String, String> p) {
        super(p.first, p.second);
        internStrings();
      }

      public MutableInternedPair(String first, String second) {
        super(first, second);
        internStrings();
      }

      protected Object readResolve() {
        internStrings();
        return this;
      }

      private void internStrings() {
        if (first != null) {
          first = first.intern();
        }
        if (second != null) {
          second = second.intern();
        }
      }

      // use serialVersionUID for cross version serialization compatibility
      private static final long serialVersionUID = 1360822168806852922L;

    }

    /**
     * Compares a <code>Pair</code> to another <code>Pair</code> according to the first object of the pair only
     * This function will work providing
     * the first element of the <code>Pair</code> is comparable, otherwise will throw a
     * <code>ClassCastException</code>
     * @author jonathanberant
     *
     * @param <T1>
     * @param <T2>
     */
    public static class ByFirstPairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

      @SuppressWarnings("unchecked")
      @Override
      public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
        return ((Comparable<T1>) pair1.first()).compareTo(pair2.first());
      }
    }

    /**
     * Compares a <code>Pair</code> to another <code>Pair</code> according to the first object of the pair only in decreasing order
     * This function will work providing
     * the first element of the <code>Pair</code> is comparable, otherwise will throw a
     * <code>ClassCastException</code>
     * @author jonathanberant
     *
     * @param <T1>
     * @param <T2>
     */
    public static class ByFirstReversePairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

      @SuppressWarnings("unchecked")
      @Override
      public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
        return -((Comparable<T1>) pair1.first()).compareTo(pair2.first());
      }
    }

    /**
     * Compares a <code>Pair</code> to another <code>Pair</code> according to the second object of the pair only
     * This function will work providing
     * the first element of the <code>Pair</code> is comparable, otherwise will throw a
     * <code>ClassCastException</code>
     * @author jonathanberant
     *
     * @param <T1>
     * @param <T2>
     */
    public static class BySecondPairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

      @SuppressWarnings("unchecked")
      @Override
      public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
        return ((Comparable<T2>) pair1.second()).compareTo(pair2.second());
      }
    }

    /**
     * Compares a <code>Pair</code> to another <code>Pair</code> according to the second object of the pair only in decreasing order
     * This function will work providing
     * the first element of the <code>Pair</code> is comparable, otherwise will throw a
     * <code>ClassCastException</code>
     * @author jonathanberant
     *
     * @param <T1>
     * @param <T2>
     */
    public static class BySecondReversePairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

      @SuppressWarnings("unchecked")
      @Override
      public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
        return -((Comparable<T2>) pair1.second()).compareTo(pair2.second());
      }
    }
}
