package io.github.chrislee0.sheetweave;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class StylesheetTest {
    private StyleNode sheet = new StyleNode("sheet", "", "report", null);
    private StyleNode cell = new StyleNode("td", "profit", "money negative", sheet);
    @Test public void nestedParentSelectorAndPropertyMerge() {
        Map<String, String> style = Stylesheet.parse(".report { font-size: 11pt; .money { -excel-number-format: '#,##0.00'; &.negative { color: #ff0000; } } }").resolve(cell);
        assertEquals("11pt", style.get("font-size"));
        assertEquals("#,##0.00", style.get("-excel-number-format"));
        assertEquals("#ff0000", style.get("color"));
    }
    @Test public void specificityAndSourceOrder() {
        Map<String, String> style = Stylesheet.parse("#profit { color: #111111; } .money { color: #222222; } .negative { color: #333333; } .money { font-weight: bold; } .negative { font-weight: normal; }").resolve(cell);
        assertEquals("#111111", style.get("color")); assertEquals("normal", style.get("font-weight"));
    }
    @Test public void listsExpandAcrossNesting() {
        assertEquals("#112233", Stylesheet.parse(".other, .report { .amount, .money { color: #112233; } }").resolve(cell).get("color"));
    }
    @Test public void backgroundDoesNotInherit() {
        Map<String, String> style = Stylesheet.parse(".report { background-color: #112233; color: #445566; }").resolve(cell);
        assertFalse(style.containsKey("background-color")); assertEquals("#445566", style.get("color"));
    }
    @Test public void quotedSemicolonsAndBracesArePreserved() {
        assertEquals("0.00;[Red](0.00);\"{zero}\"", Stylesheet.parse(".money { -excel-number-format: '0.00;[Red](0.00);\"{zero}\"'; }").resolve(cell).get("-excel-number-format"));
    }
    @Test public void commentsAndDeclarationsAfterNesting() {
        assertEquals("#222222", Stylesheet.parse("/* top */ .money { color: #111111; &.negative { font-weight: bold; } color: /* note */ #222222; }").resolve(cell).get("color"));
    }
    @Test public void rejectsUnsupportedSyntaxAndUnclosedBlocks() {
        for (String invalid : Arrays.asList(".x { padding: 4px; }", ".x { color: #112233;", "&.x { color: #112233; }", ".x:hover { color: #112233; }", ".x { color: 'unclosed; }", ".x { color: #112233 !important; }")) {
            try { Stylesheet.parse(invalid); fail(invalid); } catch (SheetWeaveException expected) { /* expected */ }
        }
    }
}
