package edu.stanford.nlp.util;

/**
* Interval with value
*
* @author Angel Chang
*/
public class ValuedInterval<T,E extends Comparable<E>> implements HasInterval<E> {
  T value;
  AbstractInterval<E> interval;

  public ValuedInterval(T value, AbstractInterval<E> interval) {
    this.value = value;
    this.interval = interval;
  }

  public T getValue() {
    return value;
  }

  public AbstractInterval<E> getInterval() {
    return interval;
  }
}
