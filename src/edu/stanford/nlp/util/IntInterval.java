package edu.stanford.nlp.util;

/**
 * Created by me on 8/11/15.
 */
public class IntInterval extends Int2 implements AbstractInterval<Integer> {

    final int flags;

    public IntInterval(int a, int b) {
        this(a, b, 0);
    }
    public IntInterval(int a, int b, int flags) {
        super(a, b);
        this.flags = flags;
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
        while (true) {

        }
    }



}
