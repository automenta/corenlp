package edu.stanford.nlp.util;

/**
 * Represents a interval of a generic type E that is comparable.
 * An interval is an ordered pair where the first element is less
 * than the second.
 * <p>
 * Only full intervals are currently supported
 * (i.e., both endpoints have to be specified - cannot be null).
 * <p>
 * Provides functions for computing relationships between intervals.
 * <p>
 * For flags that indicate relationship between two intervals, the following convention is used:
 * SS = relationship between start of first interval and start of second interval
 * SE = relationship between start of first interval and end of second interval
 * ES = relationship between end of first interval and start of second interval
 * EE = relationship between end of first interval and end of second interval
 *
 * @author Angel Chang
 */
public class Interval<E extends Comparable<E>> extends Pair<E, E> implements AbstractInterval<E> {

    private final int flags;


    // Flags indicating how the endpoints of two intervals
    // are related


    // Flags indicating how two intervals are related

    // Can be set with OVERLAP, INSIDE, CONTAIN

    // SS SAME or BEFORE, ES SAME or AFTER

    //  public final static int REL_FLAGS_INTERVAL_ALMOST_OVERLAP = 0x10000000;
//  public final static int REL_FLAGS_INTERVAL_ALMOST_INSIDE = 0x20000000;
//  public final static int REL_FLAGS_INTERVAL_ALMOST_CONTAIN = 0x40000000;

    protected Interval(E a, E b, int flags) {
        super(a, b);
        this.flags = flags;
        int comp = a.compareTo(b);
        if (comp > 0) {
            throw new IllegalArgumentException("Invalid interval: " + a + ',' + b);
        }
    }

    /**
     * Returns this interval.
     *
     * @return this interval
     */
    @Override
    public AbstractInterval<E> getInterval() {
        return this;
    }

    /**
     * Returns the start point.
     *
     * @return the start point of this interval
     */
    @Override
    public E getBegin() {
        return first;
    }

    /**
     * Returns the end point.
     *
     * @return the end point of this interval
     */
    @Override
    public E getEnd() {
        return second;
    }

    /**
     * Checks whether the point p is contained inside this interval
     *
     * @param p point to check
     * @return True if the point p is contained withing the interval, false otherwise
     */
    @Override
    public boolean contains(E p) {

        return

                // Check that the start point is before p
                ((includesBegin()) ? (first.compareTo(p) <= 0) : (first.compareTo(p) < 0))

                &&

                // Check that the end point is after p
                ((includesEnd()) ? (second.compareTo(p) >= 0) : (second.compareTo(p) > 0));
    }

    @Override
    public boolean containsOpen(E p) {
        // Check that the start point is before p
        boolean check1 = first.compareTo(p) <= 0;
        // Check that the end point is after p
        boolean check2 = second.compareTo(p) >= 0;
        return (check1 && check2);
    }




    @Override
    public int getFlags() {
        return flags;
    }


/*  // Returns true if end before (start of other)
  public boolean isEndBeforeBegin(Interval<E> other)
  {
    if (other == null) return false;
    int comp21 = this.second.compareTo(other.first());
    return (comp21 < 0);
  }

  // Returns true if end before or eq (start of other)
  public boolean isEndBeforeEqBegin(Interval<E> other)
  {
    if (other == null) return false;
    int comp21 = this.second.compareTo(other.first());
    return (comp21 <= 0);
  }

  // Returns true if end before or eq (start of other)
  public boolean isEndEqBegin(Interval<E> other)
  {
    if (other == null) return false;
    int comp21 = this.second.compareTo(other.first());
    return (comp21 == 0);
  }

  // Returns true if start after (end of other)
  public boolean isBeginAfterEnd(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    return (comp12 > 0);
  }

  // Returns true if start eq(end of other)
  public boolean isBeginAfterEqEnd(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    return (comp12 >= 0);
  }

  // Returns true if start eq(end of other)
  public boolean isBeginEqEnd(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    return (comp12 >= 0);
  }

  // Returns true if start is the same
  public boolean isBeginSame(Interval<E> other)
  {
    if (other == null) return false;
    int comp11 = this.first.compareTo(other.first());
    return (comp11 == 0);
  }

  // Returns true if end is the same
  public boolean isEndSame(Interval<E> other)
  {
    if (other == null) return false;
    int comp22 = this.second.compareTo(other.second());
    return (comp22 == 0);
  } */



    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        Interval interval = (Interval) o;

        if (flags != interval.flags) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + flags;
        return result;
    }

    private static final long serialVersionUID = 1;
}
