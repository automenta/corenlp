package edu.stanford.nlp.util;

import java.util.Comparator;

/**
 * HasInterval interface
 *
 * @author Angel Chang
 */
public interface HasInterval<E extends Comparable<E>> {
    /**
     * Returns the interval
     *
     * @return interval
     */
    public AbstractInterval<E> getInterval();

    public final static Comparator<HasInterval<Integer>> LENGTH_COMPARATOR =
            (e1, e2) -> {
                AbstractInterval<Integer> e1v = e1.getInterval();
                int len1 = e1v.getEnd() - e1v.getBegin();
                AbstractInterval<Integer> e2v = e2.getInterval();
                int len2 = e2v.getEnd() - e2v.getBegin();
                if (len1 == len2) {
                    return 0;
                } else {
                    return (len1 > len2) ? -1 : 1;
                }
            };

    public final static Comparator<HasInterval> ENDPOINTS_COMPARATOR =
            (e1, e2) -> (e1.getInterval().compareTo(e2.getInterval()));

    public final static Comparator<HasInterval> NESTED_FIRST_ENDPOINTS_COMPARATOR =
            (e1, e2) -> {
                AbstractInterval.RelType rel = e1.getInterval().getRelation(e2.getInterval());
                if (rel.equals(AbstractInterval.RelType.CONTAIN)) {
                    return 1;
                } else if (rel.equals(AbstractInterval.RelType.INSIDE)) {
                    return -1;
                } else {
                    return (e1.getInterval().compareTo(e2.getInterval()));
                }
            };

    public final static Comparator<HasInterval> CONTAINS_FIRST_ENDPOINTS_COMPARATOR =
            (e1, e2) -> {
                AbstractInterval.RelType rel = e1.getInterval().getRelation(e2.getInterval());
                if (rel.equals(AbstractInterval.RelType.CONTAIN)) {
                    return -1;
                } else if (rel.equals(AbstractInterval.RelType.INSIDE)) {
                    return 1;
                } else {
                    return (e1.getInterval().compareTo(e2.getInterval()));
                }
            };

    public final static Comparator<HasInterval<Integer>> LENGTH_ENDPOINTS_COMPARATOR =
            Comparators.chain(HasInterval.LENGTH_COMPARATOR, HasInterval.ENDPOINTS_COMPARATOR);

}
