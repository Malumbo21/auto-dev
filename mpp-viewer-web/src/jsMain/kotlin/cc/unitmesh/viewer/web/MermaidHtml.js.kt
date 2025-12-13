package cc.unitmesh.viewer.web

/**
 * JS implementation: Return empty/stub Mermaid HTML
 */
actual fun getMermaidHtml(): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Mermaid (Stub)</title>
</head>
<body>
    <div id="mermaid-container">Mermaid not supported in JS</div>
</body>
</html>
""".trimIndent()
}
