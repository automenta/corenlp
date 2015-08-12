package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.*;

/**
 * Test for the interval tree
 *
 * @author Angel Chang
 */
public class IntervalTreeTest extends TestCase {

  private void checkOverlapping(Collection<AbstractInterval<Integer>> all,
                                Collection<AbstractInterval<Integer>> overlapping,
                                AbstractInterval<Integer> target) {
    for (AbstractInterval<Integer> interval: all) {
      assertNotNull(interval);
    }
    for (AbstractInterval<Integer> interval: overlapping) {
      assertTrue(interval.overlaps(target));
    }
    List<AbstractInterval<Integer>> rest = new ArrayList<AbstractInterval<Integer>>(all);
    rest.removeAll(overlapping);
    for (AbstractInterval<Integer> interval: rest) {
      assertNotNull(interval);
      assertFalse("Should not overlap: " + interval + " with " + target, interval.overlaps(target));
    }
  }

  public void testGetOverlapping() throws Exception
  {
    AbstractInterval<Integer> a = AbstractInterval.toInterval(249210699, 249212659);
    AbstractInterval<Integer> before = AbstractInterval.toInterval(249210000, 249210600);
    AbstractInterval<Integer> included = AbstractInterval.toInterval(249210800, 249212000);
    AbstractInterval<Integer> after = AbstractInterval.toInterval(249213000, 249214000);

    IntervalTree<Integer, AbstractInterval<Integer>> tree = new IntervalTree<Integer, AbstractInterval<Integer>>();
    tree.add(a);

    List<AbstractInterval<Integer>> overlapping1 = tree.getOverlapping(before);
    assertTrue(overlapping1.isEmpty());
    List<AbstractInterval<Integer>> overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() == 1);
    List<AbstractInterval<Integer>> overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping3.isEmpty());

    // Remove a
    tree.remove(a);
    assertTrue(tree.size() == 0);

    int n = 20000;
    // Add a bunch of interval before adding a
    for (int i = 0; i < n; i++) {
      int x = i;
      int y = i+1;
      AbstractInterval<Integer> interval = AbstractInterval.toInterval(x,y);
      tree.add(interval);
    }
    tree.add(a);
    overlapping1 = tree.getOverlapping(before);
    assertTrue(overlapping1.isEmpty());
    overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() == 1);
    overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping3.isEmpty());
    assertTrue(tree.height() < 20);

    // Try balancing the tree
//    System.out.println("Height is " + tree.height());
    tree.check();
    tree.balance();
    int height = tree.height();
    assertTrue(height < 20);
    tree.check();

    overlapping1 = tree.getOverlapping(before);
    assertTrue(overlapping1.isEmpty());
    overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() == 1);
    overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping3.isEmpty());

    // Clear tree
    tree.clear();
    assertTrue(tree.size() == 0);

    // Add a bunch of random interval before adding a

    Random rand = new Random();
    List<AbstractInterval<Integer>> list = new ArrayList<AbstractInterval<Integer>>(n+1);
    for (int i = 0; i < n; i++) {
      int x = rand.nextInt();
      int y = rand.nextInt();
      AbstractInterval<Integer> interval = AbstractInterval.toValidInterval(x,y);
      tree.add(interval);
      list.add(interval);
    }
    tree.add(a);
    list.add(a);
    overlapping1 = tree.getOverlapping(before);
    checkOverlapping(list, overlapping1, before);

    overlapping2 = tree.getOverlapping(included);
    checkOverlapping(list, overlapping2, included);

    overlapping3 = tree.getOverlapping(after);
    checkOverlapping(list, overlapping3, after);
  }

  public void testIteratorRandom() throws Exception
  {
    int n = 1000;
    IntervalTree<Integer, AbstractInterval<Integer>> tree = new IntervalTree<Integer, AbstractInterval<Integer>>();

    Random rand = new Random();
    List<IntInterval> list = new ArrayList<>(n+1);
    for (int i = 0; i < n; i++) {
      int x = rand.nextInt();
      int y = rand.nextInt();
      IntInterval interval = AbstractInterval.toValidInterval(x,y);
      tree.add(interval);
      list.add(interval);
    }

    Collections.sort(list);

    AbstractInterval<Integer> next = null;
    Iterator<AbstractInterval<Integer>> iterator = tree.iterator();
    for (int i = 0; i < list.size(); i++) {
      assertTrue("HasItem " + i, iterator.hasNext());
      next = iterator.next();
      assertEquals("Item " + i, list.get(i), next);
    }
    assertFalse("No more items", iterator.hasNext());
  }

  public void testIteratorOrdered() throws Exception
  {
    int n = 1000;
    IntervalTree<Integer, AbstractInterval<Integer>> tree = new IntervalTree<Integer, AbstractInterval<Integer>>();

    List<IntInterval> list = new ArrayList<>(n+1);
    for (int i = 0; i < n; i++) {
      int x = i;
      int y = i+1;
      IntInterval interval = AbstractInterval.toValidInterval(x,y);
      tree.add(interval);
      list.add(interval);
    }

    Collections.sort(list);

    AbstractInterval<Integer> next = null;
    Iterator<AbstractInterval<Integer>> iterator = tree.iterator();
    for (int i = 0; i < list.size(); i++) {
      assertTrue("HasItem " + i, iterator.hasNext());
      next = iterator.next();
      assertEquals("Item " + i, list.get(i), next);
    }
    assertFalse("No more items", iterator.hasNext());
  }
}
