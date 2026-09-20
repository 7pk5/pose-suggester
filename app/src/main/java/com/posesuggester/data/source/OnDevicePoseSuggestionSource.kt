package com.posesuggester.data.source

import android.graphics.Bitmap
import com.posesuggester.domain.PoseSuggestionSource
import com.posesuggester.domain.model.PoseSuggestion
import com.posesuggester.domain.model.SceneState

/**
 * On-device VLM path — **stub, and deliberately unavailable**.
 *
 * Intended to be backed by the MediaPipe LLM Inference API or a llama.cpp JNI binding (the
 * architecture diagram names Nano-Flash and SmolVLM2 as candidates). No model is bundled or downloaded, so
 * [isAvailable] reports false and the repository falls through to [TemplatePoseSuggestionSource]
 * rather than this returning something invented.
 *
 * When a real model lands, have it *choose a template* rather than emit raw coordinates — small
 * VLMs are poor at coordinate regression but good at picking from a labelled set.
 */
class OnDevicePoseSuggestionSource : PoseSuggestionSource {

    override val name: String = "OnDeviceVLM"

    /** No model is loaded, so this source cannot answer. */
    override suspend fun isAvailable(): Boolean = false

    override suspend fun suggestPose(frame: Bitmap, scene: SceneState): PoseSuggestion =
        throw NotImplementedError("On-device VLM is not wired up yet")
}
