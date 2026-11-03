package io.github.chrislee0.sheetweave;

import java.util.*;
import java.util.regex.*;

/** Immutable compiled subset of SCSS: nesting, descendant selectors, lists and leading &. */
public final class Stylesheet {
    private static final Set<String> PROPERTIES = new HashSet<String>(Arrays.asList(
        "font-family", "font-size", "font-weight", "font-style", "color", "background-color",
        "text-align", "vertical-align", "-excel-number-format", "-excel-wrap-text"));
    private static final Set<String> INHERITED = new HashSet<String>(Arrays.asList(
        "font-family", "font-size", "font-weight", "font-style", "color", "text-align"));
    private static final Pattern SIMPLE = Pattern.compile("(?:[a-z][a-z0-9-]*|\\*)?(?:[.#][a-zA-Z_][a-zA-Z0-9_-]*)*");
    private final List<Rule> rules;
    private Stylesheet(List<Rule> rules) { this.rules = Collections.unmodifiableList(rules); }

    public static Stylesheet parse(String source) {
        Parser parser = new Parser(Objects.requireNonNull(source, "source"));
        parser.block(Collections.<String>emptyList(), false);
        return new Stylesheet(parser.rules);
    }

    Map<String, String> resolve(StyleNode node) {
        Map<String, String> result = new TreeMap<String, String>();
        if (node.parent != null) {
            Map<String, String> parent = resolve(node.parent);
            for (String key : INHERITED) if (parent.containsKey(key)) result.put(key, parent.get(key));
        }
        List<Rule> matched = new ArrayList<Rule>();
        for (Rule rule : rules) if (rule.matches(node)) matched.add(rule);
        Collections.sort(matched, (a, b) -> {
            int c = Integer.compare(a.ids, b.ids);
            if (c == 0) c = Integer.compare(a.classes, b.classes);
            if (c == 0) c = Integer.compare(a.tags, b.tags);
            if (c == 0) c = Integer.compare(a.order, b.order);
            return c;
        });
        for (Rule rule : matched) result.put(rule.property, rule.value);
        return result;
    }

    private static final class Rule {
        final String[] parts;
        final String property, value;
        final int order;
        int ids, classes, tags;
        Rule(String selector, String property, String value, int order) {
            this.parts = selector.trim().split("\\s+");
            for (String part : parts) {
                if (part.isEmpty() || !SIMPLE.matcher(part).matches())
                    throw new SheetWeaveException("Unsupported selector: " + selector);
                for (char ch : part.toCharArray()) { if (ch == '#') ids++; if (ch == '.') classes++; }
                if (Character.isLetter(part.charAt(0))) tags++;
            }
            this.property = property; this.value = value; this.order = order;
        }
        boolean matches(StyleNode node) {
            if (!simple(parts[parts.length - 1], node)) return false;
            StyleNode ancestor = node.parent;
            for (int i = parts.length - 2; i >= 0; i--) {
                while (ancestor != null && !simple(parts[i], ancestor)) ancestor = ancestor.parent;
                if (ancestor == null) return false;
                ancestor = ancestor.parent;
            }
            return true;
        }
        boolean simple(String selector, StyleNode node) {
            Matcher matcher = Pattern.compile("(^[a-z][a-z0-9-]*|^\\*|[.#][a-zA-Z_][a-zA-Z0-9_-]*)").matcher(selector);
            while (matcher.find()) {
                String token = matcher.group();
                if (token.charAt(0) == '.' && !node.classes.contains(token.substring(1))) return false;
                if (token.charAt(0) == '#' && !node.id.equals(token.substring(1))) return false;
                if (Character.isLetter(token.charAt(0)) && !node.tag.equals(token)) return false;
            }
            return true;
        }
    }

    private static final class Parser {
        final String source;
        final List<Rule> rules = new ArrayList<Rule>();
        int pos;
        Parser(String source) { this.source = source; }
        void block(List<String> parents, boolean nested) {
            while (true) {
                skip();
                if (pos == source.length()) {
                    if (nested) fail("Missing closing brace");
                    return;
                }
                if (source.charAt(pos) == '}') {
                    if (!nested) fail("Unexpected closing brace");
                    pos++; return;
                }
                String token = token();
                if (pos == source.length()) fail("Expected ';' or '{'");
                char end = source.charAt(pos++);
                if (end == '{') {
                    List<String> selectors = new ArrayList<String>();
                    for (String child : token.split(",", -1)) {
                        child = child.trim();
                        if (child.isEmpty()) fail("Empty selector");
                        if (parents.isEmpty()) {
                            if (child.contains("&")) fail("& requires a parent selector");
                            selectors.add(child);
                        } else for (String parent : parents) {
                            if (child.startsWith("&") && child.indexOf('&', 1) < 0)
                                selectors.add(parent + child.substring(1));
                            else if (child.contains("&")) fail("Only one leading & is supported");
                            else selectors.add(parent + " " + child);
                        }
                    }
                    block(selectors, true);
                } else {
                    if (parents.isEmpty()) fail("Declaration outside a selector");
                    int colon = token.indexOf(':');
                    if (colon <= 0) fail("Expected property: value");
                    String property = token.substring(0, colon).trim();
                    String value = token.substring(colon + 1).trim();
                    if (!PROPERTIES.contains(property)) fail("Unsupported property: " + property);
                    if (value.isEmpty() || value.contains("!important")) fail("Empty value or unsupported !important");
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) value = value.substring(1, value.length() - 1);
                    for (String parent : parents) rules.add(new Rule(parent, property, value, rules.size()));
                    if (end == '}') return;
                }
            }
        }
        String token() {
            StringBuilder out = new StringBuilder();
            char quote = 0;
            while (pos < source.length()) {
                char ch = source.charAt(pos);
                if (quote == 0 && source.startsWith("/*", pos)) { comment(); out.append(' '); continue; }
                if (quote == 0 && (ch == '{' || ch == ';' || ch == '}')) break;
                pos++; out.append(ch);
                if (ch == '\\' && quote != 0 && pos < source.length()) out.append(source.charAt(pos++));
                else if (ch == quote) quote = 0;
                else if (quote == 0 && (ch == '\'' || ch == '"')) quote = ch;
            }
            if (quote != 0) fail("Unterminated string");
            return out.toString().trim();
        }
        void skip() {
            while (pos < source.length()) {
                if (Character.isWhitespace(source.charAt(pos))) pos++;
                else if (source.startsWith("/*", pos)) comment();
                else break;
            }
        }
        void comment() {
            int end = source.indexOf("*/", pos + 2);
            if (end < 0) fail("Unterminated comment");
            pos = end + 2;
        }
        void fail(String message) {
            int line = 1;
            for (int i = 0; i < pos; i++) if (source.charAt(i) == '\n') line++;
            throw new SheetWeaveException(message + " at stylesheet line " + line);
        }
    }
}
