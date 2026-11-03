import io.github.chrislee0.sheetweave.SheetWeave;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Run from repository root; see examples/README.md. */
public class SalesReport {
    public static void main(String[] args) throws Exception {
        List<Map<String, Object>> orders = new ArrayList<>();
        orders.add(order("客户 A", "12345.67"));
        orders.add(order("客户 B", "800.00"));
        String template = new String(Files.readAllBytes(Paths.get("src/test/resources/sales.sw.html")), StandardCharsets.UTF_8);
        String stylesheet = new String(Files.readAllBytes(Paths.get("src/test/resources/sales.scss")), StandardCharsets.UTF_8);
        Files.createDirectories(Paths.get("target"));
        try (OutputStream output = Files.newOutputStream(Paths.get("target/sales.xlsx"))) {
            SheetWeave.create().template(template).stylesheet(stylesheet)
                .bind("orders", orders).bind("showNote", true).bind("note", "已复核").writeTo(output);
        }
        System.out.println("Generated target/sales.xlsx");
    }
    private static Map<String, Object> order(String customer, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer", customer);
        row.put("amount", new BigDecimal(amount));
        row.put("visible", true);
        return row;
    }
}
