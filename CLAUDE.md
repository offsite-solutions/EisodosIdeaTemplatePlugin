# CLAUDE.md

## Project Overview

PhpStorm/IntelliJ plugin providing language support for Eisodos framework `.template` files. Built as a mixed HTML+template language plugin following the same architecture as the JetBrains Handlebars plugin.

**Related project:** The Eisodos framework lives at `../Base/` — the template syntax this plugin supports is defined in `Base/src/Eisodos/TemplateEngine.php` and `Base/src/Eisodos/Parsers/`.

**Eisodos source code:** `/Users/baxi/Work/_eisodos/Base/` — reference this path when reading Eisodos PHP source, template test files, or parser implementations.

## Prerequisites

- **JDK 17** — required by IntelliJ Platform 2024.1. Installed via brew:
  ```bash
  brew install openjdk@17
  ```
- **JDK 21** — used to run Gradle. Installed via brew:
  ```bash
  brew install openjdk@21
  ```
- The JDK 17 path is configured in `gradle.properties` under `org.gradle.java.installations.paths`. Update this if your JDK 17 is installed elsewhere.

## Building

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew build
```

The plugin artifact is produced at:
```
build/distributions/Eisodos Template Support-1.0.0.zip
```

## Installing in PhpStorm

1. PhpStorm → Settings → Plugins → gear icon → "Install Plugin from Disk..."
2. Select `build/distributions/Eisodos Template Support-1.0.0.zip`
3. Restart PhpStorm

## Running a Development Instance

To launch a sandboxed PhpStorm/IntelliJ with the plugin loaded for testing:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runIde
```

This downloads and runs IntelliJ Community Edition with the plugin pre-installed.

## Architecture

### Plugin Type

This is a **TemplateLanguage** plugin — it creates dual PSI trees (one for HTML, one for template syntax) from a single `.template` file. The key interface is `com.intellij.psi.templateLanguages.TemplateLanguage`.

### Directory Structure

```
src/main/kotlin/com/eisodos/intellij/
├── EisodosTemplateLanguage.kt          # Language definition (TemplateLanguage)
├── EisodosTemplateFileType.kt          # .template file type registration
├── EisodosTemplateIcons.kt             # Plugin icon loader
├── parsing/
│   ├── EisodosTemplateLexer.kt         # Hand-written lexer (~15 states)
│   ├── EisodosTemplateTokenTypes.kt    # Token type definitions (~30 types)
│   ├── EisodosTemplateElementType.kt   # IElementType subclasses
│   ├── EisodosTemplateParser.kt        # Recursive descent parser
│   └── EisodosTemplateParserDefinition.kt
├── psi/
│   ├── EisodosTemplatePsiFile.kt       # PSI file for template language
│   └── EisodosTemplatePsiElement.kt    # Base PSI element
├── file/
│   ├── EisodosTemplateFileViewProvider.kt      # Dual PSI tree (HTML + template)
│   └── EisodosTemplateFileViewProviderFactory.kt
└── editor/
    ├── EisodosTemplateSyntaxHighlighter.kt     # Token-to-color mapping
    ├── EisodosTemplateSyntaxHighlighterFactory.kt
    ├── EisodosTemplateBraceMatcher.kt          # [%..%], <%..%>, [:..:]
    ├── EisodosTemplateCommenter.kt             # ## line comments
    └── EisodosTemplateColorSettingsPage.kt     # Settings > Color Scheme page
```

### Key Files

- **`plugin.xml`** (`src/main/resources/META-INF/`) — registers all extensions with the IntelliJ platform
- **`EisodosTemplateLexer.kt`** — the most complex file; hand-written state-machine lexer that tokenizes template syntax within HTML content. States: HTML scanning, parameter expressions, short/full callbacks, language tags
- **`EisodosTemplateFileViewProvider.kt`** — creates the mixed-language support by extending `MultiplePsiFilesPerDocumentFileViewProvider`. HTML content tokens (`CONTENT`) become `TemplateDataElementType` for the HTML PSI tree

### How the Lexer Works

The lexer scans the document and classifies text into two categories:
1. **CONTENT tokens** — HTML content between template constructs → fed to the HTML parser via `TemplateDataElementType`
2. **Template tokens** — all Eisodos syntax (`$param`, `[%...%]`, `<%FUNC%...%FUNC%>`, `[:...:]`, `##`) → parsed by `EisodosTemplateParser`

Lexer states encode the current parsing context (e.g., inside a short callback, inside a quoted string). The state integer encodes both the base state and a structure hash for full callback nesting.

## Template Syntax Reference

The plugin recognizes these Eisodos template constructs:

| Syntax | Description |
|--------|-------------|
| `$paramName` | Parameter reference |
| `$paramName:='value';` | Inline parameter assignment |
| `$paramName~='default';` | Default value (used if param is empty) |
| `$template_name` | Include inline template |
| `$templateabs_path__to__file` | Include absolute-path template |
| `$callback_paramName` | Parameter callback invocation |
| `[%funcjob=eqs;param=x;value=Y;true=yes;false=no%]` | Short callback function |
| `<%FUNC%...\n%FUNC%>` | Full callback function (multiline) |
| `[:LANG_ID,default text:]` | Language translation tag |
| `[:LANG_ID,default:param1;param2:]` | Language tag with parameters |
| `## comment` | Line comment (removed in output) |

Full callback blocks support:
- `_function_name=name` — callback function to invoke
- `_include=file.php` — PHP file to require before callback
- `_parameter_prefix=prefix` — prefix for global parameter registration
- `_real_parameters=Y` — pass params as function arguments
- `@param=$var` — parse value as template before passing
- `param>>=`...`<<=` — multiline parameter values

## Build Configuration

- **Gradle plugin:** `org.jetbrains.intellij` v1.17.4 (legacy 1.x API)
- **Kotlin:** 1.9.25
- **Target platform:** IntelliJ Community (IC) 2024.1.7
- **Compatibility range:** 241–252.* (IntelliJ 2024.1 through 2025.2)
- **JVM target:** 17

## Version Bumping

Update version in `gradle.properties`:
```properties
pluginVersion=1.0.0
```

To support newer IntelliJ versions, update:
```properties
platformVersion=2024.1.7
pluginSinceBuild=241
pluginUntilBuild=252.*
```

## Code Conventions

- All plugin code is Kotlin
- Package root: `com.eisodos.intellij`
- Token types are defined as `@JvmField val` constants in `EisodosTemplateTokenTypes`
- Composite element types use `EisodosTemplateCompositeElementType`
- The lexer extends `LexerBase` (not JFlex) for easier maintenance
- No external dependencies beyond the IntelliJ Platform SDK
