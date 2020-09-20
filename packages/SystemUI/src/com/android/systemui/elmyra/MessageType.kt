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

enum class MessageType(val id: Int) {
    RECOGNIZER_START(200),
    RECOGNIZER_STOP(201),
    SENSITIVITY_UPDATE(202),
    SNAPSHOT_REQUEST(203),
    CHASSIS_REQUEST(204),
    GRAB_RECOGNIZER_START(205),
    GRAB_RECOGNIZER_STOP(206),
    GESTURE_PROGRESS(300),
    GESTURE_DETECTED(301),
    SNAPSHOT_RESPONSE(302),
    CHASSIS_RESPONSE(303),
    GRAB_DETECTED(304),
    GRAB_RELEASED(305),
}
