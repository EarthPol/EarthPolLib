/**
 * Post-processes serialized YAML text to improve readability.
 *
 * <p>Normalizes blank lines around lists, comments, and mappings to produce
 * consistent, human-friendly formatting.</p>
 */
package com.earthpol.earthpollib.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

final class YMLPostProcessor {
    private YMLPostProcessor() {}

    static String formatRootEntries(String yamlText) {
        return formatRootEntries(yamlText, Set.of(), Set.of());
    }

    static String formatRootEntries(String yamlText,
                                    Set<String> quoteScalarPaths,
                                    Set<String> quoteListPaths) {
        if (yamlText == null || yamlText.isEmpty()) return yamlText;

        Set<String> scalarPaths = (quoteScalarPaths == null) ? Set.of() : quoteScalarPaths;
        Set<String> listPaths = (quoteListPaths == null) ? Set.of() : quoteListPaths;

        String[] lines = yamlText.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder(yamlText.length() + lines.length * 2);

        boolean lastOutWasBlank = false;

        boolean pendingBlankAfterList = false;
        int listIndent = -1;

        boolean lastEmittedWasComment = false;
        int lastCommentIndent = 0;

        Deque<PathEntry> pathStack = new ArrayDeque<>();
        String currentListPath = null;
        int currentListIndent = -1;

        final int n = lines.length;
        int i = 0;
        while (i < n) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean isBlank = trimmed.isEmpty();
            boolean isComment = trimmed.startsWith("#") || trimmed.startsWith("---");

            if (!isBlank && !isComment) {
                int indentNow = leadingIndent(line);
                boolean isListItemNow = isListItem(line, indentNow);
                if (currentListPath != null && !isListItemNow && indentNow <= currentListIndent) {
                    currentListPath = null;
                    currentListIndent = -1;
                }
            }

            if (pendingBlankAfterList && !isBlank && !isComment) {
                int indentNow = leadingIndent(line);
                boolean isListItemNow = isListItem(line, indentNow);
                if (indentNow <= listIndent && !isListItemNow) {
                    if (!lastOutWasBlank) {
                        out.append('\n');
                        lastOutWasBlank = true;
                    }
                    pendingBlankAfterList = false;
                }
            }

            if (isBlank) {
                if (lastEmittedWasComment) {
                    int k = nextNonBlankIndex(lines, i + 1);
                    if (k != -1) {
                        String next = lines[k];
                        int nextIndent = leadingIndent(next);
                        if (isMappingKeyAtIndent(next, nextIndent, lastCommentIndent)) {
                            i++;
                            continue;
                        }
                    }
                }
                if (!lastOutWasBlank) {
                    out.append('\n');
                    lastOutWasBlank = true;
                }
                i++;
                continue;
            }

            if (isComment) {
                int indentNow = leadingIndent(line);
                if (pendingBlankAfterList && indentNow <= listIndent) {
                    if (!lastOutWasBlank) {
                        out.append('\n');
                        lastOutWasBlank = true;
                    }
                    pendingBlankAfterList = false;
                }

                out.append(line).append('\n');
                lastOutWasBlank = false;
                lastEmittedWasComment = true;
                lastCommentIndent = indentNow;
                i++;
                continue;
            }

            lastEmittedWasComment = false;

            int indent = leadingIndent(line);

            if (isListItem(line, indent)) {
                if (currentListPath != null && listPaths.contains(currentListPath)) {
                    line = quoteListItem(line, indent);
                }
                out.append(line).append('\n');
                lastOutWasBlank = false;

                int k = nextNonBlankIndex(lines, i + 1);
                if (k == -1) {
                    pendingBlankAfterList = true;
                    listIndent = indent;
                } else {
                    String next = lines[k];
                    int nextIndent = leadingIndent(next);
                    String nextTrim = next.trim();
                    boolean continuesList = (nextIndent >= indent) && nextTrim.startsWith("-");
                    if (!continuesList) {
                        pendingBlankAfterList = true;
                        listIndent = indent;
                    }
                }

                i++;
                continue;
            }

            int colonAt = indexOfColon(line, indent);
            if (colonAt > indent) {
                String key = line.substring(indent, colonAt).trim();
                while (!pathStack.isEmpty() && indent <= pathStack.peek().indent) {
                    pathStack.pop();
                }
                String path = buildPath(pathStack, key);

                String afterColon = line.substring(colonAt + 1);
                boolean hasInlineValue = !afterColon.trim().isEmpty();

                if (hasInlineValue) {
                    if (scalarPaths.contains(path)) {
                        line = quoteInlineValue(line, colonAt);
                    }
                    out.append(line).append('\n');
                    lastOutWasBlank = false;

                    int blanks = countBlankRun(lines, i + 1);
                    i += blanks;

                    if (!lastOutWasBlank) {
                        out.append('\n');
                        lastOutWasBlank = true;
                    }
                    i++;
                    continue;
                } else {
                    int j = i + 1;
                    int blanks = 0;
                    while (j < n && lines[j].trim().isEmpty()) { blanks++; j++; }

                    boolean startsList = false;
                    if (j < n) {
                        int nextIndent = leadingIndent(lines[j]);
                        String nextTrim = lines[j].trim();
                        startsList = (nextIndent >= indent) && nextTrim.startsWith("-");
                    }

                    if (startsList) {
                        currentListPath = path;
                        currentListIndent = indent;
                    }

                    pathStack.push(new PathEntry(key, indent));

                    out.append(line).append('\n');
                    lastOutWasBlank = false;

                    if (startsList) {
                        i += blanks;
                        i++;
                        continue;
                    } else {
                        int extraBlanks = countBlankRun(lines, i + 1);
                        i += extraBlanks;
                        if (!lastOutWasBlank) {
                            out.append('\n');
                            lastOutWasBlank = true;
                        }
                        i++;
                        continue;
                    }
                }
            }

            out.append(line).append('\n');
            lastOutWasBlank = false;
            i++;
        }

        if (pendingBlankAfterList && !lastOutWasBlank) {
            out.append('\n');
        }

        return out.toString();
    }

    private static int leadingIndent(String line) {
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') break;
            i++;
        }
        return i;
    }

    private static boolean isListItem(String line, int indent) {
        int i = indent;
        return i < line.length()
                && line.charAt(i) == '-'
                && (i + 1 >= line.length() || Character.isWhitespace(line.charAt(i + 1)));
    }

    private static int indexOfColon(String line, int indent) {
        return line.indexOf(':', indent);
    }

    private static int countBlankRun(String[] lines, int from) {
        int c = 0;
        for (int i = from; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) break;
            c++;
        }
        return c;
    }

    private static int nextNonBlankIndex(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) return i;
        }
        return -1;
    }

    private static boolean isMappingKeyAtIndent(String line, int indentNow, int requiredIndent) {
        if (indentNow != requiredIndent) return false;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---")) return false;
        int colonAt = indexOfColon(line, indentNow);
        return colonAt > indentNow;
    }

    private static String buildPath(Deque<PathEntry> stack, String key) {
        if (stack.isEmpty()) return key;
        StringBuilder sb = new StringBuilder();
        for (java.util.Iterator<PathEntry> it = stack.descendingIterator(); it.hasNext(); ) {
            PathEntry e = it.next();
            sb.append(e.key).append('.');
        }
        sb.append(key);
        return sb.toString();
    }

    private static String quoteInlineValue(String line, int colonAt) {
        String afterColon = line.substring(colonAt + 1);
        int commentAt = findCommentStart(afterColon);
        String valuePart = (commentAt == -1) ? afterColon : afterColon.substring(0, commentAt);
        String commentPart = (commentAt == -1) ? "" : afterColon.substring(commentAt);

        int firstNonWs = indexOfNonWhitespace(valuePart);
        if (firstNonWs == -1) return line;
        String leading = valuePart.substring(0, firstNonWs);
        String valueRaw = valuePart.substring(firstNonWs).trim();
        if (valueRaw.isEmpty() || isAlreadyQuoted(valueRaw)) return line;

        String quoted = "\"" + escapeDoubleQuotes(valueRaw) + "\"";
        return line.substring(0, colonAt + 1) + leading + quoted + commentPart;
    }

    private static String quoteListItem(String line, int indent) {
        int i = indent;
        if (i >= line.length() || line.charAt(i) != '-') return line;

        int j = i + 1;
        while (j < line.length() && Character.isWhitespace(line.charAt(j))) j++;
        if (j >= line.length()) return line;

        String afterDash = line.substring(j);
        int commentAt = findCommentStart(afterDash);
        String valuePart = (commentAt == -1) ? afterDash : afterDash.substring(0, commentAt);
        String commentPart = (commentAt == -1) ? "" : afterDash.substring(commentAt);
        String valueRaw = valuePart.trim();

        if (valueRaw.isEmpty() || isAlreadyQuoted(valueRaw)) return line;

        String quoted = "\"" + escapeDoubleQuotes(valueRaw) + "\"";
        String prefix = line.substring(0, j);
        return prefix + quoted + commentPart;
    }

    private static int findCommentStart(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#') {
                if (i == 0 || Character.isWhitespace(s.charAt(i - 1))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indexOfNonWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private static boolean isAlreadyQuoted(String value) {
        return value.startsWith("\"") || value.startsWith("'");
    }

    private static String escapeDoubleQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class PathEntry {
        private final String key;
        private final int indent;

        private PathEntry(String key, int indent) {
            this.key = key;
            this.indent = indent;
        }
    }
}
