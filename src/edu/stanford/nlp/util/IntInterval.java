package edu.stanford.nlp.util;

/**
 * Created by me on 8/11/15.
 */
public class IntInterval extends Int2 implements AbstractInterval<Integer> {

    int flags;

    public IntInterval(int a, int b) {
        super(a, b);
    }

    @Override
    public AbstractInterval<Integer> getInterval() {
        return this;
    }

    @Override
    public Integer getBegin() {
        return a;
    }

    @Override
    public Integer getEnd() {
        return b;
    }

    @Override
    public boolean contains(Integer p) {
        return a <= p && b >= p;
    }

    @Override
    public boolean containsOpen(Integer p) {
        return a < p && b > p;
    }

    @Override
    public int getFlags() {
        return getFlags();
    }



}
