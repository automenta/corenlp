package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.ling.CategoryWordTag;

import java.util.Set;
import java.io.PrintWriter;
import java.io.Serializable;


/**
 * Non-language-specific options for training a grammar from a treebank.
 * These options are not used at parsing time.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public class TrainOptions implements Serializable {

  public String trainTreeFile = null; // same for me -- Teg

  /* THESE OPTIONS AFFECT ONLY TRAIN TIME */

  public TrainOptions() {}

  public int trainLengthLimit = 100000;

  /** Add all test set trees to training data for PCFG.
   *  (Currently only supported in FactoredParser main.)
   */
  public boolean cheatPCFG = false;

  /** Whether to do "horizontal Markovization" (as in ACL 2003 paper).
   *  False means regular PCFG expansions.
   */
  public boolean markovFactor = false;
  public int markovOrder = 1;
  public boolean hSelSplit = false; // good with true;
  public int HSEL_CUT = 10;

  /** Whether or not to mark final states in binarized grammar.
   *  This must be off to get most value out of grammar compaction.
   */
  public boolean markFinalStates = true;

  /**
   * A POS tag has to have been attributed to more than this number of word
   * types before it is regarded as an open-class tag.  Unknown words will
   * only possibly be tagged as open-class tags (unless flexiTag is on).
   * If flexiTag is on, unknown words will be able to be tagged any POS for
   * which the unseenMap has nonzero count (that is, the tag was seen for
   * a new word after unseen signature counting was started).
   */
  public int openClassTypesThreshold = 50;

  /**
   * Start to aggregate signature-tag pairs only for words unseen in the first
   * this fraction of the data.
   */
  public double fractionBeforeUnseenCounting = 0.5;

  /**
   * If true, declare early -- leave this on except maybe with markov on.
   * @return Whether to do outside factorization in binarization of the grammar
   */
  public boolean outsideFactor() {
    return !markovFactor;
  }

  /**
   * This variable controls doing parent annotation of phrasal nodes.  Good.
   */
  public boolean PA = true;
  /**
   * This variable controls doing 2 levels of parent annotation.  Bad.
   */
  public boolean gPA = false;

  public boolean postPA = false;
  public boolean postGPA = false;

  /**
   * Only split the "common high KL divergence" parent categories.... Good.
   */
  public boolean selectiveSplit = false; //true;

  public double selectiveSplitCutOff = 0.0;

  public boolean selectivePostSplit = false;

  public double selectivePostSplitCutOff = 0.0;

  /** Whether, in post-splitting of categories, nodes are annotated with the
   *  (grand)parent's base category or with its complete subcategorized
   *  category.
   */
  public boolean postSplitWithBaseCategory = false;

  /**
   * Selective Sister annotation.
   */
  public boolean sisterAnnotate = false;

  public Set<String> sisterSplitters;

  /**
   * Mark all unary nodes specially.  Good for just PCFG. Bad for factored.
   * markUnary affects phrasal nodes. A value of 0 means to do nothing;
   * a value of 1 means to mark the parent (higher) node of a unary rewrite.
   * A value of 2 means to mark the child (lower) node of a unary rewrie.
   * Values of 1 and 2 only apply if the child (lower) node is phrasal.
   * (A value of 1 is better than 2 in combos.)  A value of 1 corresponds
   * to the old boolean -unary flag.
   */
  public int markUnary = 0;

  /** Mark POS tags which are the sole member of their phrasal constituent.
   *  This is like markUnary=2, applied to POS tags.
   */
  public boolean markUnaryTags = false;


  /**
   * Mark all pre-preterminals (also does splitBaseNP: don't need both)
   */
  public boolean splitPrePreT = false;


  /**
   * Parent annotation on tags.  Good (for PCFG?)
   */
  public boolean tagPA = false;//true;

  /**
   * Do parent annotation on tags selectively.  Neutral, but less splits.
   */
  public boolean tagSelectiveSplit = false;

  public double tagSelectiveSplitCutOff = 0.0;

  public boolean tagSelectivePostSplit = false;

  public double tagSelectivePostSplitCutOff = 0.0;

  /**
   * Right edge is right-recursive (X << X) Bad. (NP only is good)
   */
  public boolean rightRec = false;//true;

  /**
   * Left edge is right-recursive (X << X)  Bad.
   */
  public boolean leftRec = false;

  /**
   * Promote/delete punctuation like Collins.  Bad (!)
   */
  public boolean collinsPunc = false;

  /**
   * Set the splitter strings.  These are a set of parent and/or grandparent
   * annotated categories which should be split off.
   */
  public Set<String> splitters;

  public Set postSplitters;

  public Set<String> deleteSplitters;

  /**
   * Just for debugging: check that your tree transforms work correctly.  This
   * will print the transformations of the first printTreeTransformations trees.
   */
  public int printTreeTransformations = 0;

  public PrintWriter printAnnotatedPW;
  public PrintWriter printBinarizedPW;

  // todo [cdm nov 2012]: At present this does nothing. It should print the list of all states of a grammar it trains
  // Maybe just make it an anytime option and print it at the same time that verbose printing of tags is done?
  public boolean printStates = false;

  /** How to compact grammars as FSMs.
   *  0 = no compaction [uses makeSyntheticLabel1],
   *  1 = no compaction but use label names that wrap from right to left in binarization [uses makeSyntheticLabel2],
   *  2 = wrapping labels and materialize unary at top rewriting passive to active,
   *  3 = ExactGrammarCompactor,
   *  4 = LossyGrammarCompactor,
   *  5 = CategoryMergingGrammarCompactor.
   *  (May 2007 CDM note: options 4 and 5 don't seem to be functioning sensibly.  0, 1, and 3
   *  seem to be the 'good' options. 2 is only useful as input to 3.  There seems to be
   *  no reason not to use 0, despite the default.)
   */
  public int compactGrammar = 3; // exact compaction on by default

  public boolean leftToRight = false; // whether to binarize left to right or head out

  public int compactGrammar() {
    if (markovFactor) {
      return compactGrammar;
    }
    return 0;
  }

  public boolean noTagSplit = false;

  /**
   * CHANGE ANYTHING BELOW HERE AT YOUR OWN RISK
   */

  /**
   * Enables linear rule smoothing during grammar extraction
   * but before grammar compaction. The alpha term is the same
   * as that described in Petrov et al. (2006), and has range [0,1].
   */
  public boolean ruleSmoothing = false;
  public double ruleSmoothingAlpha = 0.0;

  /**
   * TODO wsg2011: This is the old grammar smoothing parameter that no
   * longer does anything in the parser. It should be removed.
   */
  public boolean smoothing = false;

  /*  public boolean factorOut = false;
  public boolean rightBonus = false;
  public boolean brokenDep = false;*/

  /** Discounts the count of BinaryRule's (only, apparently) in training data. */
  public double ruleDiscount = 0.0;

  //public boolean outsideFilter = false;

  public boolean printAnnotatedRuleCounts = false;
  public boolean printAnnotatedStateCounts = false;

  /** Where to use the basic or split tags in the dependency grammar */
  public boolean basicCategoryTagsInDependencyGrammar = false;

  /**
   * A transformer to use on the training data before any other
   * processing step.  This is specified by using the -preTransformer
   * flag when training the parser.  A comma separated list of classes
   * will be turned into a CompositeTransformer.  This can be used to
   * strip subcategories, to run a tsurgeon pattern, or any number of
   * other useful operations.
   */
  public TreeTransformer preTransformer = null;

  /**
   * A set of files to use as extra information in the lexicon.  This
   * can provide tagged words which are not part of trees
   */
  public String taggedFiles = null;

  /**
   * Use the method reported by Berkeley for splitting and recombining
   * states.  This is an experimental and still in development
   * reimplementation of that work.
   */
  public boolean predictSplits = false;

  /**
   * If we are predicting splits, we loop this many times
   */
  public int splitCount = 1;

  /**
   * If we are predicting splits, we recombine states at this rate every loop
   */
  public double splitRecombineRate = 0.0;

  /**
   * When binarizing trees, don't annotate the labels with anything
   */
  public boolean simpleBinarizedLabels = false;

  /**
   * When binarizing trees, don't binarize trees with two children.
   * Only applies when using inside markov binarization for now.
   */
  public boolean noRebinarization = false;

  /**
   * If the training algorithm allows for parallelization, how many
   * threads to use
   */
  public int trainingThreads = 1;

  /**
   * When training the DV parsing method, how many of the top K trees
   * to analyze from the underlying parser
   */
  static public final int DEFAULT_K_BEST = 100;
  public int dvKBest = DEFAULT_K_BEST;

  /**
   * When training a parsing method where the training has a (max)
   * number of iterations, how many iterations to loop
   */
  static public final int DEFAULT_TRAINING_ITERATIONS = 40;
  public int trainingIterations = DEFAULT_TRAINING_ITERATIONS;

  /**
   * When training using batches of trees, such as in the DVParser,
   * how many trees to use in one batch
   */
  static public final int DEFAULT_BATCH_SIZE = 25;
  public int batchSize = DEFAULT_BATCH_SIZE;
  /**
   * regularization constant
   */
  public static final double DEFAULT_REGCOST = 0.0001;
  public double regCost = DEFAULT_REGCOST;

  /**
   * When training the DV parsing method, how many iterations to loop
   * for one batch of trees
   */
  static public final int DEFAULT_QN_ITERATIONS_PER_BATCH = 1;
  public int qnIterationsPerBatch = DEFAULT_QN_ITERATIONS_PER_BATCH;

  /**
   * When training the DV parsing method, how many estimates to keep
   * for the qn approximation.
   */
  public int qnEstimates = 15;

  /**
   * When training the DV parsing method, the tolerance to use if we
   * want to stop qn early
   */
  public double qnTolerance = 15;

  /**
   * If larger than 0, the parser may choose to output debug information
   * every X seconds, X iterations, or some other similar metric
   */
  public int debugOutputFrequency = 0;

  public long randomSeed = 0;

  public static final double DEFAULT_LEARNING_RATE = 0.1;
  /**
   * How fast to learn (can mean different things for different algorithms)
   */
  public double learningRate = DEFAULT_LEARNING_RATE;

  public static final double DEFAULT_DELTA_MARGIN = 0.1;
  /**
   * How much to penalize the wrong trees for how different they are
   * from the gold tree when training
   */
  public double deltaMargin = DEFAULT_DELTA_MARGIN;

  /**
   * Whether or not to build an unknown word vector specifically for numbers
   */
  public boolean unknownNumberVector = true;

  /**
   * Whether or not to handle unknown dashed words by taking the last part
   */
  public boolean unknownDashedWordVectors = true;

  /**
   * Whether or not to build an unknown word vector for words with caps in them
   */
  public boolean unknownCapsVector = true;

  /**
   * Make the dv model as simple as possible
   */
  public boolean dvSimplifiedModel = false;

  /**
   * Whether or not to build an unknown word vector to match Chinese years
   */
  public boolean unknownChineseYearVector = true;

  /**
   * Whether or not to build an unknown word vector to match Chinese numbers
   */
  public boolean unknownChineseNumberVector = true;

  /**
   * Whether or not to build an unknown word vector to match Chinese percentages
   */
  public boolean unknownChinesePercentVector = true;

  public static final double DEFAULT_SCALING_FOR_INIT = 0.5;
  /**
   * How much to scale certain parameters when initializing models.
   * For example, the DVParser uses this to rescale its initial
   * matrices.
   */
  public double scalingForInit = DEFAULT_SCALING_FOR_INIT;

  public int maxTrainTimeSeconds = 0;

  public static final String DEFAULT_UNK_WORD = "*UNK*";
  /**
   * Some models will use external data sources which contain
   * information about unknown words.  This variable is a way to
   * provide the name of the unknown word in the external data source.
   */
  public String unkWord = DEFAULT_UNK_WORD;

  /**
   * Whether or not to lowercase word vectors
   */
  public boolean lowercaseWordVectors = false;

  public enum TransformMatrixType {
    DIAGONAL, RANDOM, OFF_DIAGONAL, RANDOM_ZEROS
  }

  public TransformMatrixType transformMatrixType = TransformMatrixType.DIAGONAL;

  /**
   * Specifically for the DVModel, uses words on either side of a
   * context when combining constituents.  Gives perhaps a microscopic
   * improvement in performance but causes a large slowdown.
   */
  public boolean useContextWords = false;

  /**
   * Do we want a model that uses word vectors (such as the DVParser)
   * to train those word vectors when training the model?
   * <br>
   * Note: models prior to 2014-02-13 may have incorrect values in
   * this field, as it was originally a compile time constant
   */
  public boolean trainWordVectors = true;

  public static final int DEFAULT_STALLED_ITERATION_LIMIT = 12;
  /**
   * How many iterations to allow training to stall before taking the
   * best model, if training in an iterative manner
   */
  public int stalledIterationLimit = DEFAULT_STALLED_ITERATION_LIMIT;
  
  /** Horton-Strahler number/dimension (Maximilian Schlund) */
  public boolean markStrahler;

  public void display() {
    System.err.println(toString());
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("Train parameters:\n");
    result.append(" smooth=").append(smoothing).append('\n');
    result.append(" PA=").append(PA).append('\n');
    result.append(" GPA=").append(gPA).append('\n');
    result.append(" selSplit=").append(selectiveSplit).append('\n');
    result.append(" (").append(selectiveSplitCutOff).append((deleteSplitters != null) ? ("; deleting " + deleteSplitters) : "").append(')').append('\n');
    result.append(" mUnary=").append(markUnary).append('\n');
    result.append(" mUnaryTags=").append(markUnaryTags).append('\n');
    result.append(" sPPT=").append(splitPrePreT).append('\n');
    result.append(" tagPA=").append(tagPA).append('\n');
    result.append(" tagSelSplit=").append(tagSelectiveSplit).append(" (").append(tagSelectiveSplitCutOff).append(')').append('\n');
    result.append(" rightRec=").append(rightRec).append('\n');
    result.append(" leftRec=").append(leftRec).append('\n');
    result.append(" collinsPunc=").append(collinsPunc).append('\n');
    result.append(" markov=").append(markovFactor).append('\n');
    result.append(" mOrd=").append(markovOrder).append('\n');
    result.append(" hSelSplit=").append(hSelSplit).append(" (").append(HSEL_CUT).append(')').append('\n');
    result.append(" compactGrammar=").append(compactGrammar()).append('\n');
    result.append(" postPA=").append(postPA).append('\n');
    result.append(" postGPA=").append(postGPA).append('\n');
    result.append(" selPSplit=").append(selectivePostSplit).append(" (").append(selectivePostSplitCutOff).append(')').append('\n');
    result.append(" tagSelPSplit=").append(tagSelectivePostSplit).append(" (").append(tagSelectivePostSplitCutOff).append(')').append('\n');
    result.append(" postSplitWithBase=").append(postSplitWithBaseCategory).append('\n');
    result.append(" fractionBeforeUnseenCounting=").append(fractionBeforeUnseenCounting).append('\n');
    result.append(" openClassTypesThreshold=").append(openClassTypesThreshold).append('\n');
    result.append(" preTransformer=").append(preTransformer).append('\n');
    result.append(" taggedFiles=").append(taggedFiles).append('\n');
    result.append(" predictSplits=").append(predictSplits).append('\n');
    result.append(" splitCount=").append(splitCount).append('\n');
    result.append(" splitRecombineRate=").append(splitRecombineRate).append('\n');
    result.append(" simpleBinarizedLabels=").append(simpleBinarizedLabels).append('\n');
    result.append(" noRebinarization=").append(noRebinarization).append('\n');
    result.append(" trainingThreads=").append(trainingThreads).append('\n');
    result.append(" dvKBest=").append(dvKBest).append('\n');
    result.append(" trainingIterations=").append(trainingIterations).append('\n');
    result.append(" batchSize=").append(batchSize).append('\n');
    result.append(" regCost=").append(regCost).append('\n');
    result.append(" qnIterationsPerBatch=").append(qnIterationsPerBatch).append('\n');
    result.append(" qnEstimates=").append(qnEstimates).append('\n');
    result.append(" qnTolerance=").append(qnTolerance).append('\n');
    result.append(" debugOutputFrequency=").append(debugOutputFrequency).append('\n');
    result.append(" randomSeed=").append(randomSeed).append('\n');
    result.append(" learningRate=").append(learningRate).append('\n');
    result.append(" deltaMargin=").append(deltaMargin).append('\n');
    result.append(" unknownNumberVector=").append(unknownNumberVector).append('\n');
    result.append(" unknownDashedWordVectors=").append(unknownDashedWordVectors).append('\n');
    result.append(" unknownCapsVector=").append(unknownCapsVector).append('\n');
    result.append(" unknownChineseYearVector=").append(unknownChineseYearVector).append('\n');
    result.append(" unknownChineseNumberVector=").append(unknownChineseNumberVector).append('\n');
    result.append(" unknownChinesePercentVector=").append(unknownChinesePercentVector).append('\n');
    result.append(" dvSimplifiedModel=").append(dvSimplifiedModel).append('\n');
    result.append(" scalingForInit=").append(scalingForInit).append('\n');
    result.append(" maxTrainTimeSeconds=").append(maxTrainTimeSeconds).append('\n');
    result.append(" unkWord=").append(unkWord).append('\n');
    result.append(" lowercaseWordVectors=").append(lowercaseWordVectors).append('\n');
    result.append(" transformMatrixType=").append(transformMatrixType).append('\n');
    result.append(" useContextWords=").append(useContextWords).append('\n');
    result.append(" trainWordVectors=").append(trainWordVectors).append('\n');
    result.append(" stalledIterationLimit=").append(stalledIterationLimit).append('\n');
    result.append(" markStrahler=").append(markStrahler).append('\n');
    return result.toString();
  }

  public static void printTrainTree(PrintWriter pw, String message, Tree t) {
    PrintWriter myPW;
    if (pw == null) {
      myPW = new PrintWriter(System.out, true);
    } else {
      myPW = pw;
    }
    if (message != null && pw == null) {
      // hard coded to not print message if using file output!
      myPW.println(message);
    }
    // TODO FIXME:  wtf is this shit
    boolean previousState = CategoryWordTag.printWordTag;
    CategoryWordTag.printWordTag = false;
    t.pennPrint(myPW);
    CategoryWordTag.printWordTag = previousState;
  }

  private static final long serialVersionUID = 72571349843538L;
} // end class Train
