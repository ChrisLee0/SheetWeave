# SheetWeave · 织表

Java 8 Excel reports with HTML-style table templates and SCSS-style nested styles, powered by Apache POI.

用 HTML 风格结构描述报表，用 SCSS 风格嵌套语法复用样式，用 Java 提供数据。

**Status: 0.1.0-SNAPSHOT / experimental.** This is a working foundation, not a complete browser, Sass compiler or production reporting platform. API and template grammar may change before 1.0.

## Requirements / 构建

- JDK 8 or later; Maven 3.6.3 or later.
- Apache POI **5.5.1**, pinned for Java 8 compatibility. POI 6.x is not a drop-in Java 8 upgrade.
- Output: `.xlsx`, using in-memory `XSSFWorkbook`.

```sh
mvn clean verify
mvn install
```

The GitHub Actions workflow tests Java 8, 17 and 21. Compiler release is 8, including Java API compatibility when compiling on newer JDKs. Maven coordinates below work **after local installation**; this project is not published to Maven Central.

```xml
<dependency>
    <groupId>io.github.chrislee0</groupId>
    <artifactId>sheetweave</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick start / 使用示例

Put templates and styles in your application's `src/main/resources/reports/`:

**sales.sw.html** — XML-well-formed HTML-style syntax (closing tags required):

```html
<sw:workbook xmlns:sw="urn:sheetweave">
    <sw:sheet name="销售报表" widths="24,18" class="report">
        <sw:text span="2" class="title" height="30">销售报表</sw:text>
        <sw:spacer rows="1" />
        <table>
            <thead>
                <tr><th>客户</th><th>销售额</th></tr>
            </thead>
            <tbody>
                <tr sw:each="order : orders">
                    <td sw:value="order.customer" />
                    <td sw:value="order.amount" class="money" />
                </tr>
            </tbody>
        </table>
        <sw:text sw:if="showNote">备注：<sw:out value="note" /></sw:text>
    </sw:sheet>
</sw:workbook>
```

**sales.scss**:

```scss
.report {
    font-family: "微软雅黑";
    font-size: 11pt;

    .title {
        font-size: 20pt;
        font-weight: bold;
        text-align: center;
    }

    thead {
        th {
            background-color: #305496;
            color: #ffffff;
            font-weight: bold;
        }
    }

    .money {
        text-align: right;
        -excel-number-format: "#,##0.00";
        &.negative { color: #c62828; }
    }
}
```

```java
Map<String, Object> order = new HashMap<>();
order.put("customer", "客户 A");
order.put("amount", new BigDecimal("12345.67"));

try (OutputStream output = Files.newOutputStream(Paths.get("sales.xlsx"))) {
    SheetWeave.create()
        .templateResource("reports/sales.sw.html")
        .stylesheetResource("reports/sales.scss")
        .bind("orders", Arrays.asList(order))
        .bind("showNote", true)
        .bind("note", "已复核")
        .writeTo(output);
}
```

Import `io.github.chrislee0.sheetweave.SheetWeave`, standard `java.io`, `java.nio.file`, `java.util`, and `java.math.BigDecimal` types as needed. `.template(String)` and `.stylesheet(String)` accept text directly.

`writeTo` closes its internal workbook, **not** the caller's stream. `build()` returns an `XSSFWorkbook` that the caller must close. Each build has its own POI style cache. Builders and bound data are not thread-safe.

A complete runnable example is in [`examples/SalesReport.java`](examples/SalesReport.java). The round-trip test and resources demonstrate multi-row headers, conditional rows and branching.

## Implemented template grammar

| Construct | Behavior |
|---|---|
| `sw:workbook` / `sw:sheet` | Multiple named sheets |
| `sw:sheet widths="24,18"` | Sheet-wide widths in approximate Excel character units |
| `sw:sheet freeze-rows="4"` | Freeze top four physical rows |
| `sw:text span="2" height="30"` | Text/bound value, optional horizontal merge and point height |
| `sw:spacer rows="1"` | Vertical blank rows |
| `table`, `thead`, `tbody`, `tfoot`, `tr`, `td`, `th` | Tables render top to bottom in source order |
| `rowspan` / `colspan` | Merged cells; overlapping spans and Excel bounds are rejected |
| `sw:value="order.amount"` | Preserve bound scalar type |
| `sw:each="order : orders"` on `tr` | Iterate an `Iterable` or array |
| `sw:index="loop"` | `loop.index`, `number`, `first`, `even`; index is before filtering |
| `sw:if="condition"` | On blocks, sections or rows; omitted content takes no space |
| `sw:choose`, `sw:when test="..."`, `sw:otherwise` | First matching textual branch inside a cell/text block |
| `sw:out value="note"` | Convert a scalar to inline text |

Conditions on individual cells are rejected to avoid column shifts. Nesting tables inside cells is not supported. Sections render in source order, including `tfoot`. Cell content supports literal text, `sw:out` and `sw:choose`; arbitrary inline HTML is not supported. A `sw:value` cell must otherwise be empty. Text content is trimmed at its outer edges.

## Expressions and data types

- Property paths through Maps or public JavaBean getters: `order.customer.name`.
- Literals: `null`, `true`, `false`, decimal numbers, quoted strings.
- Conditions: boolean property, `!boolean`, or **one comparison**: `order.amount >= 10000`, `order.status == 'PAID'`, `note != null`.
- Ordered comparisons require numbers. Decimal comparisons use `BigDecimal`.
- In XML attributes, escape `<` as `&lt;` and `&` as `&amp;`.
- Unknown fields and null intermediate paths raise errors. A known property with a null value produces a blank cell.
- No method-call syntax, arbitrary class access, constructors, script execution, arithmetic, `&&`, `||` or ternaries in this version.
- JavaBean getters still execute application code: bind trusted DTOs, not arbitrary executable objects.
- Numeric values remain numbers, booleans remain booleans; `LocalDate`, `LocalDateTime` and `java.util.Date` are supported. Date display formats must be set explicitly. `java.util.Date` conversion follows the JVM default timezone; prefer `LocalDate`/`LocalDateTime` for predictable local report values.
- Numeric inputs exceeding 15 significant digits or double range are rejected. Bind identifiers/high-precision decimals as **String** when exact text preservation is required. Excel's underlying numeric representation is floating point.
- Strings beginning with `=` remain strings, never implicit formulas.

## SCSS-style subset

Supported:

- Nested rules: `.report { .money { ... } }`.
- A single leading parent reference: `&.negative`, `& .child`.
- Comma-separated selector lists, including nested combinations.
- Type selectors (`sheet`, `text`, `table`, `thead`, `tr`, `td`, `th`), `.class`, `#id`, `*`, compound selectors and descendant selectors.
- `/* comments */`, quoted values including semicolons in Excel number formats.
- Specificity: ID count, then class count, then type count; later declarations win ties.
- Inheritance: font family/size/weight/style, text color and horizontal alignment. Backgrounds and number formats do not inherit. Apply number formats to the actual cell class.
- `sw:` is stripped for type matching: `sw:sheet` matches `sheet`.

Supported properties:

| Property | Values |
|---|---|
| `font-family` | Font name; quotes optional |
| `font-size` | Integer `pt`, 1–409 |
| `font-weight` | `normal`, `bold` |
| `font-style` | `normal`, `italic` |
| `color`, `background-color` | `#RRGGBB` |
| `text-align` | `left`, `center`, `right` |
| `vertical-align` | `top`, `middle`, `bottom` |
| `-excel-number-format` | Excel number format string |
| `-excel-wrap-text` | `true`, `false` |

Unknown properties fail during parsing; invalid property values fail when the rule is applied. No Sass variables, mixins, imports, interpolation, functions, pseudo-classes, child/sibling combinators, inline `style`, `!important`, borders, flexbox or CSS grid yet. `.negative` is a class you explicitly attach; automatic conditional class binding is planned. This is **not full Sass/SCSS compatibility**.

## Design and limits

See [`docs/architecture.md`](docs/architecture.md) for the design and staged roadmap.

- XML parser rejects DOCTYPE, external entities and external schema access.
- Stylesheets and templates are application-owned configuration, not an untrusted public upload service. This is not a resource-exhaustion sandbox.
- Uses in-memory POI and a DOM template. Large datasets can consume substantial memory; SXSSF streaming is not implemented.
- A merged area is limited to 1,000,000 physical cells; distinct styles to 10,000 per workbook.
- No automatic width/height measurement. Fonts and wrapping depend on the spreadsheet viewer.
- The sheet has one shared column grid. Independent nested/side-by-side table grids are not implemented.
- No formula evaluation, images, charts, template workbook import, automatic subtotals or dynamic whole-column removal yet.
- Error messages identify expressions/elements; SCSS parser errors include line numbers. Exact template source-position diagnostics are planned.

## Contributing

Run `mvn clean verify` before submitting a pull request. Add round-trip POI assertions for observable workbook behavior. Keep Java 8 compatibility, fail clearly on unsupported grammar, and document any syntax change. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

MIT. Apache POI and its transitive dependencies retain their respective licenses. SheetWeave is an independent project, not an Apache Software Foundation project.
