package pk.myhisaab.sdk.internal.camera

import java.io.File

/**
 * One-off events emitted by [CameraViewModel] via a `SharedFlow` (replay = 0),
 * so they fire exactly once and are not re-delivered on configuration change.
 */
internal sealed interface CameraEvent {
    /** A photo was captured and saved to [file] (a JPEG in the app's external cache). */
    data class ImageReady(val file: File) : CameraEvent

    /** Capture failed; [message] is safe to surface to the user. */
    data class Error(val message: String) : CameraEvent
}
