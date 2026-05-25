/*
 *     This file is a part of WebsocketCAM (https://www.github.com/UmerCodez/WebsocketCAM)
 *     Copyright (C) 2026 Umer Farooq (umerfarooq2383@gmail.com)
 *
 *     WebsocketCAM is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     WebsocketCAM is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with WebsocketCAM. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package app.umerfarooq.websocketcam.ui.screens.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.umerfarooq.websocketcam.domain.model.RemoteCommand
import app.umerfarooq.websocketcam.domain.model.camera.CameraConfigDefaults
import app.umerfarooq.websocketcam.domain.model.camera.CameraFacing
import app.umerfarooq.websocketcam.domain.repository.CameraSettingsRepository
import app.umerfarooq.websocketcam.domain.service.ServerState
import app.umerfarooq.websocketcam.domain.service.WebsocketServerService
import app.umerfarooq.websocketcam.domain.util.BoundedServiceInstanceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject


@OptIn(ExperimentalCoroutinesApi::class)
@ExperimentalCamera2Interop
@HiltViewModel
class CameraScreenViewModel @Inject constructor(
    private val cameraSettingsRepository: CameraSettingsRepository,
    private val boundedServiceInstanceProvider: BoundedServiceInstanceProvider
) : ViewModel(), ImageAnalysis.Analyzer {
    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private var _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded

    private val isProcessing = AtomicBoolean(false)
    private val baos = ByteArrayOutputStream()

    private var mediaCodec: MediaCodec? = null
    private var encoderSurface: Surface? = null

    private var _websocketServerState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val websocketServerState: StateFlow<ServerState> = _websocketServerState

    private var _cameraContentScale = MutableStateFlow(CameraConfigDefaults.CAMERA_CONTENT_SCALE)
    val cameraContentScale = _cameraContentScale.asStateFlow()


    private var websocketServerService: WebsocketServerService? = null

    private val tag = CameraScreenViewModel::class.simpleName

    private lateinit var targetResolution: Size
    private lateinit var cameraPreviewUseCase : Preview
    private var videoEncoderUseCase : Preview? = null
    private lateinit var cameraSelector: CameraSelector
    private var quality: Int = 85
    private var rotateImage = false

    private var camera: Camera? = null

    init {
        Log.d(tag, "init(${hashCode()})")

        viewModelScope.launch {
            boundedServiceInstanceProvider.websocketServerService
                .filterNotNull()
                .flatMapLatest { websocketServerService ->
                    this@CameraScreenViewModel.websocketServerService = websocketServerService
                    
                    launch {
                        websocketServerService.remoteCommands.collect { command ->
                            handleRemoteCommand(command)
                        }
                    }

                    websocketServerService.serverState
                }
                .collect { serverState ->
                    _websocketServerState.value = serverState
                }

            }


        viewModelScope.launch {
            cameraSettingsRepository.cameraSettings.collect { cameraSettings ->
                _settingsLoaded.value = false

                rotateImage = cameraSettings.rotateImage
                _cameraContentScale.value = cameraSettings.cameraContentScale


                val isFpsRangeAvailableForSelectedCamera = !cameraSettingsRepository.getAvailableFPSRanges(cameraSettings.cameraFacing).isEmpty()


                // This fpsRange will get default value of (30,30) when selected camera don't have FPS ranges available.
                // This default value will never be used
                val fpsRange = when(cameraSettings.cameraFacing){
                    CameraFacing.FRONT -> cameraSettings.selectedFpsRangeFrontCam
                    CameraFacing.BACK -> cameraSettings.selectedFpsRangeBackCam
                }

                // TODO: add check that if available FPS range is empty then don't set specific range

                targetResolution = Size(cameraSettings.cameraResolution.width, cameraSettings.cameraResolution.height)
                quality = cameraSettings.quality


                cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(
                        when(cameraSettings.cameraFacing){
                            CameraFacing.FRONT -> CameraSelector.LENS_FACING_FRONT
                            CameraFacing.BACK -> CameraSelector.LENS_FACING_BACK
                        }
                    )
                    .build()


                @Suppress("DEPRECATION")
                cameraPreviewUseCase =  Preview.Builder()
                    .setTargetResolution(targetResolution)
                    .also {
                        if(isFpsRangeAvailableForSelectedCamera) {
                            val extender = Camera2Interop.Extender(it)
                            extender.setCaptureRequestOption(
                                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                Range(fpsRange.lower, fpsRange.upper)
                            )
                        }
                    }
                    .build().apply {
                        setSurfaceProvider { newSurfaceRequest ->
                            _surfaceRequest.update { newSurfaceRequest }
                        }
                    }

                prepareVideoEncoder()

                _settingsLoaded.value = true

            }
        }
    }



    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        
        // Use a list to bind multiple use cases
        val useCases = mutableListOf<androidx.camera.core.UseCase>(cameraPreviewUseCase)
        videoEncoderUseCase?.let { useCases.add(it) }

        camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, *useCases.toTypedArray()
        )

        // Start encoder if available
        startEncoder()

        // Cancellation signals we're done with the camera
        try { awaitCancellation() } finally {
            stopEncoder()
            processCameraProvider.unbindAll()
            camera = null
        }
    }

    private fun handleRemoteCommand(command: RemoteCommand) {
        val cameraControl = camera?.cameraControl ?: return
        when (command) {
            is RemoteCommand.Focus -> {
                val factory = SurfaceOrientedMeteringPointFactory(
                    targetResolution.width.toFloat(),
                    targetResolution.height.toFloat(),
                    cameraPreviewUseCase // Use preview use case for factory
                )
                val point = factory.createPoint(command.x, command.y)
                val action = FocusMeteringAction.Builder(point).build()
                cameraControl.startFocusAndMetering(action)
            }
            is RemoteCommand.Brightness -> {
                cameraControl.setExposureCompensationIndex(command.index)
            }
            is RemoteCommand.Zoom -> {
                cameraControl.setZoomRatio(command.ratio)
            }
            is RemoteCommand.Torch -> {
                cameraControl.enableTorch(command.enabled)
            }
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.close()
    }

    private fun prepareVideoEncoder() {
        try {
            stopEncoder() // Release previous encoder if any

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetResolution.width, targetResolution.height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 5000000) 
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            
            // ASYNC ENCODER CALLBACK
            mediaCodec?.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    // Surface input handles this automatically
                }

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    val buffer = codec.getOutputBuffer(index) ?: return
                    val data = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.get(data)
                    
                    // Broadcast H.264 data
                    websocketServerService?.broadcast(data)
                    codec.releaseOutputBuffer(index, false)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Log.e(tag, "Encoder Error: ${e.message}")
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    Log.d(tag, "Encoder Format Changed: $format")
                    // Send SPS/PPS with explicit start codes 00 00 00 01
                    val sps = format.getByteBuffer("csd-0")
                    val pps = format.getByteBuffer("csd-1")
                    if (sps != null && pps != null) {
                        val header = byteArrayOf(0, 0, 0, 1)
                        val config = ByteArray(header.size + sps.remaining() + header.size + pps.remaining())
                        var pos = 0
                        
                        System.arraycopy(header, 0, config, pos, header.size); pos += header.size
                        sps.get(config, pos, sps.remaining()); pos += sps.remaining()
                        System.arraycopy(header, 0, config, pos, header.size); pos += header.size
                        pps.get(config, pos, pps.remaining())
                        
                        websocketServerService?.broadcast(config)
                    }
                }
            })

            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = mediaCodec?.createInputSurface()
            
            videoEncoderUseCase = Preview.Builder()
                .setTargetResolution(targetResolution)
                .build().apply {
                    setSurfaceProvider(Dispatchers.IO.asExecutor()) { request ->
                        val surface = encoderSurface
                        if (surface != null && surface.isValid) {
                            request.provideSurface(surface, Dispatchers.IO.asExecutor()) {
                                // Surface result
                            }
                        } else {
                            request.willNotProvideSurface()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "Failed to prepare MediaCodec", e)
        }
    }

    private fun startEncoder() {
        try {
            mediaCodec?.start()
            Log.d(tag, "Encoder started (Async mode)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start codec", e)
        }
    }

    private fun stopEncoder() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            encoderSurface?.release()
            encoderSurface = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping encoder", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(tag, "onCleared(${hashCode()})")
    }

    private fun Bitmap.rotateBitmap(degrees: Float): Bitmap {
        if (degrees == 0f) return this

        val matrix = Matrix().apply {
            postRotate(degrees)
        }

        return Bitmap.createBitmap(
            this,
            0,
            0,
            this.width,
            this.height,
            matrix,
            true
        )
    }

}

