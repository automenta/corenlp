package edu.stanford.nlp.parser.nndep;

import com.gs.collections.api.set.primitive.MutableIntSet;
import com.gs.collections.impl.list.mutable.primitive.IntArrayList;
import com.gs.collections.impl.map.mutable.primitive.IntIntHashMap;
import com.gs.collections.impl.set.mutable.primitive.IntHashSet;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Neural network classifier which powers a transition-based dependency
 * parser.
 * <p>
 * This classifier is built to accept distributed-representation
 * inputs, and feeds back errors to these input layers as it learns.
 * <p>
 * In order to train a classifier, instantiate this class using the
 * {@link #Classifier(Config, Dataset, double[][], double[][], double[], double[][], java.util.List)}
 * constructor. (The presence of a non-null dataset signals that we
 * wish to train.) After training by alternating calls to
 * {@link #computeCostFunction(int, double, double)} and,
 * {@link #takeAdaGradientStep(edu.stanford.nlp.parser.nndep.Classifier.Cost, double, double)},
 * be sure to call {@link #finalizeTraining()} in order to allow the
 * classifier to clean up resources used during training.
 *
 * @author Danqi Chen
 * @author Jon Gauthier
 */
public class Classifier {
    // E: numFeatures x embeddingSize
    // W1: hiddenSize x (embeddingSize x numFeatures)
    // b1: hiddenSize
    // W2: numLabels x hiddenSize

    // Weight matrices
    private final double[][] W1, W2, E;
    private final double[] b1;

    // Global gradSaved
    private double[][] gradSaved;

    // Gradient histories
    private double[][] eg2W1, eg2W2, eg2E;
    private double[] eg2b1;

    /**
     * Pre-computed hidden layer unit activations. Each double array
     * within this data is an entire hidden layer. The sub-arrays are
     * indexed somewhat arbitrarily; in order to find hidden-layer unit
     * activations for a given feature ID, use {@link #preMap} to find
     * the proper index into this data.
     */
    private double[][] saved;

    /**
     * Describes features which should be precomputed. Each entry maps a
     * feature ID to its destined index in the saved hidden unit
     * activation data (see {@link #saved}).
     */
    private final IntIntHashMap preMap;

    /**
     * Initial training state is dependent on how the classifier is
     * initialized. We use this flag to determine whether calls to
     * {@link #computeCostFunction(int, double, double)}, etc. are valid.
     */
    private boolean isTraining;

    /**
     * All training examples.
     */
    private final Dataset dataset;

    /**
     * We use MulticoreWrapper to parallelize mini-batch training.
     * <p>
     * Threaded job input: partition of minibatch;
     * current weights + params
     * Threaded job output: cost value, weight gradients for partition of
     * minibatch
     */
    private final MulticoreWrapper<Pair<Collection<Example>, FeedforwardParams>, Cost> jobHandler;

    private final Config config;

    /**
     * Number of possible dependency relation labels among which this
     * classifier will choose.
     */
    public final int numLabels;

    /**
     * Instantiate a classifier with previously learned parameters in
     * order to perform new inference.
     *
     * @param config
     * @param E
     * @param W1
     * @param b1
     * @param W2
     * @param preComputed
     */
    public Classifier(Config config, double[][] E, double[][] W1, double[] b1, double[][] W2, IntArrayList preComputed) {
        this(config, null, E, W1, b1, W2, preComputed);
    }

    /**
     * Instantiate a classifier with training data and randomly
     * initialized parameter matrices in order to begin training.
     *
     * @param config
     * @param dataset
     * @param E
     * @param W1
     * @param b1
     * @param W2
     * @param preComputed
     */
    public Classifier(Config config, Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2,
                      IntArrayList preComputed) {
        this.config = config;
        this.dataset = dataset;

        this.E = E;
        this.W1 = W1;
        this.b1 = b1;
        this.W2 = W2;

        initGradientHistories();

        numLabels = W2.length;

        preMap = new IntIntHashMap();
        for (int i = 0; i < preComputed.size() && i < config.numPreComputed; ++i)
            preMap.put(preComputed.get(i), i);

        preMap.compact();

        isTraining = dataset != null;
        if (isTraining)
            jobHandler = new MulticoreWrapper<>(config.trainingThreads, new CostFunction(), false);
        else
            jobHandler = null;
    }

    /**
     * Evaluates the training cost of a particular subset of training
     * examples given the current learned weights.
     * <p>
     * This function will be evaluated in parallel on different data in
     * separate threads, and accesses the classifier's weights stored in
     * the outer class instance.
     * <p>
     * Each nested class instance accumulates its own weight gradients;
     * these gradients will be merged on a main thread after all cost
     * function runs complete.
     *
     * @see #computeCostFunction(int, double, double)
     */
    private class CostFunction implements ThreadsafeProcessor<Pair<Collection<Example>, FeedforwardParams>, Cost> {

        private double[][] gradW1;
        private double[] gradb1;
        private double[][] gradW2;
        private double[][] gradE;

        @Override
        public Cost process(Pair<Collection<Example>, FeedforwardParams> input) {
            Collection<Example> examples = input.first();
            FeedforwardParams params = input.second();

            // We can't fix the seed used with ThreadLocalRandom
            // TODO: Is this a serious problem?
            ThreadLocalRandom random = ThreadLocalRandom.current();

            gradW1 = new double[W1.length][W1[0].length];
            gradb1 = new double[b1.length];
            gradW2 = new double[W2.length][W2[0].length];
            gradE = new double[E.length][E[0].length];

            double cost = 0.0;
            double correct = 0.0;

            for (Example ex : examples) {
                List<Integer> feature = ex.getFeature();
                List<Integer> label = ex.getLabel();

                double[] scores = new double[numLabels];
                double[] hidden = new double[config.hiddenSize];
                double[] hidden3 = new double[config.hiddenSize];

                // Run dropout: randomly drop some hidden-layer units. `ls`
                // contains the indices of those units which are still active
                int[] ls = IntStream.range(0, config.hiddenSize)
                        .filter(n -> random.nextDouble() > params.getDropOutProb())
                        .toArray();

                int offset = 0;
                for (int j = 0; j < config.numTokens; ++j) {
                    int tok = feature.get(j);
                    int index = tok * config.numTokens + j;

                    int id;
                    if ( (id = preMap.getIfAbsent(index, -1))!=-1 )  {
                        // Unit activations for this input feature value have been
                        // precomputed

                        // Only extract activations for those nodes which are still
                        // activated (`ls`)
                        for (int nodeIndex : ls)
                            hidden[nodeIndex] += saved[id][nodeIndex];
                    } else {
                        for (int nodeIndex : ls) {
                            for (int k = 0; k < config.embeddingSize; ++k)
                                hidden[nodeIndex] += W1[nodeIndex][offset + k] * E[tok][k];
                        }
                    }
                    offset += config.embeddingSize;
                }

                // Add bias term and apply activation function
                for (int nodeIndex : ls) {
                    hidden[nodeIndex] += b1[nodeIndex];
                    hidden3[nodeIndex] = Math.pow(hidden[nodeIndex], 3);
                }

                // Feed forward to softmax layer (no activation yet)
                int optLabel = -1;
                for (int i = 0; i < numLabels; ++i) {
                    if (label.get(i) >= 0) {
                        for (int nodeIndex : ls)
                            scores[i] += W2[i][nodeIndex] * hidden3[nodeIndex];

                        if (optLabel < 0 || scores[i] > scores[optLabel])
                            optLabel = i;
                    }
                }

                double sum1 = 0.0;
                double sum2 = 0.0;
                double maxScore = scores[optLabel];
                for (int i = 0; i < numLabels; ++i) {
                    if (label.get(i) >= 0) {
                        scores[i] = Math.exp(scores[i] - maxScore);
                        if (label.get(i) == 1) sum1 += scores[i];
                        sum2 += scores[i];
                    }
                }

                cost += (Math.log(sum2) - Math.log(sum1)) / params.getBatchSize();
                if (label.get(optLabel) == 1)
                    correct += +1.0 / params.getBatchSize();

                double[] gradHidden3 = new double[config.hiddenSize];
                for (int i = 0; i < numLabels; ++i)
                    if (label.get(i) >= 0) {
                        double delta = -(label.get(i) - scores[i] / sum2) / params.getBatchSize();
                        for (int nodeIndex : ls) {
                            gradW2[i][nodeIndex] += delta * hidden3[nodeIndex];
                            gradHidden3[nodeIndex] += delta * W2[i][nodeIndex];
                        }
                    }

                double[] gradHidden = new double[config.hiddenSize];
                for (int nodeIndex : ls) {
                    gradHidden[nodeIndex] = gradHidden3[nodeIndex] * 3 * hidden[nodeIndex] * hidden[nodeIndex];
                    gradb1[nodeIndex] += gradHidden[nodeIndex];
                }

                offset = 0;
                for (int j = 0; j < config.numTokens; ++j) {
                    int tok = feature.get(j);
                    int index = tok * config.numTokens + j;
                    if (preMap.containsKey(index)) {
                        int id = preMap.get(index);
                        for (int nodeIndex : ls)
                            gradSaved[id][nodeIndex] += gradHidden[nodeIndex];
                    } else {
                        for (int nodeIndex : ls) {
                            for (int k = 0; k < config.embeddingSize; ++k) {
                                gradW1[nodeIndex][offset + k] += gradHidden[nodeIndex] * E[tok][k];
                                gradE[tok][k] += gradHidden[nodeIndex] * W1[nodeIndex][offset + k];
                            }
                        }
                    }
                    offset += config.embeddingSize;
                }
            }

            return new Cost(cost, correct, gradW1, gradb1, gradW2, gradE);
        }

        /**
         * Return a new threadsafe instance.
         */
        @Override
        public ThreadsafeProcessor<Pair<Collection<Example>, FeedforwardParams>, Cost> newInstance() {
            return new CostFunction();
        }
    }

    /**
     * Describes the parameters for a particular invocation of a cost
     * function.
     */
    private static class FeedforwardParams {

        /**
         * Size of the entire mini-batch (not just the chunk that might be
         * fed-forward at this moment).
         */
        private final int batchSize;

        private final double dropOutProb;

        private FeedforwardParams(int batchSize, double dropOutProb) {
            this.batchSize = batchSize;
            this.dropOutProb = dropOutProb;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public double getDropOutProb() {
            return dropOutProb;
        }

    }

    /**
     * Describes the result of feedforward + backpropagation through
     * the neural network for the batch provided to a `CostFunction.`
     * <p>
     * The members of this class represent weight deltas computed by
     * backpropagation.
     *
     * @see Classifier.CostFunction
     */
    public class Cost {

        private double cost;

        // Percent of training examples predicted correctly
        private double percentCorrect;

        // Weight deltas
        private final double[][] gradW1;
        private final double[] gradb1;
        private final double[][] gradW2;
        private final double[][] gradE;

        private Cost(double cost, double percentCorrect, double[][] gradW1, double[] gradb1, double[][] gradW2,
                     double[][] gradE) {
            this.cost = cost;
            this.percentCorrect = percentCorrect;

            this.gradW1 = gradW1;
            this.gradb1 = gradb1;
            this.gradW2 = gradW2;
            this.gradE = gradE;
        }

        /**
         * Merge the given {@code Cost} data with the data in this
         * instance.
         *
         * @param otherCost
         */
        public void merge(Cost otherCost) {
            this.cost += otherCost.getCost();
            this.percentCorrect += otherCost.getPercentCorrect();

            addInPlace(gradW1, otherCost.getGradW1());
            addInPlace(gradb1, otherCost.getGradb1());
            addInPlace(gradW2, otherCost.getGradW2());
            addInPlace(gradE, otherCost.getGradE());
        }

        /**
         * Backpropagate gradient values from gradSaved into the gradients
         * for the E vectors that generated them.
         *
         * @param featuresSeen Feature IDs observed during training for
         *                     which gradSaved values need to be backprop'd
         *                     into gradE
         */
        private void backpropSaved(IntHashSet featuresSeen) {
            featuresSeen.forEach(x -> {
                int mapX = preMap.get(x);
                int tok = x / config.numTokens;
                int offset = (x % config.numTokens) * config.embeddingSize;

                final double[] gt = gradE[tok];
                final double[] et = E[tok];

                final int hs = config.hiddenSize;
                final int es = config.embeddingSize;

                for (int j = 0; j < hs; ++j) {
                    final double delta = gradSaved[mapX][j];
                    final double[] gradw1 = gradW1[j];
                    final double[] w1j = W1[j];

                    for (int k = 0; k < es; ++k) {
                        gradw1[offset + k] += delta * et[k];
                        gt[k] += delta * w1j[offset + k];
                    }
                }
            });
        }

        /**
         * Add L2 regularization cost to the gradients associated with this
         * instance.
         */
        private void addL2Regularization(double regularizationWeight) {
            for (int i = 0; i < W1.length; ++i) {
                for (int j = 0; j < W1[i].length; ++j) {
                    cost += regularizationWeight * W1[i][j] * W1[i][j] / 2.0;
                    gradW1[i][j] += regularizationWeight * W1[i][j];
                }
            }

            for (int i = 0; i < b1.length; ++i) {
                cost += regularizationWeight * b1[i] * b1[i] / 2.0;
                gradb1[i] += regularizationWeight * b1[i];
            }

            for (int i = 0; i < W2.length; ++i) {
                for (int j = 0; j < W2[i].length; ++j) {
                    cost += regularizationWeight * W2[i][j] * W2[i][j] / 2.0;
                    gradW2[i][j] += regularizationWeight * W2[i][j];
                }
            }

            for (int i = 0; i < E.length; ++i) {
                for (int j = 0; j < E[i].length; ++j) {
                    cost += regularizationWeight * E[i][j] * E[i][j] / 2.0;
                    gradE[i][j] += regularizationWeight * E[i][j];
                }
            }
        }

        public double getCost() {
            return cost;
        }

        public double getPercentCorrect() {
            return percentCorrect;
        }

        public double[][] getGradW1() {
            return gradW1;
        }

        public double[] getGradb1() {
            return gradb1;
        }

        public double[][] getGradW2() {
            return gradW2;
        }

        public double[][] getGradE() {
            return gradE;
        }

    }

    /**
     * Determine the feature IDs which need to be pre-computed for
     * training with these examples.
     */
    private IntHashSet getToPreCompute(List<Example> examples) {
        IntHashSet featureIDs = new IntHashSet();
        for (Example ex : examples) {
            List<Integer> feature = ex.getFeature();

            for (int j = 0; j < config.numTokens; j++) {
                int tok = feature.get(j);
                int index = tok * config.numTokens + j;
                if (preMap.containsKey(index))
                    featureIDs.add(index);
            }
        }

        double percentagePreComputed = featureIDs.size() / (float) config.numPreComputed;
        System.err.printf("Percent actually necessary to pre-compute: %f%%%n", percentagePreComputed * 100);

        return featureIDs;
    }

    /**
     * Determine the total cost on the dataset associated with this
     * classifier using the current learned parameters. This cost is
     * evaluated using mini-batch adaptive gradient descent.
     * <p>
     * This method launches multiple threads, each of which evaluates
     * training cost on a partition of the mini-batch.
     *
     * @param batchSize
     * @param regParameter Regularization parameter (lambda)
     * @param dropOutProb  Drop-out probability. Hidden-layer units in the
     *                     neural network will be randomly turned off
     *                     while training a particular example with this
     *                     probability.
     * @return A {@link edu.stanford.nlp.parser.nndep.Classifier.Cost}
     * object which describes the total cost of the given
     * weights, and includes gradients to be used for further
     * training
     */
    public Cost computeCostFunction(int batchSize, double regParameter, double dropOutProb) {
        validateTraining();

        List<Example> examples = Util.getRandomSubList(dataset.examples, batchSize);

        // Redo precomputations for only those features which are triggered
        // by examples in this mini-batch.
        IntHashSet toPreCompute = getToPreCompute(examples);
        preCompute(toPreCompute);

        // Set up parameters for feedforward
        FeedforwardParams params = new FeedforwardParams(batchSize, dropOutProb);

        // Zero out saved-embedding gradients
        gradSaved = new double[preMap.size()][config.hiddenSize];

        int numChunks = config.trainingThreads;
        List<Collection<Example>> chunks = CollectionUtils.partitionIntoFolds(examples, numChunks);

        // Submit chunks for processing on separate threads
        chunks.forEach(chunk -> jobHandler.put(new Pair<>(chunk, params)));

        jobHandler.join(false);

        // Join costs from each chunk
        Cost cost = null;
        while (jobHandler.peek()) {
            Cost otherCost = jobHandler.poll();

            if (cost == null)
                cost = otherCost;
            else
                cost.merge(otherCost);
        }

        if (cost == null)
            return null;

        // Backpropagate gradients on saved pre-computed values to actual
        // embeddings
        cost.backpropSaved(toPreCompute);

        cost.addL2Regularization(regParameter);

        return cost;
    }

    /**
     * Update classifier weights using the given training cost
     * information.
     *
     * @param cost     Cost information as returned by
     *                 {@link #computeCostFunction(int, double, double)}.
     * @param adaAlpha Global AdaGrad learning rate
     * @param adaEps   Epsilon value for numerical stability in AdaGrad's
     *                 division
     */
    public void takeAdaGradientStep(Cost cost, double adaAlpha, double adaEps) {
        validateTraining();

        double[][] gradW1 = cost.getGradW1(), gradW2 = cost.getGradW2(),
                gradE = cost.getGradE();
        double[] gradb1 = cost.getGradb1();

        for (int i = 0; i < W1.length; ++i) {
            for (int j = 0; j < W1[i].length; ++j) {
                eg2W1[i][j] += gradW1[i][j] * gradW1[i][j];
                W1[i][j] -= adaAlpha * gradW1[i][j] / Math.sqrt(eg2W1[i][j] + adaEps);
            }
        }

        for (int i = 0; i < b1.length; ++i) {
            eg2b1[i] += gradb1[i] * gradb1[i];
            b1[i] -= adaAlpha * gradb1[i] / Math.sqrt(eg2b1[i] + adaEps);
        }

        for (int i = 0; i < W2.length; ++i) {
            for (int j = 0; j < W2[i].length; ++j) {
                eg2W2[i][j] += gradW2[i][j] * gradW2[i][j];
                W2[i][j] -= adaAlpha * gradW2[i][j] / Math.sqrt(eg2W2[i][j] + adaEps);
            }
        }

        for (int i = 0; i < E.length; ++i) {
            for (int j = 0; j < E[i].length; ++j) {
                eg2E[i][j] += gradE[i][j] * gradE[i][j];
                E[i][j] -= adaAlpha * gradE[i][j] / Math.sqrt(eg2E[i][j] + adaEps);
            }
        }
    }

    private void initGradientHistories() {
        eg2E = new double[E.length][E[0].length];
        eg2W1 = new double[W1.length][W1[0].length];
        eg2b1 = new double[b1.length];
        eg2W2 = new double[W2.length][W2[0].length];
    }

    /**
     * Clear all gradient histories used for AdaGrad training.
     *
     * @throws java.lang.IllegalStateException If not training
     */
    public void clearGradientHistories() {
        validateTraining();
        initGradientHistories();
    }

    private void validateTraining() {
        if (!isTraining)
            throw new IllegalStateException("Not training, or training was already finalized");
    }

    /**
     * Finish training this classifier; prepare for a shutdown.
     */
    public void finalizeTraining() {
        validateTraining();

        // Destroy threadpool
        jobHandler.join(true);

        isTraining = false;
    }

    /**
     * @see #preCompute(java.util.Set)
     */
    public void preCompute() {
        preCompute(preMap.keySet());
    }

    /**
     * Pre-compute hidden layer activations for some set of possible
     * feature inputs.
     *
     * @param toPreCompute Set of feature IDs for which hidden layer
     *                     activations should be precomputed
     */
    public void preCompute(MutableIntSet toPreCompute) {
        long startTime = System.currentTimeMillis();

        // NB: It'd make sense to just make the first dimension of this
        // array the same size as `toPreCompute`, then recalculate all
        // `preMap` indices to map into this denser array. But this
        // actually hurt training performance! (See experiments with
        // "smallMap.")
        saved = new double[preMap.size()][config.hiddenSize];

        toPreCompute.forEach(x -> {
            int mapX = preMap.get(x);
            int tok = x / config.numTokens;
            int pos = x % config.numTokens;

            final double[] et = E[tok];
            final double[] sm = saved[mapX];

            final int pc = pos * config.embeddingSize;

            for (int j = 0; j < config.hiddenSize; ++j) {
                final double[] w1j = W1[j];
                for (int k = 0; k < config.embeddingSize; ++k) {
                    sm[j] += w1j[pc + k] * et[k];
                }
            }
        });
        System.err.println("PreComputed " + toPreCompute.size() + ", Elapsed Time: " + (System
                .currentTimeMillis() - startTime) / 1000.0 + " (s)");
    }

    double[] computeScores(int[] feature, double[] scores, double[] hiddenTemp) {
        return computeScores(feature, preMap, scores, hiddenTemp);
    }

    /**
     * Feed a feature vector forward through the network. Returns the
     * values of the output layer.
     */
    private double[] computeScores(int[] feature, IntIntHashMap preMap, double[] scores /* result */, double[] hidden) {

        int offset = 0;
        final int nt = config.numTokens;
        int hiddens = config.hiddenSize;
        int embeds = config.embeddingSize;

        final double[][] saved = this.saved;
        final double[][] W1 = this.W1;
        final double[][] W2 = this.W2;

        Arrays.fill(hidden, 0);
        Arrays.fill(scores, 0);

        for (int j = 0; j < feature.length; ++j) {
            int tok = feature[j];
            int index = tok * nt + j;


            int id;
            if ((id = preMap.getIfAbsent(index, Integer.MIN_VALUE)) != Integer.MIN_VALUE) {

                final double[] si = saved[id];

                for (int i = 0; i < hiddens; ++i) {

                    hidden[i] += si[i];
                }

            } else {

                final double[] et = E[tok];

                for (int i = 0; i < hiddens; ++i) {
                    final double[] ww = W1[i];

                    double dh = 0;
                    for (int k = 0; k < embeds; ++k) {
                         dh += ww[offset + k] * et[k];
                    }
                    hidden[i] += dh;
                }
            }
            offset += config.embeddingSize;
        }

        for (int i = 0; i < config.hiddenSize; ++i) {
            final double hi = hidden[i];
            hidden[i] = (hi + b1[i]) * hi*hi;  // cube nonlinearity
        }


        for (int i = 0; i < numLabels; ++i) {
            double[] w2i = W2[i];
            double ds = 0;
            for (int j = 0; j < config.hiddenSize; ++j) {
                ds += w2i[j] * hidden[j];
            }
            scores[i] += ds;
        }
        return scores;
    }

    public double[][] getW1() {
        return W1;
    }

    public double[] getb1() {
        return b1;
    }

    public double[][] getW2() {
        return W2;
    }

    public double[][] getE() {
        return E;
    }

    /**
     * Add the two 2d arrays in place of {@code m1}.
     *
     * @throws java.lang.IndexOutOfBoundsException (possibly) If
     *                                             {@code m1} and {@code m2} are not of the same dimensions
     */
    private static void addInPlace(final double[][] m1, final double[][] m2) {
        for (int i = 0; i < m1.length; i++) {

            final double[] m1i = m1[i];
            final double[] m2i = m2[i];

            for (int j = 0; j < m1[0].length; j++) {
                m1i[j] += m2i[j];
            }
        }
    }

    /**
     * Add the two 1d arrays in place of {@code a1}.
     *
     * @throws java.lang.IndexOutOfBoundsException (Possibly) if
     *                                             {@code a1} and {@code a2} are not of the same dimensions
     */
    private static void addInPlace(final double[] a1, final double[] a2) {
        for (int i = 0; i < a1.length; i++)
            a1[i] += a2[i];
    }
}
