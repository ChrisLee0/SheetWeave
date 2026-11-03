# Architecture / 设计与路线图

## Current implementation (0.1 foundation)

1. `SheetWeave`: builder, classpath resources, typed model, secure XML parsing, workbook lifecycle.
2. `Stylesheet`: parse nested rules, flatten selector lists and leading `&`, calculate specificity, property-level inheritance/merging.
3. `StyleNode`: logical template ancestry, independent of Excel coordinates.
4. `Expressions`: restricted typed property access and comparisons.
5. `TemplateRenderer`: expand row loops/conditions, place top-to-bottom blocks, reserve rowspan cells, reject overlap, render values.
6. `PoiStyles`: resolve immutable property-map keys into workbook-local reusable POI styles.

The current renderer combines layout and writing to an in-memory workbook. It validates layout while building, before `writeTo` emits workbook bytes. It is **not yet** the separate layout compiler described in the long-term design. IO errors may still leave partial output; callers needing atomic files should write to a temporary file and rename after success.

Java data should contain business calculations. Templates control presentation. Styles define visual appearance. Unknown template attributes and CSS properties fail instead of silently disappearing.

## Layout semantics

A sheet owns one global column grid. Each table starts at column A after the previous block. Multi-row headers use rowspan/colspan. A following block starts after the greatest occupied row, including rowspans. Hidden rows/blocks consume no space. Hiding one data cell is intentionally unsupported because it could shift the remaining columns.

HTML syntax is an authoring vocabulary, not browser layout compatibility. SCSS nesting is a stylesheet authoring feature, not permission to evaluate arbitrary Sass programs.

## Planned milestones (not implemented)

### 0.2 — style and diagnostics
- Borders, explicit area-vs-cell border semantics, reusable named themes.
- Conditional class binding with strict expression typing.
- Template source locations and an explain-style diagnostic.
- Compile selector matchers once; resolve ancestry once per logical row.

### 0.3 — layout compiler
- Immutable report AST and compiled layout plan.
- Java Block API and markup frontend sharing the same plan.
- Vertical/horizontal regions, explicit track spans, conflict diagnostics.
- Column definitions with whole-column conditions, grouped headers.

### 0.4 — reporting and streaming
- Grouping with explicit input-order contract, subtotal/grand-total blocks.
- Logical formula references and explicit recalculation policy.
- SXSSF mode, one-pass data sources, documented unsupported combinations.
- Bounded lookahead, cleanup on cancellation, output-size/row/style budgets.

### Later
- Images, print configuration, conditional formatting, accessibility-friendly documentation.
- Workbook template import only after fidelity and compatibility requirements are specified.

## Test strategy

Tests reopen produced XLSX bytes with POI and inspect types, merged regions, formats, fills and positions. Parser tests cover nesting, parent selectors, selector specificity, source order, quoted semicolons, comments and rejection. Security and failure tests cover XXE, unknown fields, forbidden class access, precision, span conflicts and resource ownership. CI runs on JDK 8/17/21.

These tests validate workbook structure, not pixel-perfect rendering in Excel/LibreOffice. Manual viewer checks remain appropriate before a stable release.
