package edu.stanford.nlp.stats;

/**
 * Tests for the ClassicCounter.
 * 
 * @author dramage
 */
public class ClassicCounterTest extends edu.stanford.nlp.stats.CounterTestBase {
  public ClassicCounterTest() {
    super(new DefaultCounter<String>());
  }
}
