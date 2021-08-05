/*
 * Copyright (C) 2021 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.internal.gmscompat;

import android.app.Application;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;

/** @hide */
public final class AttestationHooks {
    private static final String TAG = "GmsCompat/Attestation";

    private static final boolean PRODUCT_NEEDS_MODEL_EDIT =
            SystemProperties.getBoolean("ro.product.needs_model_edit", false);
    private static final String PRODUCT_STOCK_FINGERPRINT =
            SystemProperties.get("ro.build.stock_fingerprint");

    private AttestationHooks() { }

    private static void setBuildField(String packageName, String key, String value) {
        /*
         * This would be much prettier if we just removed "final" from the Build fields,
         * but that requires changing the API.
         *
         * While this an awful hack, it's technically safe because the fields are
         * populated at runtime.
         */
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key + " for " + packageName, e);
        }
    }

    private static void maybeSpoofBuild(String packageName, boolean isGms) {
        if (isGms) {
            // Set fingerprint to make SafetyNet pass
            if (PRODUCT_STOCK_FINGERPRINT.length() > 0) {
                setBuildField(packageName, "FINGERPRINT", PRODUCT_STOCK_FINGERPRINT);
            }
        } else if (PRODUCT_NEEDS_MODEL_EDIT &&
                "com.google.android.googlequicksearchbox".equals(packageName)) {
            // Set device model to defy NGA in Google Assistant
            setBuildField(packageName, "MODEL", "Pixel 3 XL");
        }
    }

    public static void initApplicationBeforeOnCreate(Application app) {
        // Spoof Build properties
        String packageName = app.getPackageName();
        boolean isGms = GmsInfo.PACKAGE_GMS.equals(packageName);
        maybeSpoofBuild(packageName, isGms);
    }
}
