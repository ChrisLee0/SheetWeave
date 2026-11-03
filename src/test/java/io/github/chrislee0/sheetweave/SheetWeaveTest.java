package io.github.chrislee0.sheetweave;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

public class SheetWeaveTest {
    private static String wrap(String body) {
        return "<sw:workbook xmlns:sw='urn:sheetweave'><sw:sheet name='Report'>" + body + "</sw:sheet></sw:workbook>";
    }
    private static Map<String, Object> order(String name, String amount, boolean visible) {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("customer", name); row.put("amount", new BigDecimal(amount)); row.put("visible", visible); return row;
    }
    private void fails(String template, String message) {
        try (XSSFWorkbook ignored = SheetWeave.create().template(template).build()) { fail("Expected failure"); }
        catch (SheetWeaveException ex) { assertTrue(ex.getMessage(), ex.getMessage().contains(message)); }
        catch (IOException ex) { throw new AssertionError(ex); }
    }
    @Test public void roundTripNestedStylesLoopsMergesAndBranches() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SheetWeave.create().templateResource("sales.sw.html").stylesheetResource("sales.scss")
            .bind("orders", Arrays.asList(order("A", "12345.67", true), order("hidden", "9", false), order("B", "100", true)))
            .bind("showNote", true).bind("note", "已复核").writeTo(out);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertEquals(3, sheet.getNumMergedRegions());
            assertEquals("A3:A4", sheet.getMergedRegion(1).formatAsString());
            assertEquals("B3:C3", sheet.getMergedRegion(2).formatAsString());
            assertEquals("A", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(12345.67, sheet.getRow(4).getCell(1).getNumericCellValue(), .0001);
            assertEquals("大额", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("普通", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("备注：已复核", sheet.getRow(6).getCell(0).getStringCellValue());
            assertEquals("#,##0.00", sheet.getRow(4).getCell(1).getCellStyle().getDataFormatString());
            assertEquals("305496", sheet.getRow(2).getCell(0).getCellStyle().getFillForegroundXSSFColor().getARGBHex().substring(2));
            assertEquals(22 * 256, sheet.getColumnWidth(0));
            assertEquals(4, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }
    @Test public void preservesTypesAndDoesNotInterpretFormulaStrings() throws Exception {
        String template = wrap("<table><tr><td sw:value='number'/><td sw:value='date' class='date'/><td sw:value='flag'/><td sw:value='empty'/><td sw:value='text'/></tr></table>");
        try (XSSFWorkbook wb = SheetWeave.create().template(template)
            .stylesheet(".date { -excel-number-format: 'yyyy-mm-dd'; }")
            .bind("number", new BigDecimal("12.50")).bind("date", LocalDate.of(2026, 11, 3))
            .bind("flag", true).bind("empty", null).bind("text", "=1+1").build()) {
            Row row = wb.getSheetAt(0).getRow(0);
            assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
            assertEquals(LocalDate.of(2026, 11, 3), row.getCell(1).getLocalDateTimeCellValue().toLocalDate());
            assertTrue(row.getCell(2).getBooleanCellValue());
            assertEquals(CellType.BLANK, row.getCell(3).getCellType());
            assertEquals(CellType.STRING, row.getCell(4).getCellType());
        }
    }
    @Test public void reusesStylesAcrossThousandsOfCells() throws Exception {
        List<Integer> rows = new ArrayList<Integer>(); for (int i = 0; i < 2000; i++) rows.add(i);
        try (XSSFWorkbook wb = SheetWeave.create().template(wrap("<table><tr sw:each='item : items'><td sw:value='item' class='number'/></tr></table>"))
            .stylesheet(".number { -excel-number-format: '0'; }").bind("items", rows).build()) {
            assertTrue(wb.getNumCellStyles() < 5);
            assertEquals(1999, wb.getSheetAt(0).getLastRowNum());
        }
    }
    @Test public void falseRegionDoesNotReserveRows() throws Exception {
        try (XSSFWorkbook wb = SheetWeave.create().template(wrap("<sw:text sw:if='false'>hidden</sw:text><sw:text>visible</sw:text>")).build()) {
            assertEquals("visible", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }
    @Test public void rejectsExternalEntities() {
        fails("<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>" + wrap("<sw:text>&xxe;</sw:text>"), "Invalid XML template");
    }
    @Test public void rejectsOverlap() {
        fails(wrap("<table><tr><td>A</td><td rowspan='2'>B</td></tr><tr><td colspan='2'>bad</td></tr></table>"), "Overlapping");
    }
    @Test public void rejectsCellDeletion() {
        fails(wrap("<table><tr><td sw:if='false'>bad</td></tr></table>"), "Unsupported attribute");
    }
    @Test public void rejectsUnknownField() {
        fails(wrap("<sw:text sw:value='typo'/>"), "Unknown property");
    }
    @Test public void rejectsUnknownElements() {
        fails(wrap("<script/>"), "Unknown element");
    }
    @Test public void rejectsExcessNumericPrecision() {
        fails(wrap("<sw:text sw:value='1234567890123456'/>"), "15 significant digits");
    }
    @Test public void rejectsExcelBounds() {
        fails(wrap("<table><tr><td colspan='16385'>bad</td></tr></table>"), "Invalid colspan");
    }
    @Test public void doesNotCloseCallerStream() throws Exception {
        final boolean[] closed = {false};
        OutputStream out = new ByteArrayOutputStream() { @Override public void close() { closed[0] = true; } };
        SheetWeave.create().template(wrap("<sw:text>Hello</sw:text>")).writeTo(out);
        assertFalse(closed[0]);
    }
    @Test public void loopStateAndConditions() throws Exception {
        try (XSSFWorkbook wb = SheetWeave.create().template(wrap("<table><tr sw:each='item : items' sw:index='loop' sw:if='item > 1'><td sw:value='loop.number'/></tr></table>"))
            .bind("items", Arrays.asList(1, 2, 3)).build()) {
            assertEquals(2, wb.getSheetAt(0).getRow(0).getCell(0).getNumericCellValue(), 0);
        }
    }
    @Test public void rejectsInvalidStyleValues() {
        try (XSSFWorkbook ignored = SheetWeave.create().template(wrap("<sw:text>Hello</sw:text>"))
            .stylesheet("text { color: blue; }").build()) { fail(); }
        catch (SheetWeaveException ex) { assertTrue(ex.getMessage().contains("#RRGGBB")); }
        catch (IOException ex) { throw new AssertionError(ex); }
    }
    @Test public void rejectsMethodsAndClassAccess() {
        fails(wrap("<sw:text sw:value='Runtime.exec()'/>"), "Unsupported expression");
        fails(wrap("<sw:text sw:value='class'/>"), "Forbidden property");
    }
    @Test public void rowSpanReservesSpaceBeforeNextBlock() throws Exception {
        try (XSSFWorkbook wb = SheetWeave.create().template(wrap("<table><tr><td rowspan='3'>A</td></tr></table><sw:text>After</sw:text>")).build()) {
            assertEquals("After", wb.getSheetAt(0).getRow(3).getCell(0).getStringCellValue());
        }
    }
}
