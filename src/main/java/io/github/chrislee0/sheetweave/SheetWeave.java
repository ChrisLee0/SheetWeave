package io.github.chrislee0.sheetweave;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Entry point. Builders are not thread-safe; each build creates an independent workbook. */
public final class SheetWeave {
    private String template;
    private Stylesheet stylesheet = Stylesheet.parse("");
    private final Map<String, Object> model = new HashMap<String, Object>();
    private SheetWeave() {}
    public static SheetWeave create() { return new SheetWeave(); }
    public SheetWeave template(String template) { this.template = Objects.requireNonNull(template, "template"); return this; }
    public SheetWeave stylesheet(String source) { this.stylesheet = Stylesheet.parse(source); return this; }
    public SheetWeave templateResource(String name) throws IOException { return template(resource(name)); }
    public SheetWeave stylesheetResource(String name) throws IOException { return stylesheet(resource(name)); }
    public SheetWeave bind(String name, Object value) { model.put(Objects.requireNonNull(name, "name"), value); return this; }

    /** Caller owns and must close the returned workbook. */
    public XSSFWorkbook build() {
        if (template == null) throw new SheetWeaveException("A template is required");
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(template)));
        } catch (Exception ex) { throw new SheetWeaveException("Invalid XML template: " + ex.getMessage(), ex); }
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            new TemplateRenderer(workbook, stylesheet).render(document.getDocumentElement(), new HashMap<String, Object>(model));
            return workbook;
        } catch (RuntimeException ex) {
            try { workbook.close(); } catch (IOException close) { ex.addSuppressed(close); }
            throw ex;
        }
    }
    /** Writes XLSX without closing the caller's output stream. */
    public void writeTo(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        try (XSSFWorkbook workbook = build()) { workbook.write(output); }
    }
    private static String resource(String name) throws IOException {
        String path = name.startsWith("/") ? name.substring(1) : name;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = SheetWeave.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) throw new FileNotFoundException("Classpath resource: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
