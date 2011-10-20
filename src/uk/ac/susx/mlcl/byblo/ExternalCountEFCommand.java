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

import uk.ac.susx.mlcl.byblo.command.CountEFCommand;
import uk.ac.susx.mlcl.lib.tasks.MergeTask;
import uk.ac.susx.mlcl.lib.tasks.DeleteTask;
import uk.ac.susx.mlcl.lib.tasks.CopyTask;
import uk.ac.susx.mlcl.lib.tasks.SortTask;
import uk.ac.susx.mlcl.lib.io.TempFileFactoryConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import uk.ac.susx.mlcl.lib.Checks;
import uk.ac.susx.mlcl.lib.io.FileFactory;
import uk.ac.susx.mlcl.lib.io.IOUtil;
import uk.ac.susx.mlcl.lib.io.TempFileFactory;
import uk.ac.susx.mlcl.lib.command.AbstractParallelCommand;
import uk.ac.susx.mlcl.lib.tasks.Task;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import uk.ac.susx.mlcl.byblo.io.EntryFeatureSink;
import uk.ac.susx.mlcl.byblo.io.EntryFeature;
import uk.ac.susx.mlcl.byblo.io.EntryFeatureSource;
import uk.ac.susx.mlcl.byblo.io.WeightedEntrySink;
import uk.ac.susx.mlcl.byblo.io.WeightedFeatureSink;
import uk.ac.susx.mlcl.byblo.io.WeightedEntryFeatureSink;
import uk.ac.susx.mlcl.byblo.io.WeightedEntryFeatureSource;
import uk.ac.susx.mlcl.lib.ObjectIndex;
import uk.ac.susx.mlcl.lib.collect.Weighted;
import uk.ac.susx.mlcl.lib.io.AbstractTSVSink;
import uk.ac.susx.mlcl.lib.io.AbstractTSVSource;
import uk.ac.susx.mlcl.lib.io.Sink;
import uk.ac.susx.mlcl.lib.io.Source;
import uk.ac.susx.mlcl.lib.tasks.CountUniqueTask;

/**
 *
 * @author Hamish Morgan &lt;hamish.morgan@sussex.ac.uk%gt;
 */
@Parameters(commandDescription = "Freqency count a structured input instance file.")
public class ExternalCountEFCommand extends AbstractParallelCommand {

    private static final int DEFAULT_MAX_CHUNK_SIZE = xxxChunkCommand.DEFAULT_MAX_CHUNK_SIZE;

    @Parameter(names = {"-C", "--chunk-size"},
               description = "Number of lines per work unit. Lrger value increase performance and memory usage.")
    private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

    @Parameter(names = {"-i", "--input"},
               required = true,
               description = "Input instances file")
    private File inputFile;

    @Parameter(names = {"-o", "-output"},
               required = true,
               description = "Output entry-feature frequencies file")
    private File entryFeaturesFile = null;

    @Parameter(names = {"-T", "--temporary-directory"},
               description = "Directory used for holding temporary files.",
               converter = TempFileFactoryConverter.class)
    private FileFactory tempFileFactory = new TempFileFactory();

    @Parameter(names = {"-c", "--charset"},
               description = "Character encoding to use for reading and writing files.")
    private Charset charset = IOUtil.DEFAULT_CHARSET;

    private Queue<File> mergeQueue;

    public ExternalCountEFCommand(
            final File instancesFile, final File entryFeaturesFile,
            final Charset charset, int maxChunkSize) {
        this(instancesFile, entryFeaturesFile);
        setCharset(charset);
        setMaxChunkSize(maxChunkSize);
    }

    public ExternalCountEFCommand(
            final File instancesFile, final File entryFeaturesFile) {
        setInputFile(instancesFile);
        setOutputFile(entryFeaturesFile);
    }

    public ExternalCountEFCommand() {
        super();
    }

    public final Charset getCharset() {
        return charset;
    }

    public final void setCharset(Charset charset) {
        Checks.checkNotNull(charset);
        this.charset = charset;
    }

    public final int getMaxChunkSize() {
        return maxChunkSize;
    }

    public final void setMaxChunkSize(int maxChunkSize) {
        if (maxChunkSize < 1)
            throw new IllegalArgumentException("maxChunkSize < 1");
        this.maxChunkSize = maxChunkSize;
    }

    public final File getOutputFile() {
        return entryFeaturesFile;
    }

    public final void setOutputFile(final File featuresFile)
            throws NullPointerException {
        if (featuresFile == null)
            throw new NullPointerException("featuresFile is null");
        this.entryFeaturesFile = featuresFile;
    }

    public File getInputFile() {
        return inputFile;
    }

    public final void setInputFile(final File inputFile)
            throws NullPointerException {
        if (inputFile == null)
            throw new NullPointerException("sourceFile is null");
        this.inputFile = inputFile;
    }

    @Override
    protected void initialiseTask() throws Exception {
        super.initialiseTask();
        checkState();
    }

    @Override
    protected void runTask() throws Exception {
        mapEFCounts();
        reduceEFCounts();

//        map();
//        reduce();

        finish();
    }

    protected void mapEFCounts() throws Exception {

        mergeQueue = new ArrayDeque<File>();
        BlockingQueue<File> chunkQueue = new ArrayBlockingQueue<File>(2);

        xxxChunkCommand chunkTask = new xxxChunkCommand(getInputFile(), getCharset(),
                                            getMaxChunkSize());
        chunkTask.setDstFileQueue(chunkQueue);
        chunkTask.setChunkFileFactory(tempFileFactory);
        Future<xxxChunkCommand> chunkFuture = submitTask(chunkTask);

        // Immidiately poll the chunk task so we can start handling other
        // completed tasks
        if (!getFutureQueue().poll().equals(chunkFuture))
            throw new AssertionError("Expecting ChunkTask on future queue.");

        while (!chunkFuture.isDone() || !chunkQueue.isEmpty()) {
            if (!getFutureQueue().isEmpty() && getFutureQueue().peek().isDone()) {

                handleCompletedTask(getFutureQueue().poll().get());

            } else if (!chunkQueue.isEmpty()) {

                File src = chunkQueue.take();
                File dest = tempFileFactory.createFile();

                final Source<EntryFeature> efSource =
                        new EntryFeatureSource(src, charset);

                final Sink<Weighted<EntryFeature>> efSink =
                        new WeightedEntryFeatureSink(dest, charset);

                submitTask(new CountEFCommand(src, dest));
            }

            // XXX: Nasty hack to stop it tight looping when both queues are empty
            Thread.sleep(1);
        }
        chunkTask.throwException();
    }

    protected void reduceEFCounts() throws Exception {
        while (!getFutureQueue().isEmpty()) {
            Task task = getFutureQueue().poll().get();
            handleCompletedTask(task);
        }
    }

    protected void handleCompletedEFCounts(Task task) throws Exception {
        task.throwException();

        if (task.getClass().equals(CountUniqueTask.class)) {

            CountUniqueTask<EntryFeature> countTask = (CountUniqueTask<EntryFeature>) task;





            submitTask(new SortTask(countTask.getEntriesFile(),
                                    countTask.getEntriesFile(), getCharset(),
                                    comparator));



            submitTask(new SortTask(countTask.getEntryFeaturesFile(),
                                    countTask.getEntryFeaturesFile(),
                                    getCharset(), comparator));
            submitTask(new SortTask(countTask.getFeaturesFile(),
                                    countTask.getFeaturesFile(), getCharset(),
                                    comparator));

            submitTask(new DeleteTask(countTask.getInputFile()));

        } else if (task.getClass().equals(SortTask.class)) {

            SortTask sortTask = (SortTask) task;
            File dst = sortTask.getDstFile();

            if (dst.getName().endsWith(ENTRIES_FILE_SUFFIX))
                queueEntryMergeTask(dst, mergeEntryQueue);
            else if (dst.getName().endsWith(ENTRY_FEATURES_FILE_SUFFIX))
                queueEntryFeatureMergeTask(dst, mergeQueue);
            else if (dst.getName().endsWith(FEATURES_FILE_SUFFIX))
                queueFeatureMergeTask(dst, mergeFeaturesQueue);
            else
                throw new AssertionError("File ending does not match known merge types: " + dst.
                        getName());


        } else if (task.getClass().equals(MergeTask.class)) {

            MergeTask<?> mergeTask = (MergeTask) task;

            final File dst = ((AbstractTSVSink) mergeTask.getSink()).getFile();
            final File srcA = ((AbstractTSVSource) mergeTask.getSourceA()).
                    getFile();
            final File srcB = ((AbstractTSVSource) mergeTask.getSourceB()).
                    getFile();

            if (mergeTask.getSink() instanceof WeightedEntrySink) {
                queueEntryMergeTask(dst, mergeEntryQueue);
            } else if (mergeTask.getSink() instanceof EntryFeatureSink) {
                queueEntryFeatureMergeTask(dst, mergeQueue);
            } else if (mergeTask.getSink() instanceof WeightedFeatureSink) {
                queueFeatureMergeTask(dst, mergeFeaturesQueue);
            } else {
                throw new AssertionError("Merge task is of unknown type: " + mergeTask.
                        getSink());
            }

            srcA.delete();
            srcB.delete();
        } else if (task.getClass().equals(DeleteTask.class)) {
            // not a sausage
        } else {
            throw new AssertionError(
                    "Task type " + task.getClass()
                    + " should not have been queued.");
        }
    }

//    protected void map() throws Exception {
//
//        mergeEntryQueue = new ArrayDeque<File>();
//        mergeFeaturesQueue = new ArrayDeque<File>();
//        mergeEntryFeatureQueue = new ArrayDeque<File>();
//
//        BlockingQueue<File> chunkQueue = new ArrayBlockingQueue<File>(2);
//
//        ChunkTask chunkTask = new ChunkTask(getInputFile(), getCharset(),
//                                            getMaxChunkSize());
//        chunkTask.setDstFileQueue(chunkQueue);
//        chunkTask.setChunkFileFactory(tempFileFactory);
//        Future<ChunkTask> chunkFuture = submitTask(chunkTask);
//
//        // Immidiately poll the chunk task so we can start handling other
//        // completed tasks
//        if (!getFutureQueue().poll().equals(chunkFuture))
//            throw new AssertionError("Expecting ChunkTask on future queue.");
//
//        while (!chunkFuture.isDone() || !chunkQueue.isEmpty()) {
//            if (!getFutureQueue().isEmpty() && getFutureQueue().peek().isDone()) {
//
//                handleCompletedTask(getFutureQueue().poll().get());
//
//            } else if (!chunkQueue.isEmpty()) {
//
//                File chunk = chunkQueue.take();
//
//                File chunk_entriesFile = new File(chunk.getParentFile(),
//                                                  chunk.getName() + ENTRIES_FILE_SUFFIX);
//
//                File chunk_featuresFile = new File(chunk.getParentFile(),
//                                                   chunk.getName() + FEATURES_FILE_SUFFIX);
//
//                File chunk_entryFeaturesFile = new File(chunk.getParentFile(),
//                                                        chunk.getName() + ENTRY_FEATURES_FILE_SUFFIX);
//
//                submitTask(new CountCommand(chunk, chunk_entryFeaturesFile,
//                                            chunk_entriesFile,
//                                            chunk_featuresFile,
//                                            getCharset()));
//            }
//
//            // XXX: Nasty hack to stop it tight looping when both queues are empty
//            Thread.sleep(1);
//        }
//        chunkTask.throwException();
//    }
//    protected void reduce() throws Exception {
//        while (!getFutureQueue().isEmpty()) {
//            Task task = getFutureQueue().poll().get();
//            handleCompletedTask(task);
//        }
//    }
//    protected void handleCompletedTask(Task task) throws Exception {
//        task.throwException();
//
//        if (task.getClass().equals(CountEFCommand.class)) {
//
//            CountEFCommand countTask = (CountEFCommand) task;
//
//            submitTask(new SortTask(countTask.getEntriesFile(),
//                                    countTask.getEntriesFile(), getCharset(),
//                                    comparator));
//            submitTask(new SortTask(countTask.getOutputFile(),
//                                    countTask.getOutputFile(),
//                                    getCharset(), comparator));
//            submitTask(new SortTask(countTask.getFeaturesFile(),
//                                    countTask.getFeaturesFile(), getCharset(),
//                                    comparator));
//
//            submitTask(new DeleteTask(countTask.getInputFile()));
//
//        } else if (task.getClass().equals(SortTask.class)) {
//
//            SortTask sortTask = (SortTask) task;
//            File dst = sortTask.getDstFile();
//
//            if (dst.getName().endsWith(ENTRIES_FILE_SUFFIX))
//                queueEntryMergeTask(dst, mergeEntryQueue);
//            else if (dst.getName().endsWith(ENTRY_FEATURES_FILE_SUFFIX))
//                queueEntryFeatureMergeTask(dst, mergeQueue);
//            else if (dst.getName().endsWith(FEATURES_FILE_SUFFIX))
//                queueFeatureMergeTask(dst, mergeFeaturesQueue);
//            else
//                throw new AssertionError("File ending does not match known merge types: " + dst.
//                        getName());
//
//
//        } else if (task.getClass().equals(MergeTask.class)) {
//
//            MergeTask<?> mergeTask = (MergeTask) task;
//
//            final File dst = ((AbstractTSVSink) mergeTask.getSink()).getFile();
//            final File srcA = ((AbstractTSVSource) mergeTask.getSourceA()).
//                    getFile();
//            final File srcB = ((AbstractTSVSource) mergeTask.getSourceB()).
//                    getFile();
//
//            if (mergeTask.getSink() instanceof WeightedEntrySink) {
//                queueEntryMergeTask(dst, mergeEntryQueue);
//            } else if (mergeTask.getSink() instanceof EntryFeatureSink) {
//                queueEntryFeatureMergeTask(dst, mergeQueue);
//            } else if (mergeTask.getSink() instanceof WeightedFeatureSink) {
//                queueFeatureMergeTask(dst, mergeFeaturesQueue);
//            } else {
//                throw new AssertionError("Merge task is of unknown type: " + mergeTask.
//                        getSink());
//            }
//
//            srcA.delete();
//            srcB.delete();
//        } else if (task.getClass().equals(DeleteTask.class)) {
//            // not a sausage
//        } else {
//            throw new AssertionError(
//                    "Task type " + task.getClass()
//                    + " should not have been queued.");
//        }
//    }

    protected void finish() throws Exception {
        checkState();

        File finalMerge;

        finalMerge = mergeEntryQueue.poll();
        if (finalMerge == null)
            throw new AssertionError(
                    "The entry merge queue is empty but final copy has not been completed.");
        new CopyTask(finalMerge, getEntriesFile()).runTask();
        new DeleteTask(finalMerge).runTask();

        finalMerge = mergeQueue.poll();
        if (finalMerge == null)
            throw new AssertionError(
                    "The entry/feature merge queue is empty but final copy has not been completed.");
        new CopyTask(finalMerge, getOutputFile()).runTask();
        new DeleteTask(finalMerge).runTask();

        finalMerge = mergeFeaturesQueue.poll();
        if (finalMerge == null)
            throw new AssertionError(
                    "The feature merge queue is empty but final copy has not been completed.");
        new CopyTask(finalMerge, getFeaturesFile()).runTask();
        new DeleteTask(finalMerge).runTask();
    }

//    protected Future<MergeTask> queueMergeTask(File file, Queue<File> q) throws IOException {
//        q.add(file);
//
//        if (q.size() >= 2) {
//
//            File result_x = tempFileFactory.createFile();
//
//            File result = new File(result_x.getPath() + file.getName().substring(
//                    file.getName().length() - 2));
//            result_x.delete();
//
////            Source
//            
//            MergeTask mergeTask = new MergeTask(
//                    q.poll(), q.poll(), result,
//                    getCharset());
//            mergeTask.setComparator(comparator);
//            mergeTask.setFormatter(formatter);
//            return submitTask(mergeTask);
//        } else {
//            return null;
//        }
//    }
//    
    protected Future<MergeTask<Weighted<EntryFeature>>> queueEntryFeatureMergeTask(
            File file, Queue<File> q) throws IOException {
        q.add(file);

        if (q.size() >= 2) {

            File result_x = tempFileFactory.createFile();

            File result = new File(result_x.getPath() + file.getName().substring(
                    file.getName().length() - 2));
            result_x.delete();

            File srcFileA = q.poll();
            File srcFileB = q.poll();
            File sinkFile = result;

            ObjectIndex<String> entryIndex = new ObjectIndex<String>();
            ObjectIndex<String> featureIndex = new ObjectIndex<String>();

            WeightedEntryFeatureSource srcA = new WeightedEntryFeatureSource(
                    srcFileA, charset, entryIndex, featureIndex);
            WeightedEntryFeatureSource srcB = new WeightedEntryFeatureSource(
                    srcFileB, charset, entryIndex, featureIndex);
            WeightedEntryFeatureSink sink = new WeightedEntryFeatureSink(
                    sinkFile, charset, entryIndex, featureIndex);

            
            MergeTask<Weighted<EntryFeature>> mergeTask =
                    new MergeTask<Weighted<EntryFeature>>(srcA, srcB, sink,
                                                        null, charset);
            return submitTask(mergeTask);
        } else {
            return null;
        }
    }

//    protected Future<MergeTask<Entry>> queueEntryMergeTask(
//            File file, Queue<File> q) throws IOException {
//        q.add(file);
//
//        if (q.size() >= 2) {
//
//            File result_x = tempFileFactory.createFile();
//
//            File result = new File(result_x.getPath() + file.getName().substring(
//                    file.getName().length() - 2));
//            result_x.delete();
//
//            File srcFileA = q.poll();
//            File srcFileB = q.poll();
//            File sinkFile = result;
//
//            ObjectIndex<String> entryIndex = new ObjectIndex<String>();
//
//            WeightedEntrySource srcA = new WeightedEntrySource(
//                    srcFileA, charset, entryIndex);
//            WeightedEntrySource srcB = new WeightedEntrySource(
//                    srcFileB, charset, entryIndex);
//            WeightedEntrySink sink = new WeightedEntrySink(
//                    sinkFile, charset, entryIndex);
//
//            MergeTask<Entry> mergeTask =
//                    new MergeTask<Entry>(srcA, srcB, sink,
//                                         null, charset);
//            return submitTask(mergeTask);
//        } else {
//            return null;
//        }
//    }
//
//    protected Future<MergeTask<Feature>> queueFeatureMergeTask(
//            File file, Queue<File> q) throws IOException {
//        q.add(file);
//
//        if (q.size() >= 2) {
//
//            File result_x = tempFileFactory.createFile();
//
//            File result = new File(result_x.getPath() + file.getName().substring(
//                    file.getName().length() - 2));
//            result_x.delete();
//
//            File srcFileA = q.poll();
//            File srcFileB = q.poll();
//            File sinkFile = result;
//
//            ObjectIndex<String> featureIndex = new ObjectIndex<String>();
//
//            WeightedFeatureSource srcA = new WeightedFeatureSource(
//                    srcFileA, charset, featureIndex);
//            WeightedFeatureSource srcB = new WeightedFeatureSource(
//                    srcFileB, charset, featureIndex);
//            WeightedFeatureSink sink = new WeightedFeatureSink(
//                    sinkFile, charset, featureIndex);
//
//            MergeTask<Feature> mergeTask =
//                    new MergeTask<Feature>(srcA, srcB, sink,
//                                           null, charset);
//            return submitTask(mergeTask);
//        } else {
//            return null;
//        }
//    }

    /**
     * Method that performance a number of sanity checks on the parameterisation
     * of this class. It is necessary to do this because the the class can be
     * instantiated via a null constructor when run from the command line.
     *
     * @throws NullPointerException
     * @throws IllegalStateException
     * @throws FileNotFoundException
     */
    private void checkState() throws NullPointerException, IllegalStateException, FileNotFoundException {
        // Check non of the parameters are null
        if (inputFile == null)
            throw new NullPointerException("inputFile is null");
        if (entryFeaturesFile == null)
            throw new NullPointerException("entryFeaturesFile is null");
//        if (featuresFile == null)
//            throw new NullPointerException("featuresFile is null");
//        if (entriesFile == null)
//            throw new NullPointerException("entriesFile is null");
        if (charset == null)
            throw new NullPointerException("charset is null");

        // Check that no two files are the same
        if (inputFile.equals(entryFeaturesFile))
            throw new IllegalStateException("inputFile == featuresFile");
//        if (inputFile.equals(featuresFile))
//            throw new IllegalStateException("inputFile == contextsFile");
//        if (inputFile.equals(entriesFile))
//            throw new IllegalStateException("inputFile == entriesFile");
//        if (entryFeaturesFile.equals(featuresFile))
//            throw new IllegalStateException("entryFeaturesFile == featuresFile");
//        if (entryFeaturesFile.equals(entriesFile))
//            throw new IllegalStateException("entryFeaturesFile == entriesFile");
//        if (featuresFile.equals(entriesFile))
//            throw new IllegalStateException("featuresFile == entriesFile");


        // Check that the instances file exists and is readable
        if (!inputFile.exists())
            throw new FileNotFoundException(
                    "instances file does not exist: " + inputFile);
        if (!inputFile.isFile())
            throw new IllegalStateException(
                    "instances file is not a normal data file: " + inputFile);
        if (!inputFile.canRead())
            throw new IllegalStateException(
                    "instances file is not readable: " + inputFile);

//        // For each output file, check that either it exists and it writeable,
//        // or that it does not exist but is creatable
//        if (entriesFile.exists() && (!entriesFile.isFile() || !entriesFile.
//                                     canWrite()))
//            throw new IllegalStateException(
//                    "entries file exists but is not writable: " + entriesFile);
//        if (!entriesFile.exists() && !entriesFile.getAbsoluteFile().
//                getParentFile().
//                canWrite()) {
//            throw new IllegalStateException(
//                    "entries file does not exists and can not be reated: " + entriesFile);
//        }
//        if (featuresFile.exists() && (!featuresFile.isFile() || !featuresFile.
//                                      canWrite()))
//            throw new IllegalStateException(
//                    "features file exists but is not writable: " + featuresFile);
//        if (!featuresFile.exists() && !featuresFile.getAbsoluteFile().
//                getParentFile().
//                canWrite()) {
//            throw new IllegalStateException(
//                    "features file does not exists and can not be reated: " + featuresFile);
//        }
        if (entryFeaturesFile.exists() && (!entryFeaturesFile.isFile() || !entryFeaturesFile.
                                           canWrite()))
            throw new IllegalStateException(
                    "entry-features file exists but is not writable: " + entryFeaturesFile);
        if (!entryFeaturesFile.exists() && !entryFeaturesFile.getAbsoluteFile().
                getParentFile().
                canWrite()) {
            throw new IllegalStateException(
                    "entry-features file does not exists and can not be reated: " + entryFeaturesFile);
        }
    }
}