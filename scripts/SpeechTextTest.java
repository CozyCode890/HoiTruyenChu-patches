// Tests of tts/SpeechText (plain Java, no JUnit). Run from the repo root:
//   javac -encoding UTF-8 -d build/speechtest extensions/itruyenchu/src/main/java/app/hoitruyenchu/extension/itruyenchu/patches/tts/SpeechText.java scripts/SpeechTextTest.java
//   java -cp build/speechtest app.hoitruyenchu.extension.itruyenchu.patches.tts.SpeechTextTest
package app.hoitruyenchu.extension.itruyenchu.patches.tts;

import java.util.*;

/**
 * Plain standalone test runner for {@link SpeechText} (no JUnit required).
 */
public class SpeechTextTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testEmptyAndNull();
        testOnlyEllipsisVariants();
        testOnlyPunctuationLine();
        testQuotedAndBracketedEllipsisOnly();
        testLeadingEllipsis();
        testTrailingEllipsis();
        testTrailingSpacedEllipsis();
        testMiddleEllipsisAndIndexMap();
        testMiddleUnicodeAndSpacedEllipsis();
        testMultipleEllipsesInLine();
        testQuotedEllipsisInsideSentence();
        testConsecutiveEllipses();
        testTildeBetweenNumbersNoSpace();
        testTildeBetweenNumbersWithSpaces();
        testTildeBeforeNumber();
        testTildeBeforeNumberMidSentence();
        testTildeOtherwiseMidPiece();
        testTildeOtherwiseEndOfPiece();
        testTildeBeforePunctuationAndAtStart();
        testFullWidthTilde();
        testTildeBeforeEllipsis();
        testPreservesVietnameseAndPunctuation();
        testSingleDotNotEllipsis();
        testFromGreaterThanZero();
        testFromMidTilde();
        testApostropheWithQuotedEllipsis();
        testRuleESpeechPrefixThenEllipsis();
        testRuleETrailingEllipsisInQuotes();
        testRuleESplitInsideQuotes();
        testRuleEBalancedQuotesUnchanged();
        testRuleECurlyQuotesAndBrackets();
        testRuleESpacesInsideStrippedQuotes();
        testRuleENestedUnbalancedWrappers();
        testRuleEApostrophePreserved();
        testRuleEMiddlePieceStrayQuotes();
        testRuleEFromOffsetUnbalanced();

        System.out.println("Summary: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testEmptyAndNull() {
        check("empty string", "", 0, Collections.<String>emptyList());
        check("null string", null, 0, Collections.<String>emptyList());
        check("whitespace only", "   \t  ", 0, Collections.<String>emptyList());
        check("from == length", "Hắn đi.", 7, Collections.<String>emptyList());
    }

    private static void testOnlyEllipsisVariants() {
        check("three dots", "...", 0, Collections.<String>emptyList());
        check("four dots", "....", 0, Collections.<String>emptyList());
        check("two dots", "..", 0, Collections.<String>emptyList());
        check("spaced dots", ". . .", 0, Collections.<String>emptyList());
        check("unicode ellipsis", "\u2026", 0, Collections.<String>emptyList());
        check("double unicode ellipsis", "\u2026\u2026", 0, Collections.<String>emptyList());
    }

    private static void testOnlyPunctuationLine() {
        check("only exclamation", "!", 0, Collections.<String>emptyList());
        check("only dashes", "---", 0, Collections.<String>emptyList());
        check("only punctuation", ".,?;:", 0, Collections.<String>emptyList());
    }

    private static void testQuotedAndBracketedEllipsisOnly() {
        check("ascii double quotes ellipsis", "\"...\"", 0, Collections.<String>emptyList());
        check("curly quotes unicode ellipsis", "\u201C\u2026\u201D", 0, Collections.<String>emptyList());
        check("single quotes ellipsis", "'...'", 0, Collections.<String>emptyList());
        check("parens ellipsis", "(...)", 0, Collections.<String>emptyList());
        check("brackets ellipsis", "[...]", 0, Collections.<String>emptyList());
        check("quoted ellipsis with exclamation", "\"...!\"", 0, Collections.<String>emptyList());
        check("nested quoted paren ellipsis", "\"(...)\"", 0, Collections.<String>emptyList());
    }

    private static void testLeadingEllipsis() {
        String s = "...Hắn đi.";
        SpeechText.Prepared p = check("leading ellipsis", s, 0, Collections.singletonList("Hắn đi."));
        if (p != null) {
            assertStarts("leading ellipsis starts", p.starts.get(0), new int[]{3, 4, 5, 6, 7, 8, 9});
        }
    }

    private static void testTrailingEllipsis() {
        String s = "Hắn đi...";
        SpeechText.Prepared p = check("trailing ellipsis", s, 0, Collections.singletonList("Hắn đi"));
        if (p != null) {
            assertStarts("trailing ellipsis starts", p.starts.get(0), new int[]{0, 1, 2, 3, 4, 5});
        }
    }

    private static void testTrailingSpacedEllipsis() {
        String s = "Hắn đi. . .";
        SpeechText.Prepared p = check("trailing spaced ellipsis", s, 0, Collections.singletonList("Hắn đi"));
        if (p != null) {
            assertStarts("trailing spaced ellipsis starts", p.starts.get(0), new int[]{0, 1, 2, 3, 4, 5});
        }
    }

    private static void testMiddleEllipsisAndIndexMap() {
        String s = "Hắn... đi rồi.";
        SpeechText.Prepared p = check("middle ellipsis", s, 0, Arrays.asList("Hắn", "đi rồi."));
        if (p != null) {
            assertStarts("middle ellipsis piece 0", p.starts.get(0), new int[]{0, 1, 2});
            assertStarts("middle ellipsis piece 1", p.starts.get(1), new int[]{7, 8, 9, 10, 11, 12, 13});
            if (p.starts.get(1)[0] != s.indexOf('đ')) {
                fail("middle ellipsis 'đ' index", "expected " + s.indexOf('đ') + ", got " + p.starts.get(1)[0]);
            } else {
                pass("middle ellipsis 'đ' index");
            }
        }
    }

    private static void testMiddleUnicodeAndSpacedEllipsis() {
        String u = "Hắn\u2026 đi rồi.";
        SpeechText.Prepared pu = check("middle unicode ellipsis", u, 0, Arrays.asList("Hắn", "đi rồi."));
        if (pu != null) {
            assertStarts("middle unicode ellipsis piece 1", pu.starts.get(1), new int[]{5, 6, 7, 8, 9, 10, 11});
        }

        String sp = "Hắn. . . đi rồi.";
        SpeechText.Prepared psp = check("middle spaced ellipsis", sp, 0, Arrays.asList("Hắn", "đi rồi."));
        if (psp != null) {
            assertStarts("middle spaced ellipsis piece 1", psp.starts.get(1), new int[]{9, 10, 11, 12, 13, 14, 15});
        }
    }

    private static void testMultipleEllipsesInLine() {
        String s = "Hắn... nhìn nàng... rồi cười.";
        SpeechText.Prepared p = check("multiple ellipses", s, 0, Arrays.asList("Hắn", "nhìn nàng", "rồi cười."));
        if (p != null && p.starts.get(2)[0] != 20) {
            fail("multiple ellipses piece 2 start", "expected 20, got " + p.starts.get(2)[0]);
        } else if (p != null) {
            pass("multiple ellipses piece 2 start");
        }
    }

    private static void testQuotedEllipsisInsideSentence() {
        check("trailing quoted ellipsis", "Hắn nói: \"...\"", 0, Collections.singletonList("Hắn nói:"));
        SpeechText.Prepared p = check("mid quoted ellipsis", "Hắn \"...\" một tiếng.", 0, Arrays.asList("Hắn", "một tiếng."));
        if (p != null) {
            assertStarts("mid quoted ellipsis piece 1", p.starts.get(1), new int[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19});
        }
        check("mid paren unicode ellipsis", "Hắn (\u2026) một tiếng.", 0, Arrays.asList("Hắn", "một tiếng."));
    }

    private static void testConsecutiveEllipses() {
        String s = "Hắn... ... đi.";
        SpeechText.Prepared p = check("consecutive ellipses", s, 0, Arrays.asList("Hắn", "đi."));
        if (p != null) {
            assertStarts("consecutive ellipses piece 1", p.starts.get(1), new int[]{11, 12, 13});
        }
    }

    private static void testTildeBetweenNumbersNoSpace() {
        String s = "1~2";
        SpeechText.Prepared p = check("tilde between numbers no space", s, 0, Collections.singletonList("1 đến 2"));
        if (p != null) {
            assertStarts("1~2 starts", p.starts.get(0), new int[]{0, 1, 1, 1, 1, 1, 2});
        }
    }

    private static void testTildeBetweenNumbersWithSpaces() {
        String s = "10 ~ 20";
        SpeechText.Prepared p = check("tilde between numbers with spaces", s, 0, Collections.singletonList("10 đến 20"));
        if (p != null) {
            assertStarts("10 ~ 20 starts", p.starts.get(0), new int[]{0, 1, 2, 3, 3, 3, 4, 5, 6});
        }
    }

    private static void testTildeBeforeNumber() {
        String s = "~100 người";
        SpeechText.Prepared p = check("tilde before number", s, 0, Collections.singletonList("khoảng 100 người"));
        if (p != null) {
            assertStarts("~100 người starts", p.starts.get(0),
                    new int[]{0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        }
    }

    private static void testTildeBeforeNumberMidSentence() {
        check("tilde before number after space", "Có ~100 người", 0, Collections.singletonList("Có khoảng 100 người"));
        check("tilde before number no space", "Có~100 người", 0, Collections.singletonList("Có khoảng 100 người"));
        check("tilde before number in parens", "(~100 người)", 0, Collections.singletonList("(khoảng 100 người)"));
    }

    private static void testTildeOtherwiseMidPiece() {
        String s = "Hảo~ đi thôi";
        SpeechText.Prepared p = check("tilde otherwise mid piece", s, 0, Collections.singletonList("Hảo, đi thôi"));
        if (p != null) {
            assertStarts("Hảo~ đi thôi starts", p.starts.get(0),
                    new int[]{0, 1, 2, 3, 3, 5, 6, 7, 8, 9, 10, 11});
        }
    }

    private static void testTildeOtherwiseEndOfPiece() {
        SpeechText.Prepared p1 = check("single tilde at end", "Huynh~", 0, Collections.singletonList("Huynh"));
        if (p1 != null) {
            assertStarts("Huynh~ starts", p1.starts.get(0), new int[]{0, 1, 2, 3, 4});
        }
        SpeechText.Prepared p2 = check("triple tilde at end", "Á~~~", 0, Collections.singletonList("Á"));
        if (p2 != null) {
            assertStarts("Á~~~ starts", p2.starts.get(0), new int[]{0});
        }
    }

    private static void testTildeBeforePunctuationAndAtStart() {
        check("tilde before exclamation", "Á~~~!", 0, Collections.singletonList("Á!"));
        check("tilde before comma", "Hảo~, đi thôi", 0, Collections.singletonList("Hảo, đi thôi"));
        check("tilde at start of words", "~Hảo", 0, Collections.singletonList("Hảo"));
    }

    private static void testFullWidthTilde() {
        check("fullwidth tilde numbers", "1\uFF5E2", 0, Collections.singletonList("1 đến 2"));
        check("fullwidth tilde approx", "\uFF5E100 người", 0, Collections.singletonList("khoảng 100 người"));
        check("fullwidth tilde mid", "Hảo\uFF5E đi thôi", 0, Collections.singletonList("Hảo, đi thôi"));
        check("fullwidth tilde end", "Huynh\uFF5E", 0, Collections.singletonList("Huynh"));
    }

    private static void testTildeBeforeEllipsis() {
        check("tilde before ellipsis", "Hảo~... đi thôi", 0, Arrays.asList("Hảo", "đi thôi"));
    }

    private static void testPreservesVietnameseAndPunctuation() {
        String s = "Đừng đi, hắn nói: 1.5 không phải 2! Có đúng không?";
        SpeechText.Prepared p = check("vietnamese and punctuation", s, 0, Collections.singletonList(s));
        if (p != null) {
            int[] expected = new int[s.length()];
            for (int i = 0; i < expected.length; i++) {
                expected[i] = i;
            }
            assertStarts("vietnamese identity starts", p.starts.get(0), expected);
        }
    }

    private static void testSingleDotNotEllipsis() {
        String s = "Hắn đi. Rồi về.";
        check("single dot not ellipsis", s, 0, Collections.singletonList(s));
    }

    private static void testFromGreaterThanZero() {
        String s = "Chương 1: Hắn... đi rồi.";
        SpeechText.Prepared p = check("from > 0 ellipsis", s, 10, Arrays.asList("Hắn", "đi rồi."));
        if (p != null) {
            assertStarts("from > 0 piece 0", p.starts.get(0), new int[]{10, 11, 12});
            assertStarts("from > 0 piece 1", p.starts.get(1), new int[]{17, 18, 19, 20, 21, 22, 23});
        }
    }

    private static void testFromMidTilde() {
        String s = "1~2";
        SpeechText.Prepared p = check("from mid tilde", s, 1, Collections.singletonList("khoảng 2"));
        if (p != null) {
            assertStarts("from mid tilde starts", p.starts.get(0), new int[]{1, 1, 1, 1, 1, 1, 1, 2});
        }
    }

    private static void testApostropheWithQuotedEllipsis() {
        String s = "It's '...' ok.";
        SpeechText.Prepared p = check("apostrophe with quoted ellipsis", s, 0, Arrays.asList("It's", "ok."));
        if (p != null) {
            assertStarts("apostrophe piece 0", p.starts.get(0), new int[]{0, 1, 2, 3});
            assertStarts("apostrophe piece 1", p.starts.get(1), new int[]{11, 12, 13});
        }
    }

    private static void testRuleESpeechPrefixThenEllipsis() {
        String s = "Hắn nói: \"...Ta đi.\"";
        SpeechText.Prepared p = check("rule E speech prefix then ellipsis", s, 0,
                Arrays.asList("Hắn nói:", "Ta đi."));
        if (p != null) {
            assertStarts("rule E speech prefix piece 0", p.starts.get(0), new int[]{0, 1, 2, 3, 4, 5, 6, 7});
            assertStarts("rule E speech prefix piece 1", p.starts.get(1), new int[]{13, 14, 15, 16, 17, 18});
        }
    }

    private static void testRuleETrailingEllipsisInQuotes() {
        String s = "\"Tỉnh rồi sao...\"";
        SpeechText.Prepared p = check("rule E trailing ellipsis in quotes", s, 0,
                Collections.singletonList("Tỉnh rồi sao"));
        if (p != null) {
            assertStarts("rule E trailing ellipsis starts", p.starts.get(0),
                    new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        }
    }

    private static void testRuleESplitInsideQuotes() {
        String s = "\"Ta... ta không biết.\"";
        SpeechText.Prepared p = check("rule E split inside quotes", s, 0,
                Arrays.asList("Ta", "ta không biết."));
        if (p != null) {
            assertStarts("rule E split inside quotes piece 0", p.starts.get(0), new int[]{1, 2});
            assertStarts("rule E split inside quotes piece 1", p.starts.get(1),
                    new int[]{7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20});
        }
    }

    private static void testRuleEBalancedQuotesUnchanged() {
        check("rule E balanced ascii quotes no ellipsis", "\"Ta đi.\"", 0,
                Collections.singletonList("\"Ta đi.\""));
        check("rule E balanced quotes before ellipsis", "\"Ta đi.\"... rồi.", 0,
                Arrays.asList("\"Ta đi.\"", "rồi."));
        check("rule E balanced parens no ellipsis", "(Ta đi.)", 0,
                Collections.singletonList("(Ta đi.)"));
        check("rule E balanced single quotes with apostrophe", "'It's ok'", 0,
                Collections.singletonList("'It's ok'"));
    }

    private static void testRuleECurlyQuotesAndBrackets() {
        check("rule E curly double quotes split", "\u201CTa\u2026 ta không biết.\u201D", 0,
                Arrays.asList("Ta", "ta không biết."));
        check("rule E curly quotes after colon ellipsis", "Hắn nói: \u201C...Ta đi.\u201D", 0,
                Arrays.asList("Hắn nói:", "Ta đi."));
        check("rule E parens split", "(Ta... ta đi.)", 0,
                Arrays.asList("Ta", "ta đi."));
        check("rule E corner brackets split", "\u300CTa... ta đi.\u300D", 0,
                Arrays.asList("Ta", "ta đi."));
    }

    private static void testRuleESpacesInsideStrippedQuotes() {
        String s = "\" Tỉnh rồi sao ...\"";
        SpeechText.Prepared p = check("rule E spaces inside stripped quotes", s, 0,
                Collections.singletonList("Tỉnh rồi sao"));
        if (p != null) {
            assertStarts("rule E spaces re-trimmed starts", p.starts.get(0),
                    new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13});
        }
    }

    private static void testRuleENestedUnbalancedWrappers() {
        check("rule E nested quote and paren split", "\"(Ta... ta)\"", 0,
                Arrays.asList("Ta", "ta"));
        check("rule E double paren with one matched", "((Ta)...)", 0,
                Collections.singletonList("(Ta)"));
    }

    private static void testRuleEApostrophePreserved() {
        check("rule E apostrophe It's with ellipsis", "It's... ok.", 0,
                Arrays.asList("It's", "ok."));
        check("rule E apostrophe o'clock with ellipsis", "At o'clock... wait.", 0,
                Arrays.asList("At o'clock", "wait."));
        check("rule E quoted It's split", "'It's... ok.'", 0,
                Arrays.asList("It's", "ok."));
        check("rule E curly apostrophe in quoted split", "\u2018It\u2019s... ok.\u2019", 0,
                Arrays.asList("It\u2019s", "ok."));
    }

    private static void testRuleEMiddlePieceStrayQuotes() {
        check("rule E stray quotes on middle pieces", "A... \"B... C\"... D", 0,
                Arrays.asList("A", "B", "C", "D"));
        check("rule E comma after closing quote split", "\"Ta...\", hắn nói.", 0,
                Arrays.asList("Ta", ", hắn nói."));
    }

    private static void testRuleEFromOffsetUnbalanced() {
        String s = "\"Hắn nói... Ta đi.\"";
        SpeechText.Prepared p = check("rule E from offset unbalanced trailing quote", s, 12,
                Collections.singletonList("Ta đi."));
        if (p != null) {
            assertStarts("rule E from offset starts", p.starts.get(0), new int[]{12, 13, 14, 15, 16, 17});
        }
    }

    private static SpeechText.Prepared check(String name, String original, int from, List<String> expectedPieces) {
        SpeechText.Prepared p = SpeechText.prepare(original, from);
        if (p == null) {
            fail(name, "prepare returned null");
            return null;
        }
        if (p.pieces.size() != p.starts.size()) {
            fail(name, "pieces.size()=" + p.pieces.size() + " != starts.size()=" + p.starts.size());
            return null;
        }
        if (!p.pieces.equals(expectedPieces)) {
            fail(name, "expected " + expectedPieces + ", got " + p.pieces);
            return null;
        }
        int minIdx = original == null ? 0 : Math.max(0, Math.min(original.length(), from));
        int maxIdx = original == null ? 0 : original.length();
        for (int i = 0; i < p.pieces.size(); i++) {
            String piece = p.pieces.get(i);
            int[] map = p.starts.get(i);
            if (map == null || map.length != piece.length()) {
                fail(name, "piece " + i + " length " + piece.length()
                        + " != starts length " + (map == null ? "null" : map.length));
                return null;
            }
            for (int idx : map) {
                if (idx < minIdx || idx >= maxIdx) {
                    fail(name, "piece " + i + " start index out of range: " + idx);
                    return null;
                }
            }
        }
        pass(name);
        return p;
    }

    private static void assertStarts(String name, int[] actual, int[] expected) {
        if (!Arrays.equals(actual, expected)) {
            fail(name, "expected " + Arrays.toString(expected) + ", got " + Arrays.toString(actual));
        } else {
            pass(name);
        }
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void fail(String name, String reason) {
        failed++;
        System.out.println("FAIL: " + name + " - " + reason);
    }
}
