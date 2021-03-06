/*
 * Copyright (c) 2010-2011, University of Sussex
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Sussex nor the names of its 
 *    contributors may be used to endorse or promote products derived from this 
 *    software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package uk.ac.susx.mlcl.byblo;

import uk.ac.susx.mlcl.lib.tasks.InputFileValidator;
import uk.ac.susx.mlcl.lib.tasks.OutputFileValidator;
import com.google.common.base.Objects.ToStringHelper;
import uk.ac.susx.mlcl.lib.DoubleConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import uk.ac.susx.mlcl.byblo.allpairs.InvertedApssTask;
import uk.ac.susx.mlcl.byblo.allpairs.ThreadedApssTask;
import uk.ac.susx.mlcl.byblo.io.WeightedTokenPairVectorSource;
import uk.ac.susx.mlcl.byblo.io.WeightedTokenPairSink;
import uk.ac.susx.mlcl.byblo.measure.AbstractMIProximity;
import uk.ac.susx.mlcl.byblo.measure.CrMi;
import uk.ac.susx.mlcl.byblo.measure.KendallTau;
import uk.ac.susx.mlcl.byblo.measure.Lee;
import uk.ac.susx.mlcl.byblo.measure.Lp;
import uk.ac.susx.mlcl.byblo.measure.Proximity;
import uk.ac.susx.mlcl.byblo.measure.ReversedProximity;
import uk.ac.susx.mlcl.lib.ObjectIndex;
import uk.ac.susx.mlcl.byblo.io.TokenPair;
import uk.ac.susx.mlcl.lib.io.Sink;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import uk.ac.susx.mlcl.byblo.io.Weighted;
import uk.ac.susx.mlcl.byblo.io.WeightedTokenPairSource;
import uk.ac.susx.mlcl.byblo.io.WeightedTokenSource;
import uk.ac.susx.mlcl.lib.io.Files;
import uk.ac.susx.mlcl.lib.io.Lexer;
import uk.ac.susx.mlcl.lib.tasks.AbstractCommand;

/**
 *
 * @author Hamish Morgan &lt;hamish.morgan@sussex.ac.uk%gt;
 */
@Parameters(commandDescription = "Perform all-pair similarity search on the given input frequency files.")
public class AllPairsTask extends AbstractCommand {

    private static final Log LOG = LogFactory.getLog(AllPairsTask.class);

    @Parameter(names = {"-i", "--input"},
               description = "Entry-feature frequency vectors files.",
               required = true,
               validateWith = InputFileValidator.class)
    private File entryFeaturesFile;

    @Parameter(names = {"-if", "--input-features"},
               description = "Feature frequencies file",
               validateWith = InputFileValidator.class)
    private File featuresFile;

    @Parameter(names = {"-ie", "--input-entries"},
               description = "Entry frequencies file",
               validateWith = InputFileValidator.class)
    private File entriesFile;

    @Parameter(names = {"-o", "--output"},
               description = "Output similarity matrix file.",
               required = true,
               validateWith = OutputFileValidator.class)
    private File outputFile;

    @Parameter(names = {"-c", "--charset"},
               description = "Character encoding to use for reading and writing.")
    private Charset charset = Files.DEFAULT_CHARSET;

    @Parameter(names = {"-C", "--chunk-size"},
               description = "Number of entries to compare per work unit. Larger value increase performance and memory usage.")
    private int chunkSize = 5000;

    @Parameter(names = {"-t", "--threads"},
               description = "Number of conccurent processing threads.")
    private int nThreads = Runtime.getRuntime().availableProcessors() + 1;

    @Parameter(names = {"-Smn", "--similarity-min"},
               description = "Minimum similarity threshold.",
               converter = DoubleConverter.class)
    private double minSimilarity = Double.NEGATIVE_INFINITY;

    @Parameter(names = {"-Smx", "--similarity-max"},
               description = "Maximyum similarity threshold.",
               converter = DoubleConverter.class)
    private double maxSimilarity = Double.POSITIVE_INFINITY;

    @Parameter(names = {"-ip", "--identity-pairs"},
               description = "Produce similarity between pair of identical entries.")
    private boolean outputIdentityPairs = false;

    @Parameter(names = {"-m", "--measure"},
               description = "Similarity measure to use.")
    private String measureName = "Jaccard";

    @Parameter(names = {"--measure-reversed"},
               description = "Swap similarity measure inputs.")
    private boolean measureReversed = false;

    @Parameter(names = {"--lee-alpha"},
               description = "Alpha parameter to Lee's alpha-skew divergence measure.",
               converter = DoubleConverter.class)
    private double leeAlpha = Lee.DEFAULT_ALPHA;

    @Parameter(names = {"--crmi-beta"},
               description = "Beta paramter to Weed's CRMI measure.",
               converter = DoubleConverter.class)
    private double crmiBeta = CrMi.DEFAULT_BETA;

    @Parameter(names = {"--crmi-gamma"},
               description = "Gamma paramter to Weed's CRMI measure.",
               converter = DoubleConverter.class)
    private double crmiGamma = CrMi.DEFAULT_GAMMA;

    @Parameter(names = {"--mink-p"},
               description = "P parameter to Minkowski/Lp space measure.",
               converter = DoubleConverter.class)
    private double minkP = 2;

    private Map<String, Class<? extends Proximity>> buildMeasureClassLookupTable() throws ClassNotFoundException {

        // Map that will store measure aliases to class
        final Map<String, Class<? extends Proximity>> classLookup =
                new HashMap<String, Class<? extends Proximity>>();

        final ResourceBundle res = ResourceBundle.getBundle(
                "uk.ac.susx.mlcl.byblo.measure.measures");
        final String[] measures = res.getString("measures").split(",");

        for (int i = 0; i < measures.length; i++) {
            final String measure = measures[i].trim();
            final String className = res.getString(
                    "measure." + measure + ".class");
            @SuppressWarnings("unchecked")
            final Class<? extends Proximity> clazz =
                    (Class<? extends Proximity>) Class.forName(className);
            classLookup.put(measure.toLowerCase(), clazz);
            if (res.containsKey("measure." + measure + ".aliases")) {
                final String[] aliases = res.getString(
                        "measure." + measure + ".aliases").split(",");
                for (String alias : aliases) {
                    classLookup.put(alias.toLowerCase().trim(), clazz);
                }
            }
        }
        return classLookup;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void runCommand() throws Exception {

        if (LOG.isInfoEnabled()) {
            LOG.info(
                    "Running all-pairs similarity search from \"" + entryFeaturesFile + "\" to \"" + outputFile + "\"");
        }


        final Map<String, Class<? extends Proximity>> classLookup =
                buildMeasureClassLookupTable();

        Class<? extends Proximity> measureClass = null;
        if (classLookup.containsKey(measureName.toLowerCase().trim())) {
            measureClass = classLookup.get(measureName.toLowerCase().trim());
        } else {
            @SuppressWarnings("unchecked")
            Class<? extends Proximity> clazz =
                    (Class<? extends Proximity>) Class.forName(measureName);
            measureClass = clazz;
        }

        // Instantiate the denote proxmity measure
        Proximity prox = measureClass.newInstance();

        // Parameterise those measures that require them
        if (prox instanceof Lp) {
            ((Lp) prox).setP(minkP);
        } else if (prox instanceof Lee) {
            ((Lee) prox).setAlpha(leeAlpha);
        } else if (prox instanceof CrMi) {
            ((CrMi) prox).setBeta(crmiBeta);
            ((CrMi) prox).setGamma(crmiGamma);
        }

        ObjectIndex<String> strIndex = new ObjectIndex<String>();

        // Entry index is not really required for the core algorithm
        // implementation but is used to filter Entries

        // Mutual Information based proximity measures require the frequencies
        // of each feature, and other associate values, so load them
        // if required.
        if (prox instanceof AbstractMIProximity) {
            try {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Loading features file " + featuresFile);
                }

                WeightedTokenSource features = new WeightedTokenSource(
                        featuresFile, charset, strIndex);
                AbstractMIProximity bmip = ((AbstractMIProximity) prox);
                bmip.setFeatureFrequencies(features.readAllAsArray());
                bmip.setFeatureFrequencySum(features.getWeightSum());
                bmip.setOccuringFeatureCount(features.getCardinality());

            } catch (IOException e) {
                throw e;
            }
        } else if (prox instanceof KendallTau) {
            try {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Loading entries file for "
                            + "KendalTau.numFeatures: " + featuresFile);
                }

                WeightedTokenSource features = new WeightedTokenSource(
                        featuresFile, charset, strIndex);
                features.readAll();

                ((KendallTau) prox).setNumFeatures(features.getCardinality());

            } catch (IOException e) {
                throw e;
            }
        }

        // Swap the proximity measure inputs if required
        if (measureReversed) {
            prox = new ReversedProximity(prox);
        }

        // Instantiate two vector source objects than can scan and read the
        // main db. We need two because the algorithm takes all pairwise
        // combinations of vectors, so will be looking at two differnt points
        // in the file. Also this allows for the possibility of having differnt
        // files, e.g compare fruit words with cake words
        final WeightedTokenPairVectorSource sourceA = new WeightedTokenPairSource(
                entryFeaturesFile, charset, strIndex).getVectorSource();
        final WeightedTokenPairVectorSource sourceB = new WeightedTokenPairSource(
                entryFeaturesFile, charset, strIndex).getVectorSource();

        // Create a sink object that will act as a recipient for all pairs that
        // are produced by the algorithm.

        final Sink<Weighted<TokenPair>> sink =
                new WeightedTokenPairSink(outputFile, charset, strIndex,
                                          strIndex);

        // Instantiate the all-pairs algorithm as given on the command line.
        ThreadedApssTask<Lexer.Tell> apss = new ThreadedApssTask<Lexer.Tell>(
                sourceA, sourceB, sink);
        apss.setInnerAlgorithm(InvertedApssTask.class);

        // Parameterise the all-pairs algorithm
        apss.setNumThreads(nThreads);
        apss.setSink(sink);

        prox.setFilteredFeatureId(strIndex.get(FilterTask.FILTERED_STRING));

        apss.setMeasure(prox);
        apss.setMaxChunkSize(chunkSize);

        List<Predicate<Weighted<TokenPair>>> pairFilters =
                new ArrayList<Predicate<Weighted<TokenPair>>>();

        if (minSimilarity != Double.NEGATIVE_INFINITY) {
            pairFilters.add(Weighted.<TokenPair>greaterThanOrEqualTo(
                    minSimilarity));
        }

        if (maxSimilarity != Double.POSITIVE_INFINITY) {
            pairFilters.add(Weighted.<TokenPair>lessThanOrEqualTo(maxSimilarity));
        }

        if (!outputIdentityPairs) {
            pairFilters.add(Predicates.not(Predicates.compose(
                    TokenPair.identity(), Weighted.<TokenPair>recordFunction())));
        }

        if (pairFilters.size() == 1) {
            apss.setProducatePair(pairFilters.get(0));
        } else if (pairFilters.size() > 1) {
            apss.setProducatePair(Predicates.<Weighted<TokenPair>>and(
                    pairFilters));
        }

        apss.run();

        if (LOG.isInfoEnabled()) {
            LOG.info("Completed all-pairs similarity search.");
        }
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().
                add("eventsIn", entryFeaturesFile).
                add("entriesIn", entriesFile).
                add("featuresIn", featuresFile).
                add("simsOut", outputFile).
                add("charset", charset).
                add("chunkSize", chunkSize).
                add("threads", nThreads).
                add("minSimilarity", minSimilarity).
                add("maxSimilarity", maxSimilarity).
                add("outputIdentityPairs", outputIdentityPairs).
                add("measure", measureName).
                add("measureReversed", measureReversed).
                add("leeAlpha", leeAlpha).
                add("crmiBeta", crmiBeta).
                add("crmiGamma", crmiGamma).
                add("minkP", minkP);
    }
}
