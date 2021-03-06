package edu.stanford.nlp.patterns;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.stats.DefaultCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;

public class ScorePatternsRatioModifiedFreq<E> extends ScorePatterns<E> {

  public ScorePatternsRatioModifiedFreq(
      ConstantsAndVariables constVars,
      PatternScoring patternScoring,
      String label, Set<CandidatePhrase> allCandidatePhrases,
      TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phInPatScores,
      ScorePhrases scorePhrases, Properties props) {
    super(constVars, patternScoring, label, allCandidatePhrases,  patternsandWords4Label,
        negPatternsandWords4Label, unLabeledPatternsandWords4Label,
        props);
    this.phInPatScores = phInPatScores;
    this.scorePhrases = scorePhrases;
  }

  // cached values
  private TwoDimensionalCounter<CandidatePhrase, ScorePhraseMeasures> phInPatScores;

  private ScorePhrases scorePhrases;

  @Override
  public void setUp(Properties props) {
  }

  @Override
  public Counter<E> score() throws IOException, ClassNotFoundException {

    Counter<CandidatePhrase> externalWordWeightsNormalized = null;
    if (constVars.dictOddsWeights.containsKey(label))
      externalWordWeightsNormalized = GetPatternsFromDataMultiClass
          .normalizeSoftMaxMinMaxScores(constVars.dictOddsWeights.get(label),
            true, true, false);

    Counter<E> currentPatternWeights4Label = new DefaultCounter<>();

    boolean useFreqPhraseExtractedByPat = false;
    if (patternScoring.equals(PatternScoring.SqrtAllRatio))
      useFreqPhraseExtractedByPat = true;
    Function<Pair<E, CandidatePhrase>, Double> numeratorScore = x -> patternsandWords4Label.getCount(x.first(), x.second());

    Counter<E> numeratorPatWt = this.convert2OneDim(label,
        numeratorScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, false, null,
        useFreqPhraseExtractedByPat);

    Counter<E> denominatorPatWt = null;

    Function<Pair<E, CandidatePhrase>, Double> denoScore;
    if (patternScoring.equals(PatternScoring.PosNegUnlabOdds)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());

      denominatorPatWt = this.convert2OneDim(label,
          denoScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.RatioAll)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second()) +
        patternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label, denoScore,allCandidatePhrases, patternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PosNegOdds)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label, denoScore, allCandidatePhrases, patternsandWords4Label,
          constVars.sqrtPatScore, false, externalWordWeightsNormalized,
          useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.PhEvalInPat)
        || patternScoring.equals(PatternScoring.PhEvalInPatLogP)
        || patternScoring.equals(PatternScoring.LOGREG)
        || patternScoring.equals(PatternScoring.LOGREGlogP)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());
      denominatorPatWt = this.convert2OneDim(label,
        denoScore, allCandidatePhrases, patternsandWords4Label, constVars.sqrtPatScore, true,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else if (patternScoring.equals(PatternScoring.SqrtAllRatio)) {
      denoScore = x -> negPatternsandWords4Label.getCount(x.first(), x.second()) + unLabeledPatternsandWords4Label.getCount(x.first(), x.second());

      denominatorPatWt = this.convert2OneDim(label,
        denoScore, allCandidatePhrases, patternsandWords4Label, true, false,
          externalWordWeightsNormalized, useFreqPhraseExtractedByPat);
    } else
      throw new RuntimeException("Cannot understand patterns scoring");

    currentPatternWeights4Label = Counters.divisionNonNaN(numeratorPatWt,
        denominatorPatWt);

    //Multiplying by logP
    if (patternScoring.equals(PatternScoring.PhEvalInPatLogP) || patternScoring.equals(PatternScoring.LOGREGlogP)) {
      Counter<E> logpos_i = new DefaultCounter<>();
      for (Entry<E, DefaultCounter<CandidatePhrase>> en : patternsandWords4Label
          .entrySet()) {
        logpos_i.setCount(en.getKey(), Math.log(en.getValue().size()));
      }
      Counters.multiplyInPlace(currentPatternWeights4Label, logpos_i);
    }
    Counters.retainNonZeros(currentPatternWeights4Label);
    return currentPatternWeights4Label;
  }

  Counter<E> convert2OneDim(String label,
      Function<Pair<E, CandidatePhrase>, Double> scoringFunction, Set<CandidatePhrase> allCandidatePhrases, TwoDimensionalCounter<E, CandidatePhrase> positivePatternsAndWords,
      boolean sqrtPatScore, boolean scorePhrasesInPatSelection,
      Counter<CandidatePhrase> dictOddsWordWeights, boolean useFreqPhraseExtractedByPat) throws IOException, ClassNotFoundException {

//    if (Data.googleNGram.size() == 0 && Data.googleNGramsFile != null) {
//      Data.loadGoogleNGrams();
//    }

    Counter<E> patterns = new DefaultCounter<>();

    Counter<CandidatePhrase> googleNgramNormScores = new DefaultCounter<>();
    Counter<CandidatePhrase> domainNgramNormScores = new DefaultCounter<>();

    Counter<CandidatePhrase> externalFeatWtsNormalized = new DefaultCounter<>();
    Counter<CandidatePhrase> editDistanceFromOtherSemanticBinaryScores = new DefaultCounter<>();
    Counter<CandidatePhrase> editDistanceFromAlreadyExtractedBinaryScores = new DefaultCounter<>();
    double externalWtsDefault = 0.5;
    Counter<String> classifierScores = null;

    if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
        .equals(PatternScoring.PhEvalInPatLogP)) && scorePhrasesInPatSelection) {

      for (CandidatePhrase gc : allCandidatePhrases) {
        String g = gc.getPhrase();

        if (constVars.usePatternEvalEditDistOther) {
          editDistanceFromOtherSemanticBinaryScores.setCount(gc,
              constVars.getEditDistanceScoresOtherClassThreshold(label, g));
        }

        if (constVars.usePatternEvalEditDistSame) {
          editDistanceFromAlreadyExtractedBinaryScores.setCount(gc,
              1 - constVars.getEditDistanceScoresThisClassThreshold(label, g));
        }

        if (constVars.usePatternEvalGoogleNgram)
            googleNgramNormScores
                .setCount(gc, PhraseScorer.getGoogleNgramScore(gc));

        if (constVars.usePatternEvalDomainNgram) {
          // calculate domain-ngram wts
          if (Data.domainNGramRawFreq.containsKey(g)) {
            assert (Data.rawFreq.containsKey(gc));
            domainNgramNormScores.setCount(gc,
                PhraseScorer.getDomainNgramScore(g));
          }
        }

        if (constVars.usePatternEvalWordClass) {
          Integer num = constVars.getWordClassClusters().get(g);
          if(num == null){
            num = constVars.getWordClassClusters().get(g.toLowerCase());
          }
          if (num != null
              && constVars.distSimWeights.get(label).containsKey(num)) {
            externalFeatWtsNormalized.setCount(gc,
                constVars.distSimWeights.get(label).getCount(num));
          } else
            externalFeatWtsNormalized.setCount(gc, externalWtsDefault);
        }
      }
      if (constVars.usePatternEvalGoogleNgram)
        googleNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(googleNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalDomainNgram)
        domainNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(domainNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalWordClass)
        externalFeatWtsNormalized = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(externalFeatWtsNormalized, true,
                true, false);
    }

    else if ((patternScoring.equals(PatternScoring.LOGREG) || patternScoring.equals(PatternScoring.LOGREGlogP))
        && scorePhrasesInPatSelection) {
      Properties props2 = new Properties();
      props2.putAll(props);
      props2.setProperty("phraseScorerClass", "edu.stanford.nlp.patterns.ScorePhrasesLearnFeatWt");
      ScorePhrases scoreclassifier = new ScorePhrases(props2, constVars);
      System.out.println("file is " + props.getProperty("domainNGramsFile"));
      Execution.fillOptions(Data.class, props2);
      classifierScores = scoreclassifier.phraseScorer.scorePhrases(label, allCandidatePhrases,  true);
    }

    Counter<CandidatePhrase> cachedScoresForThisIter = new DefaultCounter<>();

    for (Map.Entry<E, DefaultCounter<CandidatePhrase>> en: positivePatternsAndWords.entrySet()) {

      final Counter<CandidatePhrase> finalGoogleNgramNormScores = googleNgramNormScores;
      final Counter<CandidatePhrase> finalDomainNgramNormScores = domainNgramNormScores;
      final Counter<CandidatePhrase> finalExternalFeatWtsNormalized = externalFeatWtsNormalized;
      final Counter<String> finalClassifierScores = classifierScores;

      en.getValue().map.forEachKeyValue((word, v) -> {

        Counter<ScorePhraseMeasures> scoreslist = new DefaultCounter<>();
        double score = 1;
        if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
                .equals(PatternScoring.PhEvalInPatLogP))
                && scorePhrasesInPatSelection) {
          if (cachedScoresForThisIter.containsKey(word)) {
            score = cachedScoresForThisIter.getCount(word);
          } else {
            if (constVars.getOtherSemanticClassesWords().contains(word)
                    || constVars.getCommonEngWords().contains(word))
              score = 1;
            else {

              if (constVars.usePatternEvalSemanticOdds) {
                double semanticClassOdds = 1;
                if (dictOddsWordWeights.containsKey(word))
                  semanticClassOdds = 1 - dictOddsWordWeights.getCount(word);
                scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS,
                        semanticClassOdds);
              }

              if (constVars.usePatternEvalGoogleNgram) {
                double gscore = 0;
                if (finalGoogleNgramNormScores.containsKey(word)) {
                  gscore = 1 - finalGoogleNgramNormScores.getCount(word);
                }
                scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
              }

              if (constVars.usePatternEvalDomainNgram) {
                double domainscore;
                if (finalDomainNgramNormScores.containsKey(word)) {
                  domainscore = 1 - finalDomainNgramNormScores.getCount(word);
                } else
                  domainscore = 1 - scorePhrases.phraseScorer
                          .getPhraseWeightFromWords(finalDomainNgramNormScores, word,
                                  scorePhrases.phraseScorer.OOVDomainNgramScore);
                scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM,
                        domainscore);
              }
              if (constVars.usePatternEvalWordClass) {
                double externalFeatureWt = externalWtsDefault;
                if (finalExternalFeatWtsNormalized.containsKey(word))
                  externalFeatureWt = 1 - finalExternalFeatWtsNormalized.getCount(word);
                scoreslist.setCount(ScorePhraseMeasures.DISTSIM,
                        externalFeatureWt);
              }

              if (constVars.usePatternEvalEditDistOther) {
                assert editDistanceFromOtherSemanticBinaryScores.containsKey(word) : "How come no edit distance info for word " + word;
                scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER,
                        editDistanceFromOtherSemanticBinaryScores.getCount(word));
              }
              if (constVars.usePatternEvalEditDistSame) {
                scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,
                        editDistanceFromAlreadyExtractedBinaryScores.getCount(word));
              }

              // taking average
              score = Counters.mean(scoreslist);

              phInPatScores.setCounter(word, scoreslist);
            }

            cachedScoresForThisIter.setCount(word, score);
          }
        } else if ((patternScoring.equals(PatternScoring.LOGREG) || patternScoring.equals(PatternScoring.LOGREGlogP))
                && scorePhrasesInPatSelection) {
          score = 1 - finalClassifierScores.getCount(word);
          // score = 1 - scorePhrases.scoreUsingClassifer(classifier,
          // e.getKey(), label, true, null, null, dictOddsWordWeights);
          // throw new RuntimeException("not implemented yet");
        }

        if (useFreqPhraseExtractedByPat)
          score = score * scoringFunction.apply(new Pair(en.getKey(), word));
        if (constVars.sqrtPatScore)
          patterns.incrementCount(en.getKey(), Math.sqrt(score));
        else
          patterns.incrementCount(en.getKey(), score);


      });

    }

    return patterns;
  }

}
