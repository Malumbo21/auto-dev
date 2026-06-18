package cc.unitmesh.devti.language

import cc.unitmesh.devti.gui.chat.ui.createDevInInputDocument
import cc.unitmesh.devti.language.psi.DevInFile
import cc.unitmesh.devti.language.psi.DevInTypes
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class DevInCompletionContributorTest: LightJavaCodeInsightFixtureTestCase() {
    fun testCommandCompletion() {
        myFixture.loadNewFile("CodeComplete.devin", "")
        editor.caretModel.moveToOffset(1)
        myFixture.type("/")
        myFixture.completeBasic()

        val lookupElements: List<String> = myFixture.lookupElements?.map { it.lookupString } ?: emptyList()

        assertTrue(lookupElements.contains("rev"))
        assertTrue(lookupElements.contains("write"))
        assertTrue(lookupElements.contains("file"))
        assertTrue(lookupElements.contains("edit_file"))
    }

    fun testChatInputDocumentUsesDevInPsiFile() {
        val inputDocument = createDevInInputDocument(project) ?: error("DevIn input document should be created")

        WriteCommandAction.runWriteCommandAction(project) {
            inputDocument.insertString(0, "/")
        }

        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitDocument(inputDocument)

        val psiFile = psiDocumentManager.getPsiFile(inputDocument)
        assertTrue(psiFile is DevInFile)
        assertEquals(DevInTypes.COMMAND_START, psiFile?.findElementAt(0)?.node?.elementType)
    }
}

fun CodeInsightTestFixture.loadNewFile(path: String, contents: String): PsiFile {
    val virtualFile = VfsTestUtil.createFile(project.guessProjectDir()!!, path, contents)
    configureFromExistingVirtualFile(virtualFile)
    return file
}
