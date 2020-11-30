package org.apfloat.internal;

import java.math.BigInteger;

import org.apfloat.ApfloatRuntimeException;
import org.apfloat.spi.CarryCRTStepStrategy;
import org.apfloat.spi.DataStorage;
import static org.apfloat.internal.DoubleModConstants.*;

/**
 * Class for performing the final steps of a three-modulus
 * Number Theoretic Transform based convolution. Works for the
 * <code>double</code> type.<p>
 *
 * All access to this class must be externally synchronized.
 *
 * @since 1.7.0
 * @version 1.8.0
 * @author Mikko Tommila
 */

public class DoubleCarryCRTStepStrategy
    extends DoubleCRTMath
    implements CarryCRTStepStrategy<double[]>
{
    /**
     * Creates a carry-CRT steps object using the specified radix.
     *
     * @param radix The radix that will be used.
     */

    public DoubleCarryCRTStepStrategy(int radix)
    {
        super(radix);
    }

    public double[] crt(DataStorage resultMod0, DataStorage resultMod1, DataStorage resultMod2, DataStorage dataStorage, long size, long resultSize, long offset, long length)
        throws ApfloatRuntimeException
    {
        long skipSize = (offset == 0 ? size - resultSize + 1: 0);   // For the first block, ignore the first 1-3 elements
        long lastSize = (offset + length == size ? 1: 0);           // For the last block, add 1 element
        long nonLastSize = 1 - lastSize;                            // For the other than last blocks, move 1 element
        long subResultSize = length - skipSize + lastSize;

        long subStart = size - offset,
             subEnd = subStart - length,
             subResultStart = size - offset - length + nonLastSize + subResultSize,
             subResultEnd = subResultStart - subResultSize;

        DataStorage.Iterator src0 = resultMod0.iterator(DataStorage.READ, subStart, subEnd),
                             src1 = resultMod1.iterator(DataStorage.READ, subStart, subEnd),
                             src2 = resultMod2.iterator(DataStorage.READ, subStart, subEnd),
                             dst = dataStorage.iterator(DataStorage.WRITE, subResultStart, subResultEnd);

        double[] carryResult = new double[3],
                  sum = new double[3],
                  tmp = new double[3];

        // Preliminary carry-CRT calculation (happens in parallel in multiple blocks)
        for (long i = 0; i < length; i++)
        {
            double y0 = MATH_MOD_0.modMultiply(T0, src0.getDouble()),
                    y1 = MATH_MOD_1.modMultiply(T1, src1.getDouble()),
                    y2 = MATH_MOD_2.modMultiply(T2, src2.getDouble());

            multiply(M12, y0, sum);
            multiply(M02, y1, tmp);

            if (add(tmp, sum) != 0 ||
                compare(sum, M012) >= 0)
            {
                subtract(M012, sum);
            }

            multiply(M01, y2, tmp);

            if (add(tmp, sum) != 0 ||
                compare(sum, M012) >= 0)
            {
                subtract(M012, sum);
            }

            add(sum, carryResult);

            double result = divide(carryResult);

            // In the first block, ignore the first element (it's zero in full precision calculations)
            // and possibly one or two more in limited precision calculations
            if (i >= skipSize)
            {
                dst.setDouble(result);
                dst.next();
            }

            src0.next();
            src1.next();
            src2.next();
        }

        // Calculate the last words (in base math)
        double result0 = divide(carryResult);
        double result1 = carryResult[2];

        assert (carryResult[0] == 0);
        assert (carryResult[1] == 0);

        // Last block has one extra element (corresponding to the one skipped in the first block)
        if (subResultSize == length - skipSize + 1)
        {
            dst.setDouble(result0);
            dst.close();

            result0 = result1;
            assert (result1 == 0);
        }

        double[] results = { result1, result0 };

        return results;
    }

    public double[] carry(DataStorage dataStorage, long size, long resultSize, long offset, long length, double[] results, double[] previousResults)
        throws ApfloatRuntimeException
    {
        long skipSize = (offset == 0 ? size - resultSize + 1: 0);   // For the first block, ignore the first 1-3 elements
        long lastSize = (offset + length == size ? 1: 0);           // For the last block, add 1 element
        long nonLastSize = 1 - lastSize;                            // For the other than last blocks, move 1 element
        long subResultSize = length - skipSize + lastSize;

        long subResultStart = size - offset - length + nonLastSize + subResultSize,
             subResultEnd = subResultStart - subResultSize;

        // Get iterators for the previous block carries, and dst, padded with this block's carries
        // Note that size could be 1 but carries size is 2
        DataStorage.Iterator src = arrayIterator(previousResults);
        DataStorage.Iterator dst = compositeIterator(dataStorage.iterator(DataStorage.READ_WRITE, subResultStart, subResultEnd), subResultSize, arrayIterator(results));

        // Propagate base addition through dst, and this block's carries
        double carry = baseAdd(dst, src, 0, dst, previousResults.length);
        carry = baseCarry(dst, carry, subResultSize);
        dst.close();                                                    // Iterator likely was not iterated to end

        assert (carry == 0);

        return results;
    }

    private double baseCarry(DataStorage.Iterator srcDst, double carry, long size)
        throws ApfloatRuntimeException
    {
        for (long i = 0; i < size && carry > 0; i++)
        {
            carry = baseAdd(srcDst, null, carry, srcDst, 1);
        }

        return carry;
    }

    // Wrap an array in a simple reverse-order iterator, padded with zeros
    private static DataStorage.Iterator arrayIterator(final double[] data)
    {
        return new DataStorage.Iterator()
        {
            public boolean hasNext()
            {
                return true;
            }

            public void next()
            {
                this.position--;
            }

            public double getDouble()
            {
                assert (this.position >= 0);
                return data[this.position];
            }

            public void setDouble(double value)
            {
                assert (this.position >= 0);
                data[this.position] = value;
            }

            private static final long serialVersionUID = 1L;

            private int position = data.length - 1;
        };
    }

    // Composite iterator, made by concatenating two iterators
    private static DataStorage.Iterator compositeIterator(final DataStorage.Iterator iterator1, final long size, final DataStorage.Iterator iterator2)
    {
        return new DataStorage.Iterator()
        {
            public boolean hasNext()
            {
                return (this.position < size ? iterator1.hasNext() : iterator2.hasNext());
            }

            public void next()
                throws ApfloatRuntimeException
            {
                (this.position < size ? iterator1 : iterator2).next();
                this.position++;
            }

            public double getDouble()
                throws ApfloatRuntimeException
            {
                return (this.position < size ? iterator1 : iterator2).getDouble();
            }

            public void setDouble(double value)
                throws ApfloatRuntimeException
            {
                (this.position < size ? iterator1 : iterator2).setDouble(value);
            }

            public void close()
                throws ApfloatRuntimeException
            {
                (this.position < size ? iterator1 : iterator2).close();
            }

            private static final long serialVersionUID = 1L;

            private long position;
        };
    }

    private static final long serialVersionUID = 2974874464027705533L;

    private static final DoubleModMath MATH_MOD_0,
                                        MATH_MOD_1,
                                        MATH_MOD_2;
    private static final double T0,
                                 T1,
                                 T2;
    private static final double[] M01,
                                   M02,
                                   M12,
                                   M012;

    static
    {
        MATH_MOD_0 = new DoubleModMath();
        MATH_MOD_1 = new DoubleModMath();
        MATH_MOD_2 = new DoubleModMath();

        MATH_MOD_0.setModulus(MODULUS[0]);
        MATH_MOD_1.setModulus(MODULUS[1]);
        MATH_MOD_2.setModulus(MODULUS[2]);

        // Probably sub-optimal, but it's a one-time operation

        BigInteger base = BigInteger.valueOf(Math.abs((long) MAX_POWER_OF_TWO_BASE)),   // In int case the base is 0x80000000
                   m0 = BigInteger.valueOf((long) MODULUS[0]),
                   m1 = BigInteger.valueOf((long) MODULUS[1]),
                   m2 = BigInteger.valueOf((long) MODULUS[2]),
                   m01 = m0.multiply(m1),
                   m02 = m0.multiply(m2),
                   m12 = m1.multiply(m2);

        T0 = m12.modInverse(m0).doubleValue();
        T1 = m02.modInverse(m1).doubleValue();
        T2 = m01.modInverse(m2).doubleValue();

        M01 = new double[2];
        M02 = new double[2];
        M12 = new double[2];
        M012 = new double[3];

        BigInteger[] qr = m01.divideAndRemainder(base);
        M01[0] = qr[0].doubleValue();
        M01[1] = qr[1].doubleValue();

        qr = m02.divideAndRemainder(base);
        M02[0] = qr[0].doubleValue();
        M02[1] = qr[1].doubleValue();

        qr = m12.divideAndRemainder(base);
        M12[0] = qr[0].doubleValue();
        M12[1] = qr[1].doubleValue();

        qr = m0.multiply(m12).divideAndRemainder(base);
        M012[2] = qr[1].doubleValue();
        qr = qr[0].divideAndRemainder(base);
        M012[0] = qr[0].doubleValue();
        M012[1] = qr[1].doubleValue();
    }
}
