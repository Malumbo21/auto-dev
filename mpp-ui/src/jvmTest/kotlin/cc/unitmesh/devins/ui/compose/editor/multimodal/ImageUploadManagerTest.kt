package cc.unitmesh.devins.ui.compose.editor.multimodal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ImageUploadManagerTest {

    @Test
    fun `addImageAndUpload should add image with PENDING status`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val manager = ImageUploadManager(scope = testScope, uploadCallback = null)

        val image = AttachedImage(
            id = "test-1",
            name = "test.png",
            path = "/path/to/test.png"
        )

        manager.addImageAndUpload(image)

        val state = manager.getState()
        assertEquals(1, state.images.size)
        assertEquals(ImageUploadStatus.PENDING, state.images[0].uploadStatus)
    }

    @Test
    fun `upload should transition to COMPLETED on success`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val uploadCallback: suspend (String, String, (Int) -> Unit) -> ImageUploadResult = { _, _, onProgress ->
            onProgress(50)
            onProgress(100)
            ImageUploadResult(
                success = true,
                url = "https://example.com/uploaded.jpg",
                originalSize = 1000,
                compressedSize = 500
            )
        }

        val manager = ImageUploadManager(scope = testScope, uploadCallback = uploadCallback)

        val image = AttachedImage(
            id = "test-1",
            name = "test.png",
            path = "/path/to/test.png"
        )

        manager.addImageAndUpload(image)
        testScope.advanceUntilIdle()

        val state = manager.getState()
        assertEquals(1, state.images.size)
        assertEquals(ImageUploadStatus.COMPLETED, state.images[0].uploadStatus)
        assertEquals("https://example.com/uploaded.jpg", state.images[0].uploadedUrl)
        assertTrue(state.allImagesUploaded)
        assertTrue(state.canSend)
    }

    @Test
    fun `upload should transition to FAILED on error`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val uploadCallback: suspend (String, String, (Int) -> Unit) -> ImageUploadResult = { _, _, _ ->
            ImageUploadResult(
                success = false,
                error = "Network error"
            )
        }

        val manager = ImageUploadManager(scope = testScope, uploadCallback = uploadCallback)

        val image = AttachedImage(
            id = "test-1",
            name = "test.png",
            path = "/path/to/test.png"
        )

        manager.addImageAndUpload(image)
        testScope.advanceUntilIdle()

        val state = manager.getState()
        assertEquals(1, state.images.size)
        assertEquals(ImageUploadStatus.FAILED, state.images[0].uploadStatus)
        assertEquals("Network error", state.images[0].uploadError)
        assertTrue(state.hasUploadError)
        assertFalse(state.canSend)
    }

    @Test
    fun `late progress updates should not overwrite COMPLETED status`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        var progressCallback: ((Int) -> Unit)? = null

        val uploadCallback: suspend (String, String, (Int) -> Unit) -> ImageUploadResult = { _, _, onProgress ->
            progressCallback = onProgress
            onProgress(50)
            // Return success immediately
            ImageUploadResult(
                success = true,
                url = "https://example.com/uploaded.jpg",
                originalSize = 1000,
                compressedSize = 500
            )
        }

        val manager = ImageUploadManager(scope = testScope, uploadCallback = uploadCallback)

        val image = AttachedImage(
            id = "test-1",
            name = "test.png",
            path = "/path/to/test.png"
        )

        manager.addImageAndUpload(image)
        testScope.advanceUntilIdle()

        // Verify COMPLETED
        assertEquals(ImageUploadStatus.COMPLETED, manager.getState().images[0].uploadStatus)

        // Simulate late progress callback (this should be ignored)
        progressCallback?.invoke(75)
        testScope.advanceUntilIdle()

        // Status should still be COMPLETED
        val state = manager.getState()
        assertEquals(ImageUploadStatus.COMPLETED, state.images[0].uploadStatus)
        assertEquals("https://example.com/uploaded.jpg", state.images[0].uploadedUrl)
    }

    @Test
    fun `removeImage should remove image from state`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val manager = ImageUploadManager(scope = testScope, uploadCallback = null)

        val image = AttachedImage(id = "test-1", name = "test.png", path = "/path/to/test.png")
        manager.addImageAndUpload(image)

        assertEquals(1, manager.getState().images.size)

        manager.removeImage("test-1")

        assertEquals(0, manager.getState().images.size)
    }

    @Test
    fun `clearImages should remove all images`() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val manager = ImageUploadManager(scope = testScope, uploadCallback = null)

        manager.addImageAndUpload(AttachedImage(id = "test-1", name = "test1.png", path = "/path/1"))
        manager.addImageAndUpload(AttachedImage(id = "test-2", name = "test2.png", path = "/path/2"))

        assertEquals(2, manager.getState().images.size)

        manager.clearImages()

        assertEquals(0, manager.getState().images.size)
    }
}

