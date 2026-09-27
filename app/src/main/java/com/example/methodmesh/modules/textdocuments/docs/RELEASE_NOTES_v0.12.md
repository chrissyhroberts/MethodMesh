# Text documents v0.12

- Broadened filename and MIME detection for YAML, TOML, XML, HTML, CSS, JavaScript, TypeScript, Python, Kotlin, Java, shell, SQL, CSV, properties/INI and common source-code files.
- Added an all-file picker fallback so source and configuration files are not hidden by inaccurate provider MIME types.
- Rejects obvious binary content instead of silently decoding it as damaged UTF-8 text.
- Save As now preserves an existing filename extension, including extensions the module does not recognise.
- Improved the library copy, format grouping and Markdown Edit/Preview affordance.
- Added focused detection, contract and extension-preservation tests.
