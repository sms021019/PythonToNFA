package com.redos;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

import dk.brics.automaton.RegExp;

class RegexHelper {

    private Logger logger;

    public RegexHelper() {

    }

    public RegexHelper(Logger logger) {
        this.logger = logger;
    }

    // private static final Pattern comment = Pattern.compile("(.*?)(\\s*//.*)?$");
    // private static final Pattern bounds = Pattern.compile("\\{(\\d*),(\\d+)}");
    private static final Pattern boundaries = Pattern.compile(
            "((?<![\\\\])\\$)|((?<!([\\\\]|[\\[]))\\^)|((?<![\\\\])\\\\b)|((?<![\\\\])\\\\B)|((?<![\\\\])\\\\A)|((?<![\\\\])\\\\G)|((?<![\\\\])\\\\Z)|((?<![\\\\])\\\\z)");
    private static final Pattern javaCharClasses = Pattern.compile(
            "((?<![\\\\])\\\\p\\{javaLowerCase\\})|((?<![\\\\])\\\\p\\{javaUpperCase\\})|((?<![\\\\])\\\\p\\{javaWhitespace\\})|((?<![\\\\])\\\\p\\{javaMirrored\\})");
    private static final Pattern unicodeCharClasses = Pattern.compile(
            "((?<![\\\\])\\\\p\\{IsLatin\\})|((?<![\\\\])\\\\p\\{InGreek\\})|((?<![\\\\])\\\\p\\{Lu\\})|((?<![\\\\])\\\\p\\{IsAlphabetic\\})|((?<![\\\\])\\\\p\\{Sc\\})|((?<![\\\\])\\\\p\\{InGreek\\})");
    private static final Pattern unknownCharClasses = Pattern.compile("(?<![\\\\])\\\\p\\{\\w+\\}");
    private static final Pattern lookAround = Pattern.compile("(\\(\\?=)|(\\(\\?!)|(\\(\\?<=)|(\\(\\?<!)|(\\(\\?>)");
    private static final Pattern possessiveQuantifier = Pattern
            .compile("(\\?\\+)|(\\*\\+)|(\\+\\+)|(\\{\\d+(,|,\\d+)?}\\+)");
    private static final Pattern backReferences = Pattern.compile("(?<![\\\\])(\\\\\\d+|\\\\k<\\p{Alpha}\\p{Alnum}*>)");
    private static final Pattern quotation = Pattern.compile("(?<![\\\\])(\\\\Q|\\\\E)");
    private static final Pattern flags = Pattern.compile("(\\(\\?[idmsuxU]+\\))");
    private static final Pattern classOpen = Pattern.compile("(?<![\\\\])\\[");

    public RegExp parse(String regex) {
        // String originalRegex = regex;
        logger.log(Logger.DEBUG, "Regex (original): " + wrapInQuotes(regex));
        regex = regex.replaceAll("\\\\u(\\d\\d\\d\\d)", "\\u$1");
        regex = regex.replaceAll("\\\\x(\\d\\d)", "\\u00$1");
        regex = StringEscapeUtils.unescapeJava(regex);
        if (regex.startsWith("^")) {
            regex = regex.substring(1);
        }

        if (regex.endsWith("$")) {
            regex = regex.substring(0, regex.length() - 1);
        }

        // String unescapedRegex = regex;
        logger.log(Logger.DEBUG, "Regex (before desugaring): " + wrapInQuotes(regex));
        regex = desugarJavaRegex(regex);
        logger.log(Logger.DEBUG, "Regex (after desugaring): " + wrapInQuotes(regex));
        // if (abstractMaxRepetitions) {
        // regex = abstractMaxRepetitions(regex, repetitions);
        // }

        // log(1, "Regex (abstracted): " + wrapInQuotes(regex));
        RegExp regExp = new RegExp(regex, 0);
        logger.log(Logger.DEBUG, "Regex (parsed): " + regExp);
        return regExp;
    }

    public static String wrapInQuotes(String s) {
        if (s == null) {
            throw new IllegalArgumentException();
        } else {
            return "\"" + s + "\"";
        }
    }

    private String desugarJavaRegex(String s) {
        String original = s;
        if (boundaries.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (boundary matchers) in " + wrapInQuotes(s));
        } else if (javaCharClasses.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (java.lang.Character classes) in " + wrapInQuotes(s));
        } else if (unicodeCharClasses.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (classes for Unicode scripts, blocks, categories and binary properties) in "
                            + wrapInQuotes(s));
        } else if (lookAround.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (lookahead, lookbehind and independent, non-capturing groups) in "
                            + wrapInQuotes(s));
        } else if (possessiveQuantifier.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (possessive quantifiers) in " + wrapInQuotes(s));
        } else if (backReferences.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (back references) in " + wrapInQuotes(s));
        } else if (quotation.matcher(s).find()) {
            throw new IllegalArgumentException("unsupported Java regex feature (quotation) in " + wrapInQuotes(s));
        } else if (flags.matcher(s).find()) {
            throw new IllegalArgumentException(
                    "unsupported Java regex feature (groups with flags) in " + wrapInQuotes(s));
        } else {
            s = s.replace("\\p{Lower}", "[a-z]");
            s = s.replace("\\p{Upper}", "[A-Z]");
            s = s.replace("\\p{ASCII}", "[\\x00-\\x7F]");
            s = s.replace("\\p{Alpha}", "[a-zA-Z]");
            s = s.replace("\\p{Digit}", "[0-9]");
            s = s.replace("\\p{Alnum}", "[a-zA-Z0-9]");
            s = s.replace("\\p{Punct}", "[!\"#$%&'()*+,-./:;<=>?@\\^_`{|}~]");
            s = s.replace("\\p{Graph}", "[a-zA-Z0-9!\"#$%&'()*+,-./:;<=>?@\\^_`{|}~]");
            s = s.replace("\\p{Print}", "[a-zA-Z0-9!\"#$%&'()*+,-./:;<=>?@\\^_`{|}~\\x20]");
            s = s.replace("\\p{Blank}", "[ \\t]");
            s = s.replace("\\p{Cntrl}", "[\\x00-\\x1F\\x7F]");
            s = s.replace("\\p{XDigit}", "[0-9a-fA-F]");
            s = s.replace("\\p{Space}", "[ \\t\\n\\x0B\\f\\r]");
            if (unknownCharClasses.matcher(s).find()) {
                throw new IllegalArgumentException(
                        "unsupported Java regex feature (unknown character classes) in " + wrapInQuotes(original));
            } else {
                Stack<Character> stack = new Stack<>();
                boolean negation = false;
                StringBuilder newS = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    if ((i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("[^"))) {
                        negation = true;
                        stack.add('{');
                    } else if (s.charAt(i) == '[') {
                        stack.add('[');
                    } else if (s.charAt(i) == ']' && !stack.empty()) {
                        if (stack.peek() == '{') {
                            negation = false;
                        }
                        stack.pop();
                    }

                    // System.out.println("" + s.charAt(i) + s.charAt(i + 1));
                    if (i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("\\s")) {
                        if (negation) {
                            newS.append(" \\t\\n\\f\\r");
                        } else {
                            newS.append("[ \\t\\n\\f\\r]");
                        }
                        i += 1;
                    } else if (i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("\\d")) {
                        if (negation) {
                            newS.append("0-9");
                        } else {
                            newS.append("[0-9]");
                        }
                        i += 1;
                    } else if (i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("\\w")) {
                        if (negation) {
                            newS.append("a-zA-Z_0-9");
                        } else {
                            newS.append("[a-zA-Z_0-9]");
                        }
                        i += 1;
                    } else if (i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("\\S")) {
                        if (negation) {
                            throw new IllegalArgumentException(
                                    "unsupported Java regex feature (Double negation character classes) in "
                                            + wrapInQuotes(original));
                        } else {
                            newS.append("[^ \\t\\n\\f\\r]");
                        }
                        i += 1;
                    } else if (i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("\\D")) {
                        if (negation) {
                            throw new IllegalArgumentException(
                                    "unsupported Java regex feature (Double negation character classes) in "
                                            + wrapInQuotes(original));
                        } else {
                            newS.append("[^0-9]");
                        }
                        i += 1;
                    } else if (i < s.length() - 1 && ("" + s.charAt(i) + s.charAt(i + 1)).equals("\\W")) {
                        if (negation) {
                            throw new IllegalArgumentException(
                                    "unsupported Java regex feature (Double negation character classes) in "
                                            + wrapInQuotes(original));
                        } else {
                            newS.append("[^a-zA-Z_0-9]");
                        }
                        i += 1;
                    } else {
                        newS.append(s.charAt(i));
                    }

                }
                // System.out.println("New String: " + newS.toString());
                s = newS.toString();
                s = s.replace("\\x0A", "\n")
                        .replace("\\x0D", "\r")
                        .replace("\\x09", "\t")
                        .replace("\\x08", "\b")
                        .replace("\\x0C", "\f")
                        .replace("\\x5C", "\\")
                        .replace("\\x27", "'")
                        .replace("\\x22", "\"")
                        .replace("\\x26", "&")
                        .replace("\\x3C", "<")
                        .replace("\\x3E", ">")
                        .replace("\\x7C", "|")
                        .replace("\\x5E", "^")
                        .replace("\\x60", "`")
                        .replace("\\x7E", "~");
                s = s.replace("\\x0a", "\n")
                        .replace("\\x0d", "\r")
                        .replace("\\x09", "\t")
                        .replace("\\x08", "\b")
                        .replace("\\x0c", "\f")
                        .replace("\\x5c", "\\")
                        .replace("\\x27", "'")
                        .replace("\\x22", "\"")
                        .replace("\\x26", "&")
                        .replace("\\x3c", "<")
                        .replace("\\x3e", ">")
                        .replace("\\x7c", "|")
                        .replace("\\x5e", "^")
                        .replace("\\x60", "`")
                        .replace("\\x7e", "~");
                // System.out.println("New String 2: " + s);
                // s = s.replace("\\d", "[0-9]");
                // s = s.replace("\\D", "[^0-9]");
                // s = s.replace("\\s", "[ \\t\\n\\f\\r]");
                // s = s.replace("\\S", "[^ \\t\\n\\f\\r]");
                // s = s.replace("\\w", "[a-zA-Z_0-9]");
                // s = s.replace("\\W", "[^a-zA-Z_0-9]");
                s = s.replaceAll("(?<![\\\\])\"", "[\"]");
                s = desugarNestedCharacterClasses(s, original);
                s = s.replaceAll("\\(\\?<\\p{Alpha}\\p{Alnum}*>", "(");
                s = s.replace("(?:", "(");
                s = s.replaceAll("((\\?)|(\\*)|(\\+)|(\\{\\d+(,|,\\d+)?}))(\\?)", "$1");
                s = s.replace("\\t", "\t");
                s = s.replace("\\n", Character.toString('\n'));
                s = s.replace("\\r", Character.toString('\r'));
                s = s.replace("\\f", "\f");
                s = s.replace("\\a", "\u0007");
                s = s.replace("\\e", "\u001b");
                return s;
            }
        }
    }

    private String desugarNestedCharacterClasses(String s, String original) {
        int pos = 0;
        StringBuilder sb = new StringBuilder();

        while (true) {
            Matcher openMatcher = classOpen.matcher(s);
            if (!openMatcher.find(pos)) {
                sb.append(s.substring(pos));
                return sb.toString();
            }

            int openPos = openMatcher.end();
            sb.append(s.substring(pos, openMatcher.start()));
            int closePos = openPos;
            int counter = 1;
            Stack<StringBuilder> stack = new Stack<>();
            stack.push(new StringBuilder());
            ((StringBuilder) stack.peek()).append("[");
            ArrayList<String> groups = new ArrayList<>();

            while (true) {
                while (0 < counter && closePos < s.length()) {
                    char c = s.charAt(closePos);
                    ++closePos;
                    int slashes = countConsecutiveSlashesBackward(s, closePos - 2);
                    if (c == '[' && slashes % 2 == 0) {
                        ++counter;
                        stack.push(new StringBuilder());
                        ((StringBuilder) stack.peek()).append("[");
                    } else if (c == ']' && slashes % 2 == 0) {
                        --counter;
                        ((StringBuilder) stack.peek()).append("]");
                        String g = ((StringBuilder) stack.pop()).toString();
                        if (!g.isEmpty() && !g.equals("[]") && !g.equals("[^]")) {
                            groups.add(g);
                        }
                    } else {
                        if (c == '&' && slashes % 2 == 0 && closePos + 1 < s.length()
                                && s.charAt(closePos + 1) == '&') {
                            throw new IllegalArgumentException(
                                    "unsupported Java regex feature (character classes intersection) in "
                                            + wrapInQuotes(original));
                        }

                        ((StringBuilder) stack.peek()).append(c);
                    }
                }

                if (!groups.isEmpty()) {
                    sb.append(groups.size() == 1 ? (String) groups.get(0) : "(" + String.join("|", groups) + ")");
                }

                LinkedList<String> others = new LinkedList<>();

                while (!stack.isEmpty()) {
                    StringBuilder o = (StringBuilder) stack.pop();
                    others.addFirst(o.toString());
                }

                sb.append(String.join("", others));
                pos = closePos;
                break;
            }
        }
    }

    private int countConsecutiveSlashesBackward(String s, int start) {
        int slashes = 0;

        for (int idx = start; 0 <= idx && s.charAt(idx) == '\\'; --idx) {
            ++slashes;
        }

        return slashes;
    }

}