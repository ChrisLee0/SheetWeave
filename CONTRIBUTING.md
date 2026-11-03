# Contributing

- Use Java 8 language features and APIs only; `mvn clean verify` is the baseline.
- Keep generated files, secrets, IDE state and `target/` out of Git.
- Add tests for user-visible workbook behavior and invalid input, not just internal implementation details.
- Document new syntax and unsupported combinations in README.
- Do not silently reinterpret unsupported HTML/CSS/Sass features.
- Separate business calculations from template presentation logic.
- Open a pull request with the problem, resulting behavior and verification performed.
