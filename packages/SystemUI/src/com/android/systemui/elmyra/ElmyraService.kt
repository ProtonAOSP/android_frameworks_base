/*
 * Copyright (C) 2020 The Proton AOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.elmyra

import android.content.Context
import android.hardware.location.ContextHubClient
import android.hardware.location.ContextHubClientCallback
import android.hardware.location.ContextHubManager
import android.hardware.location.NanoAppMessage
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import com.android.internal.util.ScreenshotHelper
import com.android.systemui.elmyra.proto.nano.ChassisProtos
import com.android.systemui.elmyra.proto.nano.ContextHubMessages
import com.android.systemui.elmyra.proto.nano.SnapshotProtos
import com.android.systemui.VendorServices
import com.google.protobuf.nano.MessageNano

private const val ELMYRA_NANOAPP_ID = 0x476f6f676c00100eL
private const val TAG = "Elmyra/SysUI"

class ElmyraService(val context: Context) : VendorServices(context) {
    // Services
    private lateinit var client: ContextHubClient
    private val vibrator = context.getSystemService("vibrator") as Vibrator
    private val screenshotHelper = ScreenshotHelper(context)
    private val handler = Handler(Looper.getMainLooper())

    // Vibration effects
    private val vibClick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    private val vibHeavyClick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    // Gesture state
    private var inGesture = false
    private var lastProgressTime = 0L

    // Settings
    var sensitivity = 0.5f
        set(value) {
            field = value
            updateSensitivity()
        }

    private val chreCallback = object : ContextHubClientCallback() {
        override fun onMessageFromNanoApp(client: ContextHubClient, msg: NanoAppMessage) {
            // Ignore other nanoapps
            if (msg.nanoAppId != ELMYRA_NANOAPP_ID) {
                return
            }

            when (msg.messageType) {
                MessageType.GESTURE_DETECTED.id -> {
                    val detectedMsg = ContextHubMessages.GestureDetected.parseFrom(msg.messageBody)
                    onGestureDetected(detectedMsg)
                }
                MessageType.GESTURE_PROGRESS.id -> {
                    val progressMsg = ContextHubMessages.GestureProgress.parseFrom(msg.messageBody)
                    onGestureProgress(progressMsg)
                }

                // These are harmless debugging messages for dumping info from the
                // CHRE nanoapp. We should never receive them because we never request
                // them, so print the message with a warning if we receive one.
                MessageType.SNAPSHOT_RESPONSE.id -> {
                    val snapshotMsg = SnapshotProtos.Snapshot.parseFrom(msg.messageBody)
                    Log.w(TAG, "Received unsolicited snapshot response $snapshotMsg")
                }
                MessageType.CHASSIS_RESPONSE.id -> {
                    val chassisMsg = ChassisProtos.Chassis.parseFrom(msg.messageBody)
                    Log.w(TAG, "Received unsolicited chassis response $chassisMsg")
                }

                // Fallback for other unexpected messages
                else -> Log.w(TAG, "Received unknown message of type ${msg.messageType}: $msg")
            }
        }

        override fun onNanoAppAborted(client: ContextHubClient, nanoappId: Long, error: Int) {
            if (nanoappId == ELMYRA_NANOAPP_ID) {
                Log.e(TAG, "Elmyra CHRE nanoapp aborted: $error")
            }
        }
    }

    override fun start() {
        Log.i(TAG, "Initializing CHRE gesture")

        val manager = context.getSystemService("contexthub") as ContextHubManager
        client = manager.createClient(manager.contextHubs[0], chreCallback)

        enableGesture()
    }

    fun enableGesture() {
        val msg = ContextHubMessages.RecognizerStart()
        msg.progressReportThreshold = 0.5f
        msg.sensitivity = sensitivity

        sendNanoappMsg(MessageType.RECOGNIZER_START.id, MessageNano.toByteArray(msg))
    }

    fun disableGesture() {
        sendNanoappMsg(MessageType.RECOGNIZER_STOP.id, ByteArray(0))
    }

    fun onGestureDetected(msg: ContextHubMessages.GestureDetected) {
        Log.i(TAG, "Gesture detected hostSuspended=${msg.hostSuspended} hapticConsumed=${msg.hapticConsumed}")

        takeScreenshot()
        vibrator.vibrate(vibHeavyClick)
        inGesture = false
    }

    fun onGestureProgress(msg: ContextHubMessages.GestureProgress) {
        // Ignore beginning and end points
        if (msg.progress < 0.5f || msg.progress > 0.99f) {
            inGesture = false
        } else if (!inGesture) {
            // Enter gesture and vibrate to indicate that
            inGesture = true
            vibrator.vibrate(vibClick)
        }
    }

    fun takeScreenshot() {
        screenshotHelper.takeScreenshot(WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                true, true, WindowManager.ScreenshotSource.SCREENSHOT_OTHER,
                handler, null)
    }

    private fun updateSensitivity() {
        val msg = ContextHubMessages.SensitivityUpdate()
        msg.sensitivity = sensitivity
        sendNanoappMsg(MessageType.SENSITIVITY_UPDATE.id, MessageNano.toByteArray(msg))
    }

    private fun sendNanoappMsg(msgType: Int, bytes: ByteArray) {
        val message = NanoAppMessage.createMessageToNanoApp(ELMYRA_NANOAPP_ID, msgType, bytes)
        val ret = client.sendMessageToNanoApp(message)
        if (ret != 0) {
            Log.e(TAG, "Failed to send message of type $msgType to nanoapp: $ret")
        }
    }
}
