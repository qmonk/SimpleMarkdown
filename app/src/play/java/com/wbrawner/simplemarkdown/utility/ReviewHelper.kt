package com.wbrawner.simplemarkdown.utility

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.wbrawner.simplemarkdown.view.activity.MainActivity

private const val KEY_TIME_IN_APP = "timeInApp"

// Prompt user to review after they've used the app for 30 minutes
private const val TIME_TO_PROMPT = 1000L * 60 * 30

/**
 * A simple attempt at a non-intrusive way of prompting long-time users of the app to leave a
 * review. It works by registering itself as one of the [Application.ActivityLifecycleCallbacks] to
 * keep track of the current activity, which is needed to launch the review flow, along with the
 * time the activity is started to estimate how long the user has been active.
 */
object ReviewHelper : Application.ActivityLifecycleCallbacks {
    private lateinit var application: Application
    private lateinit var reviewManager: ReviewManager
    private lateinit var sharedPreferences: SharedPreferences
    private var currentActivity: Activity? = null
    private var activityCount = 0
        set(value) {
            field = if (value < 0) {
                0
            } else {
                value
            }
        }
    private var activeTime = 0L

    fun init(
            application: Application,
            sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application),
            reviewManager: ReviewManager = ReviewManagerFactory.create(application)
    ) {
        this.application = application
        this.sharedPreferences = sharedPreferences
        this.reviewManager = reviewManager
        if (sharedPreferences.getLong(KEY_TIME_IN_APP, 0L) == -1L) {
            // We've already prompted the user for the review so let's not be annoying about it
            Log.i("ReviewHelper", "User already prompted for review, not configuring ReviewHelper")
            return
        }
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No op
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        if (activityCount++ == 0) {
            activeTime = SystemClock.elapsedRealtime()
        }
        if (activity !is MainActivity || sharedPreferences.getLong(KEY_TIME_IN_APP, 0L) < TIME_TO_PROMPT) {
            // Not ready to prompt just yet
            Log.v("ReviewHelper", "Not ready to prompt user for review yet")
            return
        }
        Log.v("ReviewHelper", "Prompting user for review")
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                val exception = request.exception
                        ?: RuntimeException("Failed to request review")
                Log.e("ReviewHelper", "Failed to prompt user for review", exception)
                return@addOnCompleteListener
            }

            reviewManager.launchReviewFlow(activity, request.result).addOnCompleteListener { _ ->
                // According to the docs, this may or may not have actually been shown. Either
                // way, it's not a critical piece of functionality for the app so I'm not
                // worried about it failing silently. Link for reference:
                // https://developer.android.com/guide/playcore/in-app-review/kotlin-java#launch-review-flow
                Log.v("ReviewHelper", "User finished review, ending activity watch")
                application.unregisterActivityLifecycleCallbacks(this)
                sharedPreferences.edit {
                    putLong(KEY_TIME_IN_APP, -1L)
                }
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // No op
    }

    override fun onActivityPaused(activity: Activity) {
        // No op
    }

    override fun onActivityStopped(activity: Activity) {
        currentActivity = null
        if (--activityCount == 0) {
            sharedPreferences.edit {
                putLong(KEY_TIME_IN_APP, SystemClock.elapsedRealtime() - activeTime)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No op
    }
}