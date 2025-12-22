package cc.unitmesh.nanodsl.language.sketch

import cc.unitmesh.devti.sketch.ui.ExtensionLangSketch
import cc.unitmesh.devti.sketch.ui.LanguageSketchProvider
import com.intellij.openapi.project.Project

/**
 * Provider for NanoDSL language sketch.
 * 
 * Registers NanoDSL as a supported language for the SketchToolWindow,
 * allowing NanoDSL code blocks to be rendered with proper syntax highlighting.
 */
class NanoDSLSketchProvider : LanguageSketchProvider {
    
    override fun isSupported(lang: String): Boolean {
        val normalizedLang = lang.lowercase()
        return normalizedLang == "nanodsl" || normalizedLang == "nano"
    }
    
    override fun create(project: Project, content: String): ExtensionLangSketch {
        return NanoDSLLangSketch(project, content)
    }
}
