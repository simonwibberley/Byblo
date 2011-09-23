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

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import uk.ac.susx.mlcl.lib.collect.SparseDoubleVector;
import uk.ac.susx.mlcl.lib.io.Source;

/**
 *
 * @author Hamish Morgan (hamish.morgan@sussex.ac.uk)
 * @version 27th March 2011
 */
public class RandomSparseDoubleVectorSource 
        implements Source<SparseDoubleVector> {

    long seek = 0;

    Random rand = new Random();

    int size = 100;

    int cardinality = 1000;

    double delta = 0.001;

    public RandomSparseDoubleVectorSource() {
    }

    public SparseDoubleVector read() throws IOException {
        rand.setSeed(seek);

        double[] tmp = new double[size];
        for(int i = 0; i < size; i++) {
            tmp[i] = Math.abs(rand.nextGaussian()) + delta;
        }

        int[] keys = new int[size];
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            keys[i] = rand.nextInt(cardinality);
            values[i] = Math.abs(rand.nextGaussian()) + delta;
        }
        Arrays.sort(keys);
        return new SparseDoubleVector(keys, values, cardinality, size);
    }

    public boolean hasNext() throws IOException {
        return true;
    }

}