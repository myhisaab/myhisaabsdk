package pk.myhisaab.sdk.internal.camera

/**
 * Immutable UI state for the in-app camera overlay.
 *
 * @param isProcessing true while a captured frame is being delivered; the shutter
 *   is disabled and a spinner shown to prevent double-captures.
 * @param useBackCamera which lens is currently bound. Defaults to the back camera.
 */
internal data class CameraUiState(
    val isProcessing: Boolean = false,
    val useBackCamera: Boolean = true,
)
