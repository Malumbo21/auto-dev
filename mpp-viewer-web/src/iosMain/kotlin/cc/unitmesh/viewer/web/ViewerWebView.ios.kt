package cc.unitmesh.viewer.web

/**
 * iOS implementation: Load viewer HTML from resources
 */
actual fun getViewerHtml(): String {
    return getFallbackHtml()
}

private fun getFallbackHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AutoDev Viewer (Fallback)</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
            background: #1e1e1e;
            color: #d4d4d4;
            overflow: hidden;
        }
        #container {
            width: 100vw;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
    </style>
</head>
<body>
    <div id="container">
        <div style="display: flex; align-items: center; justify-content: center; height: 100%;">
            <div style="text-align: center;">
                <h2>AutoDev Viewer</h2>
                <p>Failed to load viewer.html from resources</p>
            </div>
        </div>
    </div>
</body>
</html>
    """.trimIndent()
}
