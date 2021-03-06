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
package uk.ac.susx.mlcl.byblo.measure;

import uk.ac.susx.mlcl.lib.collect.SparseDoubleVector;

/**
 * Decorator that reverses the features sets, for calculating asymmetric
 * measures with the inputs the other way around.
 *
 * @author Hamish Morgan &lt;hamish.morgan@sussex.ac.uk%gt;
 */
public final class ReversedProximity implements Proximity {

    private final Proximity inner;

    public ReversedProximity(final Proximity inner) {
        this.inner = inner;
    }

    @Override
    public double shared(final SparseDoubleVector A, final SparseDoubleVector B) {
        return inner.shared(B, A);
    }

    @Override
    public double left(final SparseDoubleVector A) {
        return inner.right(A);
    }

    @Override
    public double right(final SparseDoubleVector B) {
        return inner.left(B);
    }

    @Override
    public double combine(final double shared, final double left,
                          final double right) {
        return inner.combine(shared, right, left);
    }

    @Override
    public boolean isSymmetric() {
        return inner.isSymmetric();
    }

    public Proximity getInner() {
        return inner;
    }

    @Override
    public String toString() {
        return "ReversedProximity{" + "inner=" + inner + '}';
    }

    @Override
    public void setFilteredFeatureId(int key) {
        inner.setFilteredFeatureId(key);
    }
}
