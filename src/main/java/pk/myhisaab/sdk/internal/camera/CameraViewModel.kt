package pk.myhisaab.sdk.internal.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the camera overlay's state and the capture flow.
 *
 * No dependency injection: this is a plain [ViewModel] created by the default
 * factory (`viewModel()`), so the SDK does not force Hilt on the host app.
 * The Composable drives CameraX; captured [File]s are handed here, de-duped, and
 * forwarded as one-off events.
 */
internal class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // replay = 0 so a delivered photo is never re-emitted after rotation.
    private val _events = MutableSharedFlow<CameraEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    /** Called by the UI once CameraX has written a still. Guards against double-delivery. */
    fun onImageCaptured(imageFile: File) {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch { _events.emit(CameraEvent.ImageReady(imageFile)) }
    }

    /** Called when CameraX itself fails to capture (hardware/IO error). */
    fun onCaptureFailed(message: String) {
        _uiState.update { it.copy(isProcessing = false) }
        viewModelScope.launch { _events.emit(CameraEvent.Error(message)) }
    }

    /** Flip lenses. Ignored mid-capture to avoid rebinding races. */
    fun toggleLens() {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(useBackCamera = !it.useBackCamera) }
    }
}
