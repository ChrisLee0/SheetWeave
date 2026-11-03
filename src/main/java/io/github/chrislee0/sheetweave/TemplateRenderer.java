package io.github.chrislee0.sheetweave;

import java.lang.reflect.Array;
import java.math.*;
import java.time.*;
import java.util.*;
import org.w3c.dom.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

/** In-memory, top-to-bottom renderer. Layout is validated before bytes reach the output stream. */
final class TemplateRenderer {
    private final XSSFWorkbook workbook;
    private final Stylesheet stylesheet;
    private final PoiStyles styles;
    private XSSFSheet sheet;
    private int rowIndex;
    private final Set<Long> occupied = new HashSet<Long>();
    private int tableEnd;
    private static final Map<String, Set<String>> ATTRIBUTES = new HashMap<String, Set<String>>();
    static {
        allow("sw:workbook", "xmlns:sw");
        allow("sw:sheet", "name", "widths", "freeze-rows");
        allow("sw:text", "span", "height", "sw:value", "sw:if");
        allow("sw:spacer", "rows", "sw:if");
        allow("table", "sw:if");
        allow("thead", "sw:if"); allow("tbody", "sw:if"); allow("tfoot", "sw:if");
        allow("tr", "sw:each", "sw:index", "sw:if", "height");
        allow("td", "sw:value", "rowspan", "colspan");
        allow("th", "sw:value", "rowspan", "colspan");
        allow("sw:choose"); allow("sw:when", "test"); allow("sw:otherwise");
        allow("sw:out", "value");
    }
    private static void allow(String tag, String... extra) {
        Set<String> values = new HashSet<String>(Arrays.asList(extra));
        values.add("class"); values.add("id"); ATTRIBUTES.put(tag, values);
    }
    TemplateRenderer(XSSFWorkbook workbook, Stylesheet stylesheet) {
        this.workbook = workbook; this.stylesheet = stylesheet; this.styles = new PoiStyles(workbook);
    }
    void render(Element root, Map<String, Object> model) {
        validate(root);
        require(root.getTagName().equals("sw:workbook"), "Root must be sw:workbook");
        StyleNode rootStyle = node(root, null);
        for (Element element : children(root)) {
            require(element.getTagName().equals("sw:sheet"), "workbook only accepts sw:sheet");
            String name = element.getAttribute("name");
            require(!name.isEmpty(), "Sheet name is required");
            sheet = workbook.createSheet(name); rowIndex = 0;
            if (element.hasAttribute("widths")) {
                String[] widths = element.getAttribute("widths").split(",", -1);
                require(widths.length <= 16384, "Too many columns");
                for (int i = 0; i < widths.length; i++) {
                    double width = Double.parseDouble(widths[i].trim());
                    require(Double.isFinite(width) && width > 0 && width <= 255, "Widths must be > 0 and <= 255 characters");
                    sheet.setColumnWidth(i, (int) Math.round(width * 256));
                }
            }
            StyleNode sheetStyle = node(element, rootStyle);
            for (Element block : children(element)) {
                if (!visible(block, model)) continue;
                switch (block.getTagName()) {
                    case "table": table(block, model, sheetStyle); break;
                    case "sw:text":
                        int span = integer(block, "span", 1, 16384);
                        writeCell(block, model, node(block, sheetStyle), rowIndex, 0, 1, span);
                        height(block, rowIndex); rowIndex++; break;
                    case "sw:spacer": rowIndex += integer(block, "rows", 1, 1048576); break;
                    default: throw error(block, "Unsupported sheet child");
                }
                require(rowIndex <= 1048576, "Excel row limit exceeded");
            }
            if (element.hasAttribute("freeze-rows")) {
                int freeze = integer(element, "freeze-rows", 1, 1048575);
                sheet.createFreezePane(0, freeze);
            }
        }
        require(workbook.getNumberOfSheets() > 0, "At least one sheet is required");
    }
    private void table(Element table, Map<String, Object> model, StyleNode parent) {
        occupied.clear(); tableEnd = rowIndex;
        rows(table, model, node(table, parent));
        rowIndex = Math.max(rowIndex, tableEnd);
    }
    private void rows(Element container, Map<String, Object> model, StyleNode parent) {
        for (Element element : children(container)) {
            String tag = element.getTagName();
            if (tag.equals("tr")) {
                if (element.hasAttribute("sw:each")) {
                    String[] binding = element.getAttribute("sw:each").split(":", -1);
                    require(binding.length == 2 && binding[0].trim().matches("[a-zA-Z_][\\w]*"), "Expected sw:each='item : items'");
                    Object data = Expressions.value(binding[1], model);
                    require(data != null, "Loop source is null");
                    Iterable<?> iterable;
                    if (data instanceof Iterable) iterable = (Iterable<?>) data;
                    else if (data.getClass().isArray()) {
                        List<Object> list = new ArrayList<Object>();
                        for (int i = 0; i < Array.getLength(data); i++) list.add(Array.get(data, i));
                        iterable = list;
                    } else throw error(element, "Loop source must be Iterable or array");
                    int index = 0;
                    for (Object item : iterable) {
                        Map<String, Object> local = new HashMap<String, Object>(model);
                        local.put(binding[0].trim(), item);
                        if (element.hasAttribute("sw:index")) {
                            String alias = element.getAttribute("sw:index");
                            require(alias.matches("[a-zA-Z_][\\w]*") && !alias.equals(binding[0].trim()), "Invalid loop index alias");
                            Map<String, Object> state = new HashMap<String, Object>();
                            state.put("index", index); state.put("number", index + 1);
                            state.put("first", index == 0); state.put("even", (index + 1) % 2 == 0);
                            local.put(alias, state);
                        }
                        if (visible(element, local)) row(element, local, parent);
                        index++;
                    }
                } else if (visible(element, model)) row(element, model, parent);
            } else if (tag.equals("thead") || tag.equals("tbody") || tag.equals("tfoot")) {
                require(container.getTagName().equals("table"), "Table sections cannot be nested");
                if (visible(element, model)) rows(element, model, node(element, parent));
            } else throw error(element, "Table expects tr, thead, tbody or tfoot");
        }
    }
    private void row(Element element, Map<String, Object> model, StyleNode parent) {
        require(rowIndex < 1048576, "Excel row limit exceeded");
        StyleNode rowStyle = node(element, parent);
        int column = 0;
        for (Element cell : children(element)) {
            require(cell.getTagName().equals("td") || cell.getTagName().equals("th"), "Rows only accept td/th");
            while (occupied.contains(key(rowIndex, column))) column++;
            int rowSpan = integer(cell, "rowspan", 1, 1048576);
            int colSpan = integer(cell, "colspan", 1, 16384);
            require((long) rowIndex + rowSpan <= 1048576 && (long) column + colSpan <= 16384, "Cell exceeds Excel limits");
            require((long) rowSpan * colSpan <= 1000000, "Merged area exceeds safety limit of 1000000 cells");
            for (int r = rowIndex; r < rowIndex + rowSpan; r++)
                for (int c = column; c < column + colSpan; c++)
                    require(occupied.add(key(r, c)), "Overlapping rowspan/colspan at row " + (r + 1) + ", column " + (c + 1));
            writeCell(cell, model, node(cell, rowStyle), rowIndex, column, rowSpan, colSpan);
            column += colSpan; tableEnd = Math.max(tableEnd, rowIndex + rowSpan);
        }
        height(element, rowIndex);
        final int completedRow = rowIndex++;
        // Retain only reservations for future rows.
        occupied.removeIf(k -> (k >>> 14) <= completedRow);
    }
    private void writeCell(Element element, Map<String, Object> model, StyleNode node, int row, int col, int rows, int cols) {
        require(row < 1048576, "Excel row limit exceeded");
        XSSFCellStyle style = styles.get(stylesheet.resolve(node));
        for (int r = row; r < row + rows; r++) {
            XSSFRow target = sheet.getRow(r);
            if (target == null) target = sheet.createRow(r);
            for (int c = col; c < col + cols; c++) target.createCell(c).setCellStyle(style);
        }
        XSSFCell target = sheet.getRow(row).getCell(col);
        Object value;
        if (element.hasAttribute("sw:value")) {
            require(children(element).isEmpty() && element.getTextContent().trim().isEmpty(), "sw:value cannot be combined with cell content");
            value = Expressions.value(element.getAttribute("sw:value"), model);
        } else value = content(element, model).trim();
        if (value == null) target.setBlank();
        else if (value instanceof Boolean) target.setCellValue((Boolean) value);
        else if (value instanceof Number) {
            BigDecimal number;
            try { number = new BigDecimal(value.toString()); }
            catch (NumberFormatException ex) { throw error(element, "Number is not finite"); }
            require(number.stripTrailingZeros().precision() <= 15, "Excel numeric precision exceeds 15 significant digits; bind a String instead");
            double d = number.doubleValue();
            require(Double.isFinite(d) && (d != 0 || number.signum() == 0), "Number outside Excel numeric range");
            target.setCellValue(d);
        } else if (value instanceof LocalDate) target.setCellValue((LocalDate) value);
        else if (value instanceof LocalDateTime) target.setCellValue((LocalDateTime) value);
        else if (value instanceof Date) target.setCellValue((Date) value);
        else if (value instanceof CharSequence || value instanceof Character || value instanceof Enum) target.setCellValue(value.toString());
        else throw error(element, "Unsupported cell value type: " + value.getClass().getName());
        if (rows > 1 || cols > 1) sheet.addMergedRegion(new CellRangeAddress(row, row + rows - 1, col, col + cols - 1));
    }
    private String content(Element element, Map<String, Object> model) {
        StringBuilder result = new StringBuilder();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) result.append(child.getNodeValue());
            else if (child instanceof Element) {
                Element part = (Element) child;
                if (part.getTagName().equals("sw:out")) {
                    Object value = Expressions.value(part.getAttribute("value"), model);
                    if (value != null) result.append(value);
                } else if (part.getTagName().equals("sw:choose")) {
                    boolean selected = false, otherwise = false;
                    for (Element branch : children(part)) {
                        String tag = branch.getTagName();
                        require(tag.equals("sw:when") || tag.equals("sw:otherwise"), "choose expects when/otherwise");
                        require(!otherwise, "otherwise must be last and unique");
                        otherwise = tag.equals("sw:otherwise");
                        if (!selected && (otherwise || Expressions.test(branch.getAttribute("test"), model))) {
                            result.append(content(branch, model)); selected = true;
                        }
                    }
                } else throw error(part, "Only sw:out and sw:choose are supported inside cell content");
            }
        }
        return result.toString();
    }
    private void validate(Element element) {
        Set<String> allowed = ATTRIBUTES.get(element.getTagName());
        require(allowed != null, "Unknown element: " + element.getTagName());
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++)
            require(allowed.contains(attributes.item(i).getNodeName()), "Unsupported attribute " + attributes.item(i).getNodeName() + " on " + element.getTagName());
        for (Element child : children(element)) validate(child);
    }
    private boolean visible(Element element, Map<String, Object> model) {
        return !element.hasAttribute("sw:if") || Expressions.test(element.getAttribute("sw:if"), model);
    }
    private void height(Element element, int row) {
        if (element.hasAttribute("height")) {
            int height = integer(element, "height", 15, 409);
            XSSFRow target = sheet.getRow(row); if (target == null) target = sheet.createRow(row);
            target.setHeightInPoints(height);
        }
    }
    private static int integer(Element element, String attribute, int fallback, int max) {
        if (!element.hasAttribute(attribute)) return fallback;
        try {
            int value = Integer.parseInt(element.getAttribute(attribute));
            require(value > 0 && value <= max, "Invalid " + attribute + " on " + element.getTagName()); return value;
        } catch (NumberFormatException ex) { throw error(element, "Invalid integer: " + attribute); }
    }
    private static StyleNode node(Element element, StyleNode parent) {
        String tag = element.getTagName();
        return new StyleNode(tag.startsWith("sw:") ? tag.substring(3) : tag, element.getAttribute("id"), element.getAttribute("class"), parent);
    }
    private static List<Element> children(Element element) {
        List<Element> result = new ArrayList<Element>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element) result.add((Element) nodes.item(i));
        return result;
    }
    private static long key(int row, int col) { return ((long) row << 14) | col; }
    private static void require(boolean condition, String message) { if (!condition) throw new SheetWeaveException(message); }
    private static SheetWeaveException error(Element element, String message) { return new SheetWeaveException(message + " in <" + element.getTagName() + ">"); }
}
