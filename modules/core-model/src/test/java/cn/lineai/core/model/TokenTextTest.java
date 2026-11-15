package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TokenTextTest {

    @Test public void keepsSmallAmountsExact() {
        assertEquals("0", TokenText.format(0));
        assertEquals("999", TokenText.format(999));
        assertEquals("980", TokenText.format(980));
    }

    @Test public void formatsThousandsWithLowercaseSuffix() {
        assertEquals("1k", TokenText.format(1000));
        assertEquals("1.2k", TokenText.format(1234));
        assertEquals("10k", TokenText.format(10000));
        assertEquals("12.3k", TokenText.format(12345));
        assertEquals("999.9k", TokenText.format(999949));
    }

    @Test public void formatsMillionsAndPromotesRoundingOverflow() {
        assertEquals("1m", TokenText.format(1000000));
        assertEquals("1.2m", TokenText.format(1234567));
        assertEquals("1m", TokenText.format(999999));
    }

    @Test public void clampsNegativeAmounts() {
        assertEquals("0", TokenText.format(-5));
    }
}
