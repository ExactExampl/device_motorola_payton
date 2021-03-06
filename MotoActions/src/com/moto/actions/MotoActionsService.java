/*
 * Copyright (c) 2015 The CyanogenMod Project
 * Copyright (c) 2017 The LineageOS Project
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

package com.moto.actions;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.util.List;
import java.util.LinkedList;

import com.moto.actions.actions.UpdatedStateNotifier;
import com.moto.actions.actions.CameraActivationSensor;
import com.moto.actions.actions.ChopChopSensor;
import com.moto.actions.actions.FlipToMute;
import com.moto.actions.actions.LiftToSilence;
import com.moto.actions.actions.ProximitySilencer;

import com.moto.actions.doze.DozePulseAction;
import com.moto.actions.doze.StowSensor;
import com.moto.actions.doze.FlatUpSensor;
import com.moto.actions.doze.ScreenReceiver;
import com.moto.actions.doze.ScreenStateNotifier;

public class MotoActionsService extends IntentService implements ScreenStateNotifier,
        UpdatedStateNotifier {
    private static final String TAG = "MotoActions";

    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;

    private final List<ScreenStateNotifier> mScreenStateNotifiers = new LinkedList<>();
    private final List<UpdatedStateNotifier> mUpdatedStateNotifiers = new LinkedList<>();

    public MotoActionsService(Context context) {
        super("MotoActionService");

        Log.d(TAG, "Starting");

        MotoActionsSettings motoActionsSettings = new MotoActionsSettings(context, this);
        SensorHelper sensorHelper = new SensorHelper(context);
        new ScreenReceiver(context, this);

        DozePulseAction mDozePulseAction = new DozePulseAction(context);
        mScreenStateNotifiers.add(mDozePulseAction);

        // Actionable sensors get screen on/off notifications
        mScreenStateNotifiers.add(new StowSensor(motoActionsSettings, sensorHelper, mDozePulseAction));
        mScreenStateNotifiers.add(new FlatUpSensor(motoActionsSettings, sensorHelper, mDozePulseAction));
        

        // Other actions that are always enabled
        mUpdatedStateNotifiers.add(new CameraActivationSensor(motoActionsSettings, sensorHelper));
        mUpdatedStateNotifiers.add(new ChopChopSensor(motoActionsSettings, sensorHelper));
        mUpdatedStateNotifiers.add(new ProximitySilencer(motoActionsSettings, context, sensorHelper));
        mUpdatedStateNotifiers.add(new FlipToMute(motoActionsSettings, context, sensorHelper));
        mUpdatedStateNotifiers.add(new LiftToSilence(motoActionsSettings, context, sensorHelper));

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        String tag = context.getPackageName() + ":ServiceWakeLock";
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        updateState();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    @Override
    public void screenTurnedOn() {
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOn();
        }
    }

    @Override
    public void screenTurnedOff() {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOff();
        }
    }

    public void updateState() {
        if (mPowerManager.isInteractive()) {
            screenTurnedOn();
        } else {
            screenTurnedOff();
        }
        for (UpdatedStateNotifier notifier : mUpdatedStateNotifiers) {
            notifier.updateState();
        }
    }
}
