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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import uk.ac.susx.mlcl.lib.Checks;
import uk.ac.susx.mlcl.lib.MiscUtil;
import uk.ac.susx.mlcl.lib.io.FileFactory;
import uk.ac.susx.mlcl.lib.io.IOUtil;
import uk.ac.susx.mlcl.lib.io.TempFileFactory;
import uk.ac.susx.mlcl.lib.tasks.AbstractTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @version 2nd December 2010
 * @author Hamish Morgan &lt;hamish.morgan@sussex.ac.uk%gt;
 */
@Parameters(commandDescription = "USAGE_CHUNK_COMMAND")
public class ChunkTask extends AbstractTask {

    private static final Logger LOG = Logger.getLogger(ChunkTask.class.getName());

    public static final int DEFAULT_MAX_CHUNK_SIZE = 5000000;

    public static final File DEFAULT_SOURCE_FILE = new File("-");

    private FileFactory chunkFileFactory =
            new TempFileFactory(this.getClass().getName() + ".", "");

    @Parameter(names = {"-c", "--max-chunk-size"},
               descriptionKey = "USAGE_MAX_CHUNK_SIZE")
    private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

    @Parameter(names = {"-i", "--input-file"},
               descriptionKey = "USAGE_INPUT_FILE")
    private File sourceFile = DEFAULT_SOURCE_FILE;

    private BlockingQueue<File> dstFileQueue = new LinkedBlockingDeque<File>();

    @Parameter(names = {"--charset"},
               descriptionKey = "USAGE_CHARSET")
    private Charset charset = IOUtil.DEFAULT_CHARSET;

    public final Charset getCharset() {
        return charset;
    }

    public final void setCharset(Charset charset) {
        Checks.checkNotNull(charset);
        this.charset = charset;
    }

    public ChunkTask() {
    }

    public ChunkTask(File srcFile, Charset charset) {
        setSrcFile(srcFile);
        setCharset(charset);
    }

    public ChunkTask(File srcFile, Charset charset, int maxChunkSize) {
        setSrcFile(srcFile);
        setMaxChunkSize(maxChunkSize);
        setCharset(charset);
    }

    @Override
    protected void initialiseTask() throws Exception {
    }

    @Override
    protected void finaliseTask() throws Exception {
    }

    public BlockingQueue<File> getDstFileQueue() {
        return dstFileQueue;
    }

    public void setDstFileQueue(BlockingQueue<File> dstFileQueue) {
        if (dstFileQueue == null)
            throw new NullPointerException("dstFileQueue is null");
        this.dstFileQueue = dstFileQueue;
    }

    protected void runTask() throws Exception {
        LOG.log(Level.INFO,
                "Chunking from file \"{0}\"; max-chunk-size={1}. (Thread:{2})",
                new Object[]{sourceFile,
                    MiscUtil.humanReadableBytes(getMaxChunkSize()),
                    Thread.currentThread().getName()});

        BufferedReader reader = null;
        BufferedWriter writer = null;
        File tmp = null;

        final int nlBytes = System.getProperty("line.separator").getBytes().length;

        try {
            reader = IOUtil.openReader(sourceFile, charset);
            int chunk = 1;
            int chunkBytesWritten = 0;
            String line = reader.readLine();
            int lineBytes = line == null ? 0
                    : line.getBytes().length + nlBytes;

            while (line != null) {

                try {
                    tmp = chunkFileFactory.createFile();
                    LOG.log(Level.INFO,
                            "Producing chunk {0} to file \"{1}\". (Thread:{2})",
                            new Object[]{chunk, tmp, Thread.currentThread().
                                getName()});
                    writer = IOUtil.openWriter(tmp, charset);

                    do {
                        writer.write(line);
                        writer.newLine();
                        chunkBytesWritten += lineBytes;
                        line = reader.readLine();
                        lineBytes = line == null ? 0 : line.getBytes().length + nlBytes;
                    } while (line != null && chunkBytesWritten + lineBytes < maxChunkSize);

                } finally {

                    if (writer != null) {
                        writer.flush();
                        writer.close();
                    }
                    if (tmp != null)
                        dstFileQueue.put(tmp);
                    chunkBytesWritten = 0;
                    chunk++;
                }
            }
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    public final void setMaxChunkSize(int maxChunkSize) {
        if (maxChunkSize <= 0)
            throw new IllegalArgumentException("maxChunkSize <= 0");
        this.maxChunkSize = maxChunkSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public final void setSrcFile(File sourceFile) {
        if (sourceFile == null)
            throw new NullPointerException("sourceFile is null");
        this.sourceFile = sourceFile;
    }

    public File getSrcFile() {
        return sourceFile;
    }

    public Collection<File> getDestFiles() {
        return Collections.unmodifiableCollection(dstFileQueue);
    }

    public FileFactory getChunkFileFactory() {
        return chunkFileFactory;
    }

    public void setChunkFileFactory(FileFactory chunkFileFactory) {
        this.chunkFileFactory = chunkFileFactory;
    }
}