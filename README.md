# Eisodos Template Support

IntelliJ/PhpStorm plugin providing language support for [Eisodos](https://github.com/nicemattwork/eisodos) framework `.template` files.

## Features

- **Syntax highlighting** for all Eisodos template constructs
- **Mixed HTML + template editing** — full HTML intelligence alongside template syntax
- **Parameter references** — `$paramName`, `$param:='value'`, `$param~='default'`
- **Short callback functions** — `[%funcjob=eqs;param=test;value=Y%]`
- **Full callback functions** — `<%FUNC%...%FUNC%>`
- **Language translation tags** — `[:LANG_ID,default text:]`
- **Line comments** — `## comment`
- **Brace matching** for callback and language tag delimiters
- **Configurable color scheme** via Settings > Editor > Color Scheme

## Template Syntax Reference

| Syntax | Description |
|--------|-------------|
| `$paramName` | Parameter reference |
| `$paramName:='value';` | Inline parameter assignment |
| `$paramName~='default';` | Default value (used if param is empty) |
| `$template_name` | Include inline template |
| `[%funcjob=eqs;param=x;value=Y;true=yes;false=no%]` | Short callback function |
| `<%FUNC%...%FUNC%>` | Full callback function (multiline) |
| `[:LANG_ID,default text:]` | Language translation tag |
| `[:LANG_ID,default:param1;param2:]` | Language tag with parameters |
| `## comment` | Line comment (removed in output) |

## Requirements

- IntelliJ IDEA / PhpStorm **2024.1 – 2026.1** (build 241–261.*)
- JDK 17+ (for running the IDE)

## Installation

### From Disk

1. Download the latest release ZIP from the [Releases](../../releases) page
2. In PhpStorm/IntelliJ: **Settings → Plugins → ⚙ → Install Plugin from Disk...**
3. Select the downloaded ZIP file
4. Restart the IDE

## Building from Source

### Prerequisites

- JDK 17 — required by IntelliJ Platform 2024.1
- JDK 21 — used to run Gradle

On macOS:
```bash
brew install openjdk@17 openjdk@21
```

### Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew build
```

The plugin ZIP is produced at `build/distributions/Eisodos Template Support-1.0.0.zip`.

### Run Development Instance

Launch a sandboxed IDE with the plugin pre-installed:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runIde
```

## Architecture

This is a **TemplateLanguage** plugin — it creates dual PSI trees (HTML + template syntax) from a single `.template` file, following the same pattern as the JetBrains Handlebars plugin.

- **Hand-written lexer** with ~15 states (no JFlex) for easier maintenance
- **Recursive descent parser** for template constructs
- **`MultiplePsiFilesPerDocumentFileViewProvider`** for mixed HTML + template editing

## License

Copyright © Offsite Solutions Kft.