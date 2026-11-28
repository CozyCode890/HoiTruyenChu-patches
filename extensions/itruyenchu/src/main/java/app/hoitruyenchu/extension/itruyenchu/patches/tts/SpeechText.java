package app.hoitruyenchu.extension.itruyenchu.patches.tts;

import java.util.*;
import java.util.regex.*;

/**
 * Prepares Vietnamese novel lines for Android TextToSpeech by removing/splitting on ellipses,
 * stripping empty-after-ellipsis or unbalanced edge quotes/brackets, and expanding or silencing
 * tildes while keeping a character-level index map back to the original line.
 */
final class SpeechText {

    /** Result of preparing one line for the TTS engine. */
    static final class Prepared {
        /** Pieces to speak in order; a pause (silence) is played AFTER every piece except the last. */
        final java.util.List<String> pieces;
        /** For every piece, the index in the ORIGINAL line of each character of the piece:
         *  starts.get(p)[i] = index in the original line of character i of piece p. Same length as the piece. */
        final java.util.List<int[]> starts;

        Prepared(List<String> pieces, List<int[]> starts) {
            this.pieces = pieces;
            this.starts = starts;
        }
    }

    /** Matches a run of one or more '…' (\u2026) or 2+ ASCII dots with optional horizontal spaces between them. */
    private static final Pattern ELLIPSIS_PATTERN =
            Pattern.compile("\\u2026+|\\.(?:[ \\t\\u00A0]*\\.)+");

    /** Opening and closing quote/bracket pairs that may wrap an ellipsis or piece edges. */
    private static final String OPEN_WRAPPERS  = "\"\u201C'\u2018([{\u300C\u300E\uFF08\u3010\u300A";
    private static final String CLOSE_WRAPPERS = "\"\u201D'\u2019)]}\u300D\u300F\uFF09\u3011\u300B";

    /** Pause or closing punctuation that should not be preceded by an inserted ", ". */
    private static final String PAUSE_OR_CLOSING_PUNCT =
            ",.?!;:)]}\u201D\u2019\uFF09\u3011\u300D\u300F\u300B\u2014\u2013-\"'";

    private SpeechText() {
    }

    /**
     * Prepares original.substring(from) (from = 0..length) for speaking. Never returns null; pieces may be
     * empty (nothing to say: the line is only punctuation / ellipsis), then the caller plays one pause.
     */
    static Prepared prepare(String original, int from) {
        if (original == null) {
            return emptyPrepared();
        }
        int origLen = original.length();
        if (from < 0) {
            from = 0;
        } else if (from > origLen) {
            from = origLen;
        }
        if (from == origLen) {
            return emptyPrepared();
        }

        String sub = original.substring(from);
        int subLen = sub.length();
        boolean[] isSplit = markSplitRegions(sub);

        List<String> pieces = new ArrayList<String>();
        List<int[]> starts = new ArrayList<int[]>();

        int pos = 0;
        while (pos < subLen) {
            while (pos < subLen && isSplit[pos]) {
                pos++;
            }
            if (pos >= subLen) {
                break;
            }
            int rawStart = pos;
            while (pos < subLen && !isSplit[pos]) {
                pos++;
            }
            int rawEnd = pos;

            // Trim whitespace before tilde processing so leading/trailing tildes know piece boundaries.
            while (rawStart < rawEnd && isSpace(sub.charAt(rawStart))) {
                rawStart++;
            }
            while (rawEnd > rawStart && isSpace(sub.charAt(rawEnd - 1))) {
                rawEnd--;
            }
            if (rawStart >= rawEnd) {
                continue;
            }

            PieceBuilder built = replaceTildes(sub, rawStart, rawEnd, from);
            appendIfSpeakable(built, pieces, starts);
        }

        return new Prepared(
                Collections.unmodifiableList(pieces),
                Collections.unmodifiableList(starts));
    }

    private static Prepared emptyPrepared() {
        return new Prepared(
                Collections.<String>emptyList(),
                Collections.<int[]>emptyList());
    }

    /**
     * Marks ellipses and any quote/bracket spans that contain an ellipsis and no letters or digits.
     */
    private static boolean[] markSplitRegions(String sub) {
        int len = sub.length();
        boolean[] isSplit = new boolean[len];

        Matcher m = ELLIPSIS_PATTERN.matcher(sub);
        while (m.find()) {
            Arrays.fill(isSplit, m.start(), m.end(), true);
        }

        // Repeat to handle nested wrappers such as "(...)".
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int w = 0; w < OPEN_WRAPPERS.length(); w++) {
                char open = OPEN_WRAPPERS.charAt(w);
                char close = CLOSE_WRAPPERS.charAt(w);
                if (markEmptyQuotedSpans(sub, isSplit, open, close)) {
                    changed = true;
                }
            }
        }
        return isSplit;
    }

    private static boolean markEmptyQuotedSpans(String sub, boolean[] isSplit, char open, char close) {
        int len = sub.length();
        boolean changed = false;

        for (int i = 0; i < len; i++) {
            if (isSplit[i] || sub.charAt(i) != open) {
                continue;
            }
            boolean hasSplit = false;
            for (int j = i + 1; j < len; j++) {
                char c = sub.charAt(j);
                if (isSplit[j]) {
                    hasSplit = true;
                    continue;
                }
                if (Character.isLetterOrDigit(c)) {
                    break;
                }
                if (c == close) {
                    if (hasSplit) {
                        Arrays.fill(isSplit, i, j + 1, true);
                        changed = true;
                        i = j;
                    }
                    break;
                }
                // For asymmetric brackets, let an inner opening bracket resolve first.
                if (open != close && c == open) {
                    break;
                }
            }
        }
        return changed;
    }

    private static PieceBuilder replaceTildes(String sub, int start, int end, int from) {
        PieceBuilder buf = new PieceBuilder(end - start + 8);
        boolean hasWordBefore = false;

        for (int i = start; i < end; i++) {
            char c = sub.charAt(i);
            if (!isTilde(c)) {
                buf.append(c, from + i);
                if (Character.isLetterOrDigit(c)) {
                    hasWordBefore = true;
                }
                continue;
            }

            int tildeOrigIdx = from + i;
            int j = i + 1;
            while (j < end && isTilde(sub.charAt(j))) {
                j++;
            }

            int nextNonSpace = j;
            while (nextNonSpace < end && isSpace(sub.charAt(nextNonSpace))) {
                nextNonSpace++;
            }

            int spaceStart = buf.trailingSpaceStart();
            int prevNonSpaceIdx = spaceStart - 1;
            boolean hasDigitBefore = prevNonSpaceIdx >= 0 && Character.isDigit(buf.chars[prevNonSpaceIdx]);
            boolean hasDigitAfter = nextNonSpace < end && Character.isDigit(sub.charAt(nextNonSpace));
            boolean directlyBeforeDigit = j < end && Character.isDigit(sub.charAt(j));

            if (hasDigitBefore && hasDigitAfter) {
                // Between two numbers: "<n1> đến <n2>".
                if (spaceStart < buf.size) {
                    buf.size = spaceStart + 1;
                } else {
                    buf.append(' ', tildeOrigIdx);
                }
                buf.appendAll("đến", tildeOrigIdx);
                if (j < nextNonSpace) {
                    buf.append(sub.charAt(j), from + j);
                    i = nextNonSpace - 1;
                } else {
                    buf.append(' ', tildeOrigIdx);
                    i = j - 1;
                }
            } else if (directlyBeforeDigit) {
                // Directly before a number: "khoảng <n>".
                if (spaceStart < buf.size) {
                    buf.size = spaceStart + 1;
                } else if (buf.size > 0 && Character.isLetterOrDigit(buf.chars[buf.size - 1])) {
                    buf.append(' ', tildeOrigIdx);
                }
                buf.appendAll("khoảng ", tildeOrigIdx);
                i = j - 1;
            } else if (hasWordBefore
                    && nextNonSpace < end
                    && PAUSE_OR_CLOSING_PUNCT.indexOf(sub.charAt(nextNonSpace)) < 0
                    && hasLetterOrDigit(sub, nextNonSpace, end)) {
                // Otherwise with more words following in the same piece: replace with ", ".
                buf.size = spaceStart;
                buf.appendAll(", ", tildeOrigIdx);
                i = nextNonSpace - 1;
            } else {
                // At the end of a piece (or no following words / already followed by pause punctuation): drop it.
                if ((buf.size == 0 || spaceStart < buf.size) && j < nextNonSpace) {
                    i = nextNonSpace - 1;
                } else {
                    i = j - 1;
                }
            }
        }
        return buf;
    }

    private static void appendIfSpeakable(PieceBuilder buf, List<String> pieces, List<int[]> starts) {
        int trimStart = 0;
        int trimEnd = buf.size;

        boolean hasContent = false;
        for (int k = trimStart; k < trimEnd; k++) {
            if (Character.isLetterOrDigit(buf.chars[k])) {
                hasContent = true;
                break;
            }
        }
        if (!hasContent) {
            return;
        }

        // Rule E: strip unbalanced quotes/brackets at piece edges and re-trim spaces.
        while (trimStart < trimEnd) {
            while (trimStart < trimEnd && isSpace(buf.chars[trimStart])) {
                trimStart++;
            }
            while (trimEnd > trimStart && isSpace(buf.chars[trimEnd - 1])) {
                trimEnd--;
            }
            if (trimStart >= trimEnd) {
                break;
            }
            if (shouldStripEdgeQuote(buf.chars, trimStart, trimEnd, true)) {
                trimStart++;
                continue;
            }
            if (shouldStripEdgeQuote(buf.chars, trimStart, trimEnd, false)) {
                trimEnd--;
                continue;
            }
            break;
        }

        if (trimStart >= trimEnd) {
            return;
        }
        pieces.add(new String(buf.chars, trimStart, trimEnd - trimStart));
        starts.add(Arrays.copyOfRange(buf.starts, trimStart, trimEnd));
    }

    private static boolean shouldStripEdgeQuote(char[] chars, int start, int end, boolean leading) {
        char c = leading ? chars[start] : chars[end - 1];
        int openIdx = OPEN_WRAPPERS.indexOf(c);
        int closeIdx = CLOSE_WRAPPERS.indexOf(c);
        if (openIdx < 0 && closeIdx < 0) {
            return false;
        }
        if (openIdx >= 0 && openIdx == closeIdx) {
            return (countQuotes(chars, c, start, end) & 1) == 1;
        }
        if (leading) {
            return openIdx < 0 || !hasMatchingClose(chars, c, CLOSE_WRAPPERS.charAt(openIdx), start, end);
        }
        return closeIdx < 0 || !hasMatchingOpen(chars, OPEN_WRAPPERS.charAt(closeIdx), c, start, end);
    }

    private static int countQuotes(char[] chars, char quote, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (chars[i] == quote && !isWordApostrophe(chars, i, start, end)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasMatchingClose(char[] chars, char open, char close, int start, int end) {
        int depth = 0;
        for (int i = start; i < end; i++) {
            if (isWordApostrophe(chars, i, start, end)) {
                continue;
            }
            if (chars[i] == open) {
                depth++;
            } else if (chars[i] == close) {
                depth--;
                if (depth == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMatchingOpen(char[] chars, char open, char close, int start, int end) {
        int depth = 0;
        for (int i = end - 1; i >= start; i--) {
            if (isWordApostrophe(chars, i, start, end)) {
                continue;
            }
            if (chars[i] == close) {
                depth++;
            } else if (chars[i] == open) {
                depth--;
                if (depth == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Treats ' and ’ flanked by letters or digits as word-internal apostrophes rather than quotes. */
    private static boolean isWordApostrophe(char[] chars, int idx, int start, int end) {
        char c = chars[idx];
        if (c != '\'' && c != '\u2019') {
            return false;
        }
        return idx > start && idx + 1 < end
                && Character.isLetterOrDigit(chars[idx - 1])
                && Character.isLetterOrDigit(chars[idx + 1]);
    }

    private static boolean hasLetterOrDigit(String s, int from, int to) {
        for (int k = from; k < to; k++) {
            if (Character.isLetterOrDigit(s.charAt(k))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTilde(char c) {
        return c == '~' || c == '\uFF5E';
    }

    private static boolean isSpace(char c) {
        return c == '\u00A0' || Character.isWhitespace(c);
    }

    /** Growable parallel arrays of characters and their original string indices. */
    private static final class PieceBuilder {
        char[] chars;
        int[] starts;
        int size;

        PieceBuilder(int initialCapacity) {
            int cap = Math.max(16, initialCapacity);
            this.chars = new char[cap];
            this.starts = new int[cap];
        }

        void append(char ch, int origIndex) {
            if (size == chars.length) {
                chars = Arrays.copyOf(chars, chars.length * 2);
                starts = Arrays.copyOf(starts, starts.length * 2);
            }
            chars[size] = ch;
            starts[size] = origIndex;
            size++;
        }

        void appendAll(String text, int origIndex) {
            for (int i = 0; i < text.length(); i++) {
                append(text.charAt(i), origIndex);
            }
        }

        int trailingSpaceStart() {
            int idx = size;
            while (idx > 0 && isSpace(chars[idx - 1])) {
                idx--;
            }
            return idx;
        }
    }
}
