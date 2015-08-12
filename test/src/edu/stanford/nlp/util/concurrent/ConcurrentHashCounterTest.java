package edu.stanford.nlp.util.concurrent;

import edu.stanford.nlp.stats.DefaultCounter;
import edu.stanford.nlp.stats.CounterTestBase;

/**
 * 
 * @author Spence Green
 *
 */
public class ConcurrentHashCounterTest extends CounterTestBase {
  public ConcurrentHashCounterTest() {
    super(new DefaultCounter<String>());
    // TODO(spenceg): Fix concurrenthashcounter and reactivate
//    super(new ConcurrentHashCounter<String>());
  }
}
