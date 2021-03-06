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

import uk.ac.susx.mlcl.byblo.io.TokenPairSource;
import java.io.File;
import java.nio.charset.Charset;
import org.junit.Test;
import uk.ac.susx.mlcl.byblo.io.WeightedTokenSource;
import static org.junit.Assert.*;
import static uk.ac.susx.mlcl.TestConstants.*;

/**
 *
 * @author Hamish Morgan &lt;hamish.morgan@sussex.ac.uk&gt;
 */
public class CountTaskTest {

    private static final String subject = CountTask.class.getName();

    private void runWithAPI(File inInst, File outE, File outF, File outEF,
            Charset charset)
            throws Exception {
        final CountTask countTask = new CountTask();
        countTask.setInstancesFile(inInst);
        countTask.setEntriesFile(outE);
        countTask.setFeaturesFile(outF);
        countTask.setEntryFeaturesFile(outEF);
        countTask.setCharset(charset);
        countTask.run();
        while (countTask.isExceptionThrown()) {
            countTask.throwException();
        }

        assertTrue("Output files not created: " + outE, outE.exists());
        assertTrue("Output files not created: " + outF, outF.exists());
        assertTrue("Output files not created: " + outEF, outEF.exists());

        assertTrue("Empty output file found: " + outE, outE.length() > 0);
        assertTrue("Empty output file found: " + outF, outF.length() > 0);
        assertTrue("Empty output file found: " + outEF, outEF.length() > 0);
    }

    private void runExpectingNullPointer(File inInst, File outE, File outF,
            File outEF, Charset charset) throws Exception {
        try {
            runWithAPI(inInst, outE, outF, outEF, charset);
            fail("NullPointerException should have been thrown.");
        } catch (NullPointerException ex) {
            // pass
        }
    }

    private void runExpectingIllegalState(File inInst, File outE, File outF,
            File outEF, Charset charset) throws Exception {
        try {
            runWithAPI(inInst, outE, outF, outEF, charset);
            fail("IllegalStateException should have been thrown.");
        } catch (IllegalStateException ex) {
            // pass 
        }
    }

    @Test
    public void testRunOnFruitAPI() throws Exception {
        System.out.println("Testing " + subject + " on " + TEST_FRUIT_INPUT);

        final String fruitPrefix = TEST_FRUIT_INPUT.getName();
        final File eActual = new File(TEST_OUTPUT_DIR, fruitPrefix + ".entries");
        final File fActual = new File(TEST_OUTPUT_DIR, fruitPrefix + ".features");
        final File efActual = new File(TEST_OUTPUT_DIR,
                fruitPrefix + ".entryFeatures");

        eActual.delete();
        fActual.delete();
        efActual.delete();

        runWithAPI(TEST_FRUIT_INPUT, eActual, fActual, efActual,
                DEFAULT_CHARSET);

        assertTrue("Output entries file differs from sampledata file.",
                WeightedTokenSource.equal(eActual, TEST_FRUIT_ENTRIES,
                DEFAULT_CHARSET));
        assertTrue("Output features file differs from test data file.",
                WeightedTokenSource.equal(fActual, TEST_FRUIT_FEATURES,
                DEFAULT_CHARSET));
        assertTrue("Output entry/features file differs from test data file.",
                TokenPairSource.equal(efActual, TEST_FRUIT_ENTRY_FEATURES,
                DEFAULT_CHARSET));
    }

    @Test(timeout = 1000)
    public void testMissingParameters() throws Exception {
        System.out.println("Testing " + subject + " for bad parameterisation.");

        final File instIn = TEST_FRUIT_INPUT;
        final String fruitPrefix = TEST_FRUIT_INPUT.getName();
        final File eOut = new File(TEST_OUTPUT_DIR, fruitPrefix + ".entries");
        final File fOut = new File(TEST_OUTPUT_DIR, fruitPrefix + ".features");
        final File efOut = new File(TEST_OUTPUT_DIR,
                fruitPrefix + ".entryFeatures");

        runExpectingNullPointer(null, eOut, fOut, efOut, DEFAULT_CHARSET);
        runExpectingNullPointer(instIn, null, fOut, efOut, DEFAULT_CHARSET);
        runExpectingNullPointer(instIn, eOut, null, efOut, DEFAULT_CHARSET);
        runExpectingNullPointer(instIn, eOut, fOut, null, DEFAULT_CHARSET);
        runExpectingNullPointer(instIn, eOut, fOut, efOut, null);

        File dir = TEST_OUTPUT_DIR;
        runExpectingIllegalState(dir, eOut, fOut, efOut, DEFAULT_CHARSET);
        runExpectingIllegalState(instIn, dir, fOut, efOut, DEFAULT_CHARSET);
        runExpectingIllegalState(instIn, eOut, dir, efOut, DEFAULT_CHARSET);
        runExpectingIllegalState(instIn, eOut, fOut, dir, DEFAULT_CHARSET);
    }
}
