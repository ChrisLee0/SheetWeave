# Run the example

From the repository root, with Maven and JDK 8+ installed (Linux/macOS):

```sh
mvn clean verify dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
javac -encoding UTF-8 -cp "target/classes:$(cat target/classpath.txt)" -d target/examples examples/SalesReport.java
java -cp "target/examples:target/classes:$(cat target/classpath.txt)" SalesReport
```

Open `target/sales.xlsx`. Windows uses `;` instead of `:` as the classpath separator; you can also run `SalesReport.main` from an IDE with this Maven project on its classpath.

The template and stylesheet are shared with round-trip tests in `src/test/resources` to keep examples exercised by CI.
