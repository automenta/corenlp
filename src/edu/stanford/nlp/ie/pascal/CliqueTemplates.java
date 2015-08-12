package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.stats.DefaultCounter;

import java.util.HashMap;
import java.util.ArrayList;

/**
 * Template information and counters corresponding to sampling on one document.
 *
 * As an alternative to reading a document labelling into a full {@link PascalTemplate}
 * we can read it into partial templates which contain only strictly related information,
 * (See {@link DateTemplate} and {@link InfoTemplate}).
 *
 * @author Chris Cox
 */

public class CliqueTemplates {

  public HashMap stemmedAcronymIndex = new HashMap();
  public HashMap inverseAcronymMap = new HashMap();

  public ArrayList<String> urls = null;

  public DefaultCounter dateCliqueCounter = new DefaultCounter();
  public DefaultCounter locationCliqueCounter = new DefaultCounter();
  public DefaultCounter workshopInfoCliqueCounter = new DefaultCounter();


}
