package io.github.chrislee0.sheetweave;

import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

/** Workbook-local style cache. Never mutates a style after assigning it to a cell. */
final class PoiStyles {
    final XSSFWorkbook workbook;
    final Map<Map<String, String>, XSSFCellStyle> cache = new HashMap<Map<String, String>, XSSFCellStyle>();
    PoiStyles(XSSFWorkbook workbook) { this.workbook = workbook; }
    XSSFCellStyle get(Map<String, String> values) {
        XSSFCellStyle found = cache.get(values);
        if (found != null) return found;
        if (cache.size() >= 10000) throw new SheetWeaveException("More than 10000 distinct styles; simplify stylesheet");
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        try {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String value = entry.getValue();
                switch (entry.getKey()) {
                    case "font-family": font.setFontName(value); break;
                    case "font-size":
                        if (!value.matches("[0-9]+pt")) throw new IllegalArgumentException("Use integer pt units");
                        int size = Integer.parseInt(value.substring(0, value.length() - 2));
                        if (size < 1 || size > 409) throw new IllegalArgumentException("Font size must be 1..409pt");
                        font.setFontHeightInPoints((short) size); break;
                    case "font-weight": require(value, "normal", "bold"); font.setBold(value.equals("bold")); break;
                    case "font-style": require(value, "normal", "italic"); font.setItalic(value.equals("italic")); break;
                    case "color": font.setColor(color(value)); break;
                    case "background-color": style.setFillForegroundColor(color(value)); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); break;
                    case "text-align": require(value, "left", "center", "right"); style.setAlignment(HorizontalAlignment.valueOf(value.toUpperCase(Locale.ROOT))); break;
                    case "vertical-align":
                        require(value, "top", "middle", "bottom");
                        style.setVerticalAlignment(value.equals("middle") ? VerticalAlignment.CENTER : VerticalAlignment.valueOf(value.toUpperCase(Locale.ROOT))); break;
                    case "-excel-number-format": style.setDataFormat(workbook.createDataFormat().getFormat(value)); break;
                    case "-excel-wrap-text": require(value, "true", "false"); style.setWrapText(Boolean.parseBoolean(value)); break;
                    default: throw new IllegalArgumentException("Unsupported property");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new SheetWeaveException("Invalid style " + values + ": " + ex.getMessage(), ex);
        }
        style.setFont(font);
        cache.put(Collections.unmodifiableMap(new TreeMap<String, String>(values)), style);
        return style;
    }
    private static void require(String value, String... allowed) {
        if (!Arrays.asList(allowed).contains(value)) throw new IllegalArgumentException("Unsupported value: " + value);
    }
    private static XSSFColor color(String value) {
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must be #RRGGBB: " + value);
        return new XSSFColor(new byte[]{(byte) Integer.parseInt(value.substring(1, 3), 16),
            (byte) Integer.parseInt(value.substring(3, 5), 16), (byte) Integer.parseInt(value.substring(5, 7), 16)}, new DefaultIndexedColorMap());
    }
}
