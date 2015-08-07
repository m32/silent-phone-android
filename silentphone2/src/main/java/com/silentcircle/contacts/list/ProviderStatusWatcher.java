/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.silentcircle.contacts.list;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.google.common.collect.Lists;
import com.silentcircle.silentcontacts2.ScContactsContract.ProviderStatus;

import java.util.ArrayList;

/**
 * A singleton that keeps track of the last known provider status.
 *
 * All methods must be called on the UI thread unless noted otherwise.
 *
 * All members must be set on the UI thread unless noted otherwise.
 */
public class ProviderStatusWatcher extends ContentObserver {
    private static final String TAG = "ProviderStatusWatcher";
    private static final boolean DEBUG = false;

    /**
     * Callback interface invoked when the provider status changes.
     */
    public interface ProviderStatusListener {
        public void onProviderStatusChange();
    }

    public static class Status {
        /** See {@link com.silentcircle.silentcontacts.ScContactsContract.ProviderStatus#STATUS} */
        public final int status;

        /** See {@link com.silentcircle.silentcontacts.ScContactsContract.ProviderStatus#DATA1} */
        public final String data;

        public Status(int status, String data) {
            this.status = status;
            this.data = data;
        }
    }

    private static final String[] PROJECTION = new String[] {
        ProviderStatus.STATUS,
        ProviderStatus.DATA1
    };

    /**
     * We'll wait for this amount of time on the UI thread if the load hasn't finished.
     */
    private static final int LOAD_WAIT_TIMEOUT_MS = 1000;

    private static ProviderStatusWatcher sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler();

    private final Object mSignal = new Object();

    private int mStartRequestedCount;

    private LoaderTask mLoaderTask;

    /** Last known provider status.  This can be changed on a worker thread. */
    private Status mProviderStatus;

    private final ArrayList<ProviderStatusListener> mListeners = Lists.newArrayList();

    private final Runnable mStartLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            startLoading();
        }
    };

    /**
     * Returns the singleton instance.
     */
    public synchronized static ProviderStatusWatcher getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ProviderStatusWatcher(context);
        }
        return sInstance;
    }

    private ProviderStatusWatcher(Context context) {
        super(null);
        mContext = context;
    }

    /** Add a listener. */
    public void addListener(ProviderStatusListener listener) {
        mListeners.add(listener);
    }

    /** Remove a listener */
    public void removeListener(ProviderStatusListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        if (DEBUG) {
            Log.d(TAG, "notifyListeners: " + mListeners.size());
        }
        if (isStarted()) {
            for (ProviderStatusListener listener : mListeners) {
                listener.onProviderStatusChange();
            }
        }
    }

    private boolean isStarted() {
        return mStartRequestedCount > 0;
    }

    /**
     * Starts watching the provider status.  {@link #start()} and {@link #stop()} calls can be
     * nested.
     */
    public void start() {
        if (++mStartRequestedCount == 1) {
            mContext.getContentResolver().registerContentObserver(ProviderStatus.CONTENT_URI, false, this);
            startLoading();

            if (DEBUG) {
                Log.d(TAG, "Start observing");
            }
        }
    }

    /**
     * Stops watching the provider status.
     */
    public void stop() {
        if (!isStarted()) {
            Log.e(TAG, "Already stopped");
            return;
        }
        if (--mStartRequestedCount == 0) {

            mHandler.removeCallbacks(mStartLoadingRunnable);

            mContext.getContentResolver().unregisterContentObserver(this);
            if (DEBUG) {
                Log.d(TAG, "Stop observing");
            }
        }
    }

    /**
     * @return last known provider status.
     *
     * If this method is called when we haven't started the status query or the query is still in
     * progress, it will start a query in a worker thread if necessary, and *wait for the result*.
     *
     * This means this method is essentially a blocking {@link com.silentcircle.silentcontacts.ScContactsContract.ProviderStatus#CONTENT_URI} query.
     * This URI is not backed by the file system, so is usually fast enough to perform on the main
     * thread, but in extreme cases (when the system takes a while to bring up the contacts
     * provider?) this may still cause ANRs.
     *
     * In order to avoid that, if we can't load the status within {@link #LOAD_WAIT_TIMEOUT_MS},
     * we'll give up and just returns {@link com.silentcircle.silentcontacts.ScContactsContract.ProviderStatus#STATUS_UPGRADING} in order to unblock
     * the UI thread.  The actual result will be delivered later via {@link ProviderStatusListener}.
     * (If {@link com.silentcircle.silentcontacts.ScContactsContract.ProviderStatus#STATUS_UPGRADING} is returned, the app (should) shows an according
     * message, like "contacts are being updated".)
     */
    public Status getProviderStatus() {
        waitForLoaded();

        if (mProviderStatus == null) {
            return new Status(ProviderStatus.STATUS_UPGRADING, null);
        }

        return mProviderStatus;
    }

    private void waitForLoaded() {
        if (mProviderStatus == null) {
            if (mLoaderTask == null) {
                // For some reason the loader couldn't load the status.  Let's start it again.
                startLoading();
            }
            synchronized (mSignal) {
                try {
                    mSignal.wait(LOAD_WAIT_TIMEOUT_MS);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }

    private void startLoading() {
        if (mLoaderTask != null) {
            return; // Task already running.
        }

        if (DEBUG) {
            Log.d(TAG, "Start loading");
        }

        mLoaderTask = new LoaderTask();
        mLoaderTask.execute();
    }

    private class LoaderTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                Cursor cursor = mContext.getContentResolver().query(ProviderStatus.CONTENT_URI,
                        PROJECTION, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            // Note here we can't just say "Status", as AsyncTask has the "Status"
                            // enum too.
                            mProviderStatus = new ProviderStatusWatcher.Status(
                                    cursor.getInt(0), cursor.getString(1));
                            return true;
                        }
                    } finally {
                        cursor.close();
                    }
                }
                return false;
            } finally {
                synchronized (mSignal) {
                    mSignal.notifyAll();
                }
            }
        }

        @Override
        protected void onCancelled(Boolean result) {
            cleanUp();
        }

        @Override
        protected void onPostExecute(Boolean loaded) {
            cleanUp();
            if (loaded != null && loaded) {
                notifyListeners();
            }
        }

        private void cleanUp() {
            mLoaderTask = null;
        }
    }

    /**
     * Called when provider status may have changed.
     *
     * This method will be called on a worker thread by the framework.
     */
    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (!ProviderStatus.CONTENT_URI.equals(uri)) return;

        // Provider status change is rare, so okay to log.
        if (DEBUG) Log.i(TAG, "Provider status changed.");

        mHandler.removeCallbacks(mStartLoadingRunnable); // Remove one in the queue, if any.
        mHandler.post(mStartLoadingRunnable);
    }

    /**
     * Sends a provider status update, which will trigger a retry of database upgrade
     */
    public static void retryUpgrade(final Context context) {
        Log.i(TAG, "retryUpgrade");
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
                context.getContentResolver().update(ProviderStatus.CONTENT_URI, values,
                        null, null);
                return null;
            }
        };
        task.execute();
    }
}
