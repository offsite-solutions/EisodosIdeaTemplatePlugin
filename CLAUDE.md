# CLAUDE.md

## Project Overview

PhpStorm/IntelliJ plugin providing language support for Eisodos framework `.template` files. Built as a mixed HTML+template language plugin following the same architecture as the JetBrains Handlebars plugin.

**Related project:** The Eisodos framework lives at `../Base/` — the template syntax this plugin supports is defined in `Base/src/Eisodos/TemplateEngine.php` and `Base/src/Eisodos/Parsers/`.

**Eisodos source code:** `/Users/baxi/Work/_eisodos/Base/` — reference this path when reading Eisodos PHP source, template test files, or parser implementations.

## Prerequisites

- **JDK 21** — used both to run Gradle and as the Java/Kotlin compilation toolchain. Installed via brew:
  ```bash
  brew install openjdk@21
  ```
- The JDK 21 path is configured in `gradle.properties` under `org.gradle.java.installations.paths`. Update this if your JDK 21 is installed elsewhere.

## Building

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew build
```

The plugin artifact is produced at:
```
build/distributions/eisodos-template-support-1.1.0.zip
```

### Verifying IDE Compatibility

`verifyPlugin` runs the JetBrains Plugin Verifier against the PhpStorm versions listed in
`pluginVerifierIdeVersions` (`gradle.properties`) — currently the lower and upper bound of the
declared `since`/`until` range:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew verifyPlugin
```

This is the check that proves compatibility with IDE versions newer than the one we compile
against. Run it whenever `pluginUntilBuild` is raised.

## Installing in PhpStorm

1. PhpStorm → Settings → Plugins → gear icon → "Install Plugin from Disk..."
2. Select `build/distributions/eisodos-template-support-1.1.0.zip`
3. Restart PhpStorm

## Running a Development Instance

To launch a sandboxed PhpStorm/IntelliJ with the plugin loaded for testing:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runIde
```

This downloads and runs PhpStorm with the plugin pre-installed.

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

- **Gradle:** 9.7.1 (required by the IntelliJ Platform Gradle Plugin 2.x)
- **Gradle plugin:** `org.jetbrains.intellij.platform` v2.18.1
- **Kotlin:** 2.4.10
- **Target platform:** PhpStorm (PS) 2026.1.5
- **Compatibility range:** 242–263.* (2024.2 through 2026.3)
- **JVM target:** 21

### Why PhpStorm and why 2026.1.5?

- The IntelliJ IDEA Community (`IC`) distribution is **no longer published after 2025.2**, so the
  old `platformType=IC` cannot resolve modern versions. PhpStorm (`PS`) is the target IDE anyway.
- We compile against **2026.1.5** rather than 2026.2.x because 2026.1 is the last platform line
  that still targets **Java 21**. PhpStorm 2026.2 requires **Java 25**, and compiling against it
  would emit Java 25 bytecode, which older IDEs (running JBR 21) cannot load — that would force
  `pluginSinceBuild=262`.
- Compatibility with 2026.2.x and 2026.3.x is therefore established by the **Plugin Verifier** (`verifyPlugin`)
  instead of by compiling against it. The plugin only uses long-stable core platform APIs
  (`LexerBase`, `ParserDefinition`, `MultiplePsiFilesPerDocumentFileViewProvider`,
  `TemplateDataElementType`, `LayeredLexerEditorHighlighter`), so this holds well.

## Releasing

The IDE checks for updates in a custom plugin repository: `updatePlugins.xml` in the repo root,
served from `https://raw.githubusercontent.com/offsite-solutions/EisodosIdeaTemplatePlugin/main/updatePlugins.xml`.
Its download URL points to the GitHub Release asset of the current version.

When the user asks for a new version or release, Claude runs every step below end to end
without handing any of them back:

1. Bump `pluginVersion` in `gradle.properties` (ask which version if the user did not say)
2. `./gradlew buildPlugin updatePluginsXml`
3. Commit and push (including `updatePlugins.xml`)
4. `gh release create v<version> build/distributions/eisodos-template-support-<version>.zip`
5. Verify that the raw `updatePlugins.xml` URL serves the new version and the release asset URL
   answers 200, then report both to the user

## Version Bumping

Update version in `gradle.properties`:
```properties
pluginVersion=1.1.0
```

To support newer IDE versions, raise the upper bound and re-run the verifier:
```properties
pluginUntilBuild=263.*
pluginVerifierIdeVersions=2024.2,2026.3
```

Only raise `platformVersion` / `pluginSinceBuild` when you actually need newer APIs — note that
moving to a 2026.2+ platform also forces the JVM target to 25 and `pluginSinceBuild=262`.

## Code Conventions

- All plugin code is Kotlin
- Package root: `com.eisodos.intellij`
- Token types are defined as `@JvmField val` constants in `EisodosTemplateTokenTypes`
- Composite element types use `EisodosTemplateCompositeElementType`
- The lexer extends `LexerBase` (not JFlex) for easier maintenance
- No external dependencies beyond the IntelliJ Platform SDK
- Look up HTML by ID (`Language.findLanguageByID("HTML")`), never via `HTMLLanguage` or `HtmlFileType`: since 2026.3 these live in XML plugin content modules, and direct references fail the Plugin Verifier
