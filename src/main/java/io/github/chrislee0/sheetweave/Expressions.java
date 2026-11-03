package io.github.chrislee0.sheetweave;

import java.beans.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Restricted expressions: literals, property paths and one comparison or negation. No method calls. */
final class Expressions {
    private static final Pattern COMPARE = Pattern.compile("^([a-zA-Z_][\\w.]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    static Object value(String expression, Map<String, Object> model) {
        String text = expression.trim();
        if (text.equals("null")) return null;
        if (text.equals("true") || text.equals("false")) return Boolean.valueOf(text);
        if (text.matches("-?\\d+(\\.\\d+)?")) return new BigDecimal(text);
        if (text.length() >= 2 && ((text.startsWith("'") && text.endsWith("'")) ||
            (text.startsWith("\"") && text.endsWith("\"")))) return text.substring(1, text.length() - 1);
        if (!text.matches("[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*"))
            throw new SheetWeaveException("Unsupported expression: " + expression);
        Object current = model;
        for (String part : text.split("\\.")) {
            if (part.equals("class") || part.equals("classLoader")) throw new SheetWeaveException("Forbidden property: " + part);
            if (current == null) throw new SheetWeaveException("Null intermediate property in: " + expression);
            if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;
                if (!map.containsKey(part)) throw new SheetWeaveException("Unknown property: " + expression);
                current = map.get(part);
            } else {
                try {
                    Method getter = null;
                    for (PropertyDescriptor pd : Introspector.getBeanInfo(current.getClass()).getPropertyDescriptors())
                        if (pd.getName().equals(part)) getter = pd.getReadMethod();
                    if (getter == null) throw new SheetWeaveException("Unknown property: " + expression);
                    current = getter.invoke(current);
                } catch (IntrospectionException | IllegalAccessException | InvocationTargetException ex) {
                    throw new SheetWeaveException("Cannot read: " + expression, ex);
                }
            }
        }
        return current;
    }
    static boolean test(String expression, Map<String, Object> model) {
        String text = expression.trim();
        if (text.startsWith("!")) return !test(text.substring(1), model);
        Matcher match = COMPARE.matcher(text);
        if (!match.matches()) {
            Object result = value(text, model);
            if (!(result instanceof Boolean)) throw new SheetWeaveException("Condition must be boolean: " + text);
            return (Boolean) result;
        }
        Object left = value(match.group(1), model), right = value(match.group(3), model);
        String op = match.group(2);
        boolean numbers = left instanceof Number && right instanceof Number;
        boolean equal = numbers ? decimal(left).compareTo(decimal(right)) == 0 : Objects.equals(left, right);
        if (op.equals("==")) return equal;
        if (op.equals("!=")) return !equal;
        if (!numbers) throw new SheetWeaveException("Ordered comparison requires two numbers: " + text);
        int comparison = decimal(left).compareTo(decimal(right));
        if (op.equals(">")) return comparison > 0;
        if (op.equals("<")) return comparison < 0;
        if (op.equals(">=")) return comparison >= 0;
        return comparison <= 0;
    }
    private static BigDecimal decimal(Object value) { return new BigDecimal(value.toString()); }
}
