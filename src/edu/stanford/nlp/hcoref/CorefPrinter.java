package edu.stanford.nlp.hcoref;

import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Dictionaries.MentionType;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.sieve.DiscourseMatch;
import edu.stanford.nlp.hcoref.sieve.ExactStringMatch;
import edu.stanford.nlp.hcoref.sieve.PreciseConstructs;
import edu.stanford.nlp.hcoref.sieve.PronounMatch;
import edu.stanford.nlp.hcoref.sieve.RFSieve;
import edu.stanford.nlp.hcoref.sieve.RelaxedExactStringMatch;
import edu.stanford.nlp.hcoref.sieve.RelaxedHeadMatch;
import edu.stanford.nlp.hcoref.sieve.Sieve;
import edu.stanford.nlp.hcoref.sieve.SpeakerMatch;
import edu.stanford.nlp.hcoref.sieve.StrictHeadMatch1;
import edu.stanford.nlp.hcoref.sieve.StrictHeadMatch2;
import edu.stanford.nlp.hcoref.sieve.StrictHeadMatch3;
import edu.stanford.nlp.hcoref.sieve.StrictHeadMatch4;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UtteranceAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.math.NumberMatchingRegex;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

public class CorefPrinter {

  public static final DecimalFormat df = new DecimalFormat("#.####");
  
  // for debug
//  public static final ChineseHeadMatch dcorefChineseHeadMatch = new ChineseHeadMatch(StringUtils.argsToProperties(new String[]{"-coref.language", "zh"}));
  public static final SpeakerMatch dcorefSpeaker = new SpeakerMatch();
  public static final DiscourseMatch dcorefDiscourse = new DiscourseMatch();
  public static final ExactStringMatch dcorefExactString = new ExactStringMatch();
  public static final RelaxedExactStringMatch dcorefRelaxedExactString = new RelaxedExactStringMatch();
  public static final PreciseConstructs dcorefPreciseConstructs = new PreciseConstructs();
  public static final StrictHeadMatch1 dcorefHead1 = new StrictHeadMatch1();
  public static final StrictHeadMatch2 dcorefHead2 = new StrictHeadMatch2();
  public static final StrictHeadMatch3 dcorefHead3 = new StrictHeadMatch3();
  public static final StrictHeadMatch4 dcorefHead4 = new StrictHeadMatch4();
  public static final RelaxedHeadMatch dcorefRelaxedHead = new RelaxedHeadMatch();
  public static final PronounMatch dcorefPronounSieve = new PronounMatch();
  
  /** Print raw document for analysis */
  public static String printRawDoc(Document document, boolean gold, boolean printClusterID) throws FileNotFoundException {
    StringBuilder sb = new StringBuilder();
    List<CoreMap> sentences = document.annotation.get(CoreAnnotations.SentencesAnnotation.class);
    StringBuilder doc = new StringBuilder();

    for(int i = 0 ; i<sentences.size(); i++) {
      doc.append(sentenceStringWithMention(i, document, gold, printClusterID));
      doc.append('\n');
    }
    sb.append("PRINT RAW DOC START\n");
    sb.append(document.annotation.get(CoreAnnotations.DocIDAnnotation.class)).append('\n');
    if (gold) {
      sb.append("New DOC: (GOLD MENTIONS) ==================================================\n");
    } else {
      sb.append("New DOC: (Predicted Mentions) ==================================================\n");
    }
    sb.append(doc.toString()).append('\n');
    sb.append("PRINT RAW DOC END").append('\n');
    return sb.toString();
  }

  public static String printErrorLog(Mention m, Document document, Counter<Integer> probs, int mIdx, Dictionaries dict, RFSieve sieve) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("\nERROR START-----------------------------------------------------------------------\n");
    sb.append("RESOLVER TYPE: mType: ").append(sieve.mType).append(", aType: ").append(sieve.aType).append('\n');
    sb.append("DOCUMENT: ").append(document.docInfo.get("DOC_ID")).append(", ").append(document.docInfo.get("DOC_PART")).append('\n');

    List<Mention> orderedAnts = new ArrayList<>(); 
    
    sb.append("\nGOLD CLUSTER ID\n");
    for(int sentDist=m.sentNum ; sentDist >= 0  ; sentDist--) {
      if(sentDist == sieve.maxSentDist) sb.append("\tstart compare from here-------------\n");
      int sentIdx = m.sentNum-sentDist;
      sb.append("\tSENT ").append(sentIdx).append('\t').append(sentenceStringWithMention(sentIdx, document, true, true)).append('\n');
    }
    
    sb.append("\nMENTION ID\n");
    for(int sentDist=m.sentNum ; sentDist >= 0  ; sentDist--) {
      if(sentDist == sieve.maxSentDist) sb.append("\tstart compare from here-------------\n");
      int sentIdx = m.sentNum-sentDist;
      sb.append("\tSENT ").append(sentIdx).append('\t').append(sentenceStringWithMention(sentIdx, document, false, false)).append('\n');
    }

    // get dcoref antecedents ordering
    for(int sentDist=0 ; sentDist <= Math.min(sieve.maxSentDist, m.sentNum) ; sentDist++) {
      int sentIdx = m.sentNum-sentDist;
      orderedAnts.addAll(Sieve.getOrderedAntecedents(m, sentIdx, mIdx, document.predictedMentions, dict));
    }
    Map<Integer, Integer> orders = Generics.newHashMap();
    for(int i=0 ; i<orderedAnts.size() ; i++) {
      Mention ant = orderedAnts.get(i);
      orders.put(ant.mentionID, i);
    }
    
    CorefCluster mC = document.corefClusters.get(m.corefClusterID);
    
    boolean isFirstMention = isFirstMention(m, document);
    boolean foundCorefAnt = (probs.size() > 0 && Counters.max(probs) > sieve.thresMerge);
    boolean correctDecision = ( (isFirstMention && !foundCorefAnt) 
        || (foundCorefAnt && Sieve.isReallyCoref(document, m.mentionID, Counters.argmax(probs))) );
    boolean barePlural = (m.originalSpan.size()==1 && m.headWord.tag().equals("NNS"));
    if(correctDecision) return "";
    sb.append("\nMENTION: ").append(m.spanToString()).append(" (").append(m.mentionID).append(")\tperson: ").append(m.person).append("\tsingleton? ").append(!m.hasTwin).append("\t\tisFirstMention? ").append(isFirstMention).append("\t\tfoundAnt? ").append(foundCorefAnt).append("\t\tcorrectDecision? ").append(correctDecision).append("\tbarePlural? ").append(barePlural);
    sb.append("\n\ttype: ").append(m.mentionType).append("\tHeadword: ").append(m.headWord.word()).append("\tNEtype: ").append(m.nerString).append("\tnumber: ").append(m.number).append("\tgender: ").append(m.gender).append("\tanimacy: ").append(m.animacy).append('\n');
    if(m.contextParseTree!=null) sb.append(m.contextParseTree.pennString());
    
    sb.append("\n\n\t\tOracle\t\tDcoref\t\t\tRF\t\tAntecedent\n");
    for(int antID : Counters.toSortedList(probs)) {
      Mention ant = document.predictedMentionsByID.get(antID);
      CorefCluster aC = document.corefClusters.get(ant.corefClusterID);
      boolean oracle = Sieve.isReallyCoref(document, m.mentionID, antID);
      double prob = probs.getCount(antID);
      int order = orders.get(antID);
      
      String oracleStr = (oracle)? "coref   " : "notcoref";
//      String dcorefStr = (dcoref)? "coref   " : "notcoref";
      String dcorefStr = "notcoref";
      if(dcorefDiscourse.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-discourse";
//      else if(dcorefChineseHeadMatch.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-chineseHeadMatch";
      else if(dcorefExactString.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-exactString";
      else if(dcorefRelaxedExactString.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-relaxedExact";
      else if(dcorefPreciseConstructs.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-preciseConstruct";
      else if(dcorefHead1.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head1";
      else if(dcorefHead2.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head2";
      else if(dcorefHead3.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head3";
      else if(dcorefHead4.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head4";
      else if(dcorefRelaxedHead.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-relaxedHead";
      else if(dcorefPronounSieve.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-pronounSieve";
      else if(dcorefSpeaker.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-speaker";
      
      dcorefStr += '\t' +String.valueOf(order);
      String probStr = df.format(prob);
          
      sb.append("\t\t").append(oracleStr).append('\t').append(dcorefStr).append('\t').append(probStr).append("\t\t").append(ant.spanToString()).append(" (").append(ant.mentionID).append(")\n");
    }
    
    sb.append("ERROR END -----------------------------------------------------------------------\n");
    return sb.toString();
  }
  static boolean isFirstMention(Mention m, Document document) {
    if(!m.hasTwin) return true;
    Mention twinGold = document.goldMentionsByID.get(m.mentionID);
    for(Mention coref : document.goldCorefClusters.get(twinGold.goldCorefClusterID).getCorefMentions()) {
      if(coref==twinGold) continue;
      if(coref.appearEarlierThan(twinGold)) return false;
    }
    return true;
  }

  public static String sentenceStringWithMention(int i, Document document, boolean gold, boolean printClusterID) {
    StringBuilder sentStr = new StringBuilder();
    List<CoreMap> sentences = document.annotation.get(CoreAnnotations.SentencesAnnotation.class);
    List<List<Mention>> allMentions;
    if (gold) {
      allMentions = document.goldMentions;
    } else {
      allMentions = document.predictedMentions;
    }
    //    String filename = document.annotation.get()

    int previousOffset = 0;
    
    CoreMap sentence = sentences.get(i);
    List<Mention> mentions = allMentions.get(i);

    List<CoreLabel> t = sentence.get(CoreAnnotations.TokensAnnotation.class);
    String speaker = t.get(0).get(SpeakerAnnotation.class);
    if(NumberMatchingRegex.isDecimalInteger(speaker)) speaker = speaker + ": "+document.predictedMentionsByID.get(Integer.parseInt(speaker)).spanToString();
    sentStr.append("\tspeaker: ").append(speaker).append(" (").append(t.get(0).get(UtteranceAnnotation.class)).append(") ");
    String[] tokens = new String[t.size()];
    for(CoreLabel c : t) {
      tokens[c.index()-1] = c.word();
    }
//    if(previousOffset+2 < t.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) && printClusterID) {
//      sentStr.append("\n");
//    }
    previousOffset = t.get(t.size()-1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    Counter<Integer> startCounts = new ClassicCounter<>();
    Counter<Integer> endCounts = new ClassicCounter<>();
    Map<Integer, Deque<Mention>> endMentions = Generics.newHashMap();
    for (Mention m : mentions) {
//      if(!gold && (document.corefClusters.get(m.corefClusterID)==null || document.corefClusters.get(m.corefClusterID).getCorefMentions().size()<=1)) {
//        continue;
//      }
      startCounts.incrementCount(m.startIndex);
      endCounts.incrementCount(m.endIndex);
      if(!endMentions.containsKey(m.endIndex)) endMentions.put(m.endIndex, new ArrayDeque<>());
      endMentions.get(m.endIndex).push(m);
    }
    for (int j = 0 ; j < tokens.length; j++){
      if(endMentions.containsKey(j)) {
        for(Mention m : endMentions.get(j)){
          int id =  (gold)? m.goldCorefClusterID: m.corefClusterID;
          id = (printClusterID)? id : m.mentionID;
          sentStr.append("]_").append(id);
        }
      }
      for (int k = 0 ; k < startCounts.getCount(j) ; k++) {
        if (sentStr.length() > 0 && sentStr.charAt(sentStr.length()-1) != '[') sentStr.append(' ');
        sentStr.append('[');
      }
      if (sentStr.length() > 0 && sentStr.charAt(sentStr.length()-1)!='[') sentStr.append(' ');
      sentStr.append(tokens[j]);
    }
    if(endMentions.containsKey(tokens.length)) {
      for(Mention m : endMentions.get(tokens.length)){
        int id =  (gold)? m.goldCorefClusterID: m.corefClusterID;
        id = (printClusterID)? id : m.mentionID;
        sentStr.append("]_").append(id); //append("_").append(m.mentionID);
      }
    }
//    sentStr.append("\n");
    
    return sentStr.toString();
  }
  
  public static String printConllOutput(Document document, boolean gold) {
    return printConllOutput(document, gold, false);
  }

  public static String printConllOutput(Document document, boolean gold, boolean filterSingletons) {
    List<List<Mention>> orderedMentions;
    if (gold) {
      orderedMentions = document.goldMentions;
    } else {
      orderedMentions = document.predictedMentions;
    }
    if (filterSingletons) {
      orderedMentions = CorefSystem.filterMentionsWithSingletonClusters(document, orderedMentions);
    }
    return printConllOutput(document, orderedMentions, gold);
  }

  public static String printConllOutput(Document document, List<List<Mention>> orderedMentions, boolean gold)
  {
    Annotation anno = document.annotation;
    List<List<String[]>> conllDocSentences = document.conllDoc.sentenceWordLists;
    String docID = anno.get(CoreAnnotations.DocIDAnnotation.class);
    StringBuilder sb = new StringBuilder();
    sb.append("#begin document ").append(docID).append('\n');
    List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
    for(int sentNum = 0 ; sentNum < sentences.size() ; sentNum++){
      List<CoreLabel> sentence = sentences.get(sentNum).get(CoreAnnotations.TokensAnnotation.class);
      List<String[]> conllSentence = conllDocSentences.get(sentNum);
      Map<Integer,Set<Mention>> mentionBeginOnly = Generics.newHashMap();
      Map<Integer,Set<Mention>> mentionEndOnly = Generics.newHashMap();
      Map<Integer,Set<Mention>> mentionBeginEnd = Generics.newHashMap();

      for(int i=0 ; i<sentence.size(); i++){
        mentionBeginOnly.put(i, new LinkedHashSet<>());
        mentionEndOnly.put(i, new LinkedHashSet<>());
        mentionBeginEnd.put(i, new LinkedHashSet<>());
      }

      for(Mention m : orderedMentions.get(sentNum)) {
        if(m.startIndex==m.endIndex-1) {
          mentionBeginEnd.get(m.startIndex).add(m);
        } else {
          mentionBeginOnly.get(m.startIndex).add(m);
          mentionEndOnly.get(m.endIndex-1).add(m);
        }
      }

      for(int i=0 ; i<sentence.size(); i++){
        StringBuilder sb2 = new StringBuilder();
        for(Mention m : mentionBeginOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append('|');
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append('(').append(corefClusterId);
        }
        for(Mention m : mentionBeginEnd.get(i)){
          if (sb2.length() > 0) {
            sb2.append('|');
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append('(').append(corefClusterId).append(')');
        }
        for(Mention m : mentionEndOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append('|');
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append(corefClusterId).append(')');
        }
        if(sb2.length() == 0) sb2.append('-');

        String[] columns = conllSentence.get(i);
        for(int j = 0 ; j < columns.length-1 ; j++){
          String column = columns[j];
          sb.append(column).append('\t');
        }
        sb.append(sb2).append('\n');
      }
      sb.append('\n');
    }

    sb.append("#end document").append('\n');
    //    sb.append("#end document ").append(docID).append("\n");

    return sb.toString();
  }

  public static String printMentionDetectionLog(Document document) {
    StringBuilder sbLog = new StringBuilder();
    List<CoreMap> sentences = document.annotation.get(SentencesAnnotation.class);
    sbLog.append("\nERROR START-----------------------------------------------------------------------\n");
    for(int i=0 ; i < sentences.size() ; i++) {
      sbLog.append("\nSENT ").append(i).append(" GOLD   : ").append(CorefPrinter.sentenceStringWithMention(i, document, true, false)).append('\n');
      sbLog.append("SENT ").append(i).append(" PREDICT: ").append(CorefPrinter.sentenceStringWithMention(i, document, false, false)).append('\n');
      
//      for(CoreLabel cl : sentences.get(i).get(TokensAnnotation.class)) {
//        sbLog.append(cl.word()).append("-").append(cl.get(UtteranceAnnotation.class)).append("-").append(cl.get(SpeakerAnnotation.class)).append(" ");
//      }
      
      for(Mention p : document.predictedMentions.get(i)) {
        sbLog.append('\n');
        if(!p.hasTwin) sbLog.append("\tSPURIOUS");
        sbLog.append("\tmention: ").append(p.spanToString()).append("\t\t\theadword: ").append(p.headString).append("\tPOS: ").append(p.headWord.tag()).append("\tmentiontype: ").append(p.mentionType).append("\tnumber: ").append(p.number).append("\tgender: ").append(p.gender).append("\tanimacy: ").append(p.animacy).append("\tperson: ").append(p.person).append("\tNE: ").append(p.nerString);
      }
      sbLog.append('\n');
      
      for(Mention g : document.goldMentions.get(i)){
        if(!g.hasTwin) {
          sbLog.append("\tmissed gold: ").append(g.spanToString()).append("\tPOS: ").append(g.headWord.tag()).append("\tmentiontype: ").append(g.mentionType).append("\theadword: ").append(g.headString).append("\tnumber: ").append(g.number).append("\tgender: ").append(g.gender).append("\tanimacy: ").append(g.animacy).append("\tperson: ").append(g.person).append("\tNE: ").append(g.nerString).append('\n');
          if(g.sentenceWords!=null)
            if(g.sentenceWords.size() > g.endIndex) sbLog.append("\tnextword: ").append(g.sentenceWords.get(g.endIndex)).append('\t').append(g.sentenceWords.get(g.endIndex).tag()).append('\n');
          if(g.contextParseTree!=null) sbLog.append(g.contextParseTree.pennString()).append("\n\n");
          else sbLog.append("\n\n");
        }
      }
      if(sentences.get(i).get(TreeAnnotation.class)!=null) sbLog.append("\n\tparse: \n").append(sentences.get(i).get(TreeAnnotation.class).pennString());
      sbLog.append("\n\tcollapsedDependency: \n").append(sentences.get(i).get(BasicDependenciesAnnotation.class));
    }
    sbLog.append("ERROR END -----------------------------------------------------------------------\n");
    return sbLog.toString();
  }
  public static String printErrorLogDcoref(Mention m, Mention found, Document document, Dictionaries dict, int mIdx, String whichResolver) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("\nERROR START-----------------------------------------------------------------------\n");
    sb.append("RESOLVER TYPE: ").append(whichResolver).append('\n');
    sb.append("DOCUMENT: ").append(document.docInfo.get("DOC_ID")).append(", ").append(document.docInfo.get("DOC_PART")).append('\n');

    List<Mention> orderedAnts = new ArrayList<>(); 
    
    sb.append("\nGOLD CLUSTER ID\n");
    for(int sentDist=m.sentNum ; sentDist >= 0  ; sentDist--) {
      int sentIdx = m.sentNum-sentDist;
      sb.append("\tSENT ").append(sentIdx).append('\t').append(sentenceStringWithMention(sentIdx, document, true, true)).append('\n');
    }
    
    sb.append("\nMENTION ID\n");
    for(int sentDist=m.sentNum ; sentDist >= 0  ; sentDist--) {
      int sentIdx = m.sentNum-sentDist;
      sb.append("\tSENT ").append(sentIdx).append('\t').append(sentenceStringWithMention(sentIdx, document, false, false)).append('\n');
    }

    // get dcoref antecedents ordering
    for(int sentDist=0 ; sentDist <= m.sentNum ; sentDist++) {
      int sentIdx = m.sentNum-sentDist;
      orderedAnts.addAll(Sieve.getOrderedAntecedents(m, sentIdx, mIdx, document.predictedMentions, dict));
    }
    Map<Integer, Integer> orders = Generics.newHashMap();
    for(int i=0 ; i<orderedAnts.size() ; i++) {
      Mention ant = orderedAnts.get(i);
      orders.put(ant.mentionID, i);
    }
    
    CorefCluster mC = document.corefClusters.get(m.corefClusterID);
    
    boolean isFirstMention = isFirstMention(m, document);
    boolean foundCorefAnt = true;   // we're printing only mentions that found coref antecedent
    boolean correctDecision = document.isCoref(m, found);
    if(correctDecision) return "";
    sb.append("\nMENTION: ").append(m.spanToString()).append(" (").append(m.mentionID).append(")\tperson: ").append(m.person).append("\tsingleton? ").append(!m.hasTwin).append("\t\tisFirstMention? ").append(isFirstMention).append("\t\tfoundAnt? ").append(foundCorefAnt).append("\t\tcorrectDecision? ").append(correctDecision);
    sb.append("\n\ttype: ").append(m.mentionType).append("\tHeadword: ").append(m.headWord.word()).append("\tNEtype: ").append(m.nerString).append("\tnumber: ").append(m.number).append("\tgender: ").append(m.gender).append("\tanimacy: ").append(m.animacy).append('\n');
    if(m.contextParseTree!=null) sb.append(m.contextParseTree.pennString());
    
    sb.append("\n\n\t\tOracle\t\tDcoref\t\t\tRF\t\tAntecedent\n");
    for(Mention ant : orderedAnts) {
      int antID = ant.mentionID;
      CorefCluster aC = document.corefClusters.get(ant.corefClusterID);
      boolean oracle = Sieve.isReallyCoref(document, m.mentionID, antID);
      int order = orders.get(antID);
      
      String oracleStr = (oracle)? "coref   " : "notcoref";
//      String dcorefStr = (dcoref)? "coref   " : "notcoref";
      String dcorefStr = "notcoref";
      if(dcorefSpeaker.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-speaker";
//      else if(dcorefChineseHeadMatch.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-chineseHeadMatch";
      else if(dcorefDiscourse.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-discourse";
      else if(dcorefExactString.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-exactString";
      else if(dcorefRelaxedExactString.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-relaxedExact";
      else if(dcorefPreciseConstructs.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-preciseConstruct";
      else if(dcorefHead1.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head1";
      else if(dcorefHead2.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head2";
      else if(dcorefHead3.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head3";
      else if(dcorefHead4.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-head4";
      else if(dcorefRelaxedHead.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-relaxedHead";
      else if(dcorefPronounSieve.coreferent(document, mC, aC, m, ant, dict, null)) dcorefStr = "coref-pronounSieve";

      
      dcorefStr += '\t' +String.valueOf(order);
          
      sb.append("\t\t").append(oracleStr).append('\t').append(dcorefStr).append("\t\t").append(ant.spanToString()).append(" (").append(ant.mentionID).append(")\n");
    }
    
    sb.append("ERROR END -----------------------------------------------------------------------\n");
    return sb.toString();
  }

  public static void linkDistanceAnalysis(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);

    CorefSystem cs = new CorefSystem(props);
    cs.docMaker.resetDocs();
    
    Counter<Integer> proper = new ClassicCounter<>();
    Counter<Integer> common = new ClassicCounter<>();
    Counter<Integer> pronoun = new ClassicCounter<>();
    Counter<Integer> list = new ClassicCounter<>();
    
    
    while(true) {
      Document document = cs.docMaker.nextDoc();
      if(document==null) break;
      document.extractGoldCorefClusters();
      
      for(int sentIdx=0 ; sentIdx < document.predictedMentions.size() ; sentIdx++) {
        List<Mention> predictedInSent = document.predictedMentions.get(sentIdx);
        
        for(int mIdx = 0 ; mIdx < predictedInSent.size() ; mIdx++) {
          Mention m = predictedInSent.get(mIdx);
      
          loop:
          for(int distance=0 ; distance <= sentIdx ; distance++) {
            List<Mention> candidates = Sieve.getOrderedAntecedents(m, sentIdx-distance, mIdx, document.predictedMentions, cs.dictionaries);

            for(Mention candidate : candidates) {
              if(candidate == m) continue;
              if(distance==0 && m.appearEarlierThan(candidate)) continue;   // ignore cataphora
              
              if(candidate.goldCorefClusterID == m.goldCorefClusterID) {
                switch(m.mentionType) {
                  case NOMINAL:
                    if(candidate.mentionType==MentionType.NOMINAL || candidate.mentionType==MentionType.PROPER) {
                      common.incrementCount(distance);
                      break loop;
                    }
                    break;
                  case PROPER:
                    if(candidate.mentionType==MentionType.PROPER) {
                      proper.incrementCount(distance);
                      break loop;
                    }
                    break;
                  case PRONOMINAL:
                    pronoun.incrementCount(distance);
                    break loop;
                  case LIST:
                    if(candidate.mentionType==MentionType.LIST) {
                      list.incrementCount(distance);
                      break loop;
                    }
                    break;
                  default:
                    break;
                }
              }
            }   
          }
        }   
      }
    }
    System.out.println("PROPER -------------------------------------------");
    Counters.printCounterSortedByKeys(proper);
    System.out.println("COMMON -------------------------------------------");
    Counters.printCounterSortedByKeys(common);
    System.out.println("PRONOUN -------------------------------------------");
    Counters.printCounterSortedByKeys(pronoun);
    System.out.println("LIST -------------------------------------------");
    Counters.printCounterSortedByKeys(list);
    
    System.err.println();

  }
  
  public static void printScoreSummary(String summary, Logger logger, boolean afterPostProcessing) {
    String[] lines = summary.split("\n");
    if(!afterPostProcessing) {
      for(String line : lines) {
        if(line.startsWith("Identification of Mentions")) {
          Redwood.log(line);
          return;
        }
      }
    } else {
      StringBuilder sb = new StringBuilder();
      for(String line : lines) {
        if(line.startsWith("METRIC")) sb.append(line);
        if(!line.startsWith("Identification of Mentions") && line.contains("Recall")) {
          sb.append(line).append('\n');
        }
      }
      Redwood.log(sb.toString());
    }
  }
  /** Print average F1 of MUC, B^3, CEAF_E */
  public static void printFinalConllScore(String summary) {
    Pattern f1 = Pattern.compile("Coreference:.*F1: (.*)%");
    Matcher f1Matcher = f1.matcher(summary);
    double[] F1s = new double[5];
    int i = 0;
    while (f1Matcher.find()) {
      F1s[i++] = Double.parseDouble(f1Matcher.group(1));
    }
    double finalScore = (F1s[0]+F1s[1]+F1s[3])/3;
    Redwood.log("Final conll score ((muc+bcub+ceafe)/3) = " + (new DecimalFormat("#.##")).format(finalScore));
  }
  
  public static void printMentionDetection(Map<Integer, Mention> goldMentionsByID) {
    int foundGoldCount = 0;
    for(Mention g : goldMentionsByID.values()) {
      if(g.hasTwin) foundGoldCount++;
    }
    Redwood.log("debug-md", "# of found gold mentions: "+foundGoldCount + " / # of gold mentions: "+goldMentionsByID.size());
  }
  
  public static void main(String[] args) throws Exception {
    linkDistanceAnalysis(args);
  }
}
