package cc.unitmesh.viewer.web

/**
 * JVM implementation: Load viewer HTML from resources
 */
actual fun getViewerHtml(): String {
    return try {
        val resource = object {}.javaClass.classLoader?.getResource("viewer.html")
        
        if (resource != null) {
            val html = resource.readText()
            html
        } else {
            getFallbackHtml()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        getFallbackHtml()
    }
}

private fun getFallbackHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Viewer</title>
    <style>
        body { margin: 0; padding: 20px; font-family: sans-serif; }
        pre { white-space: pre-wrap; word-wrap: break-word; }
    </style>
</head>
<body>
    <div id="content">Loading...</div>
    <script>
        window.showContent = function(jsonStr) {
            try {
                const request = JSON.parse(jsonStr);
                const contentDiv = document.getElementById('content');
                contentDiv.innerHTML = '<h3>' + request.type + '</h3><pre>' + 
                    escapeHtml(request.content) + '</pre>';
            } catch (e) {
                console.error('Error parsing content:', e);
            }
        };
        
        window.clearContent = function() {
            document.getElementById('content').innerHTML = '';
        };
        
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    </script>
</body>
</html>
""".trimIndent()
