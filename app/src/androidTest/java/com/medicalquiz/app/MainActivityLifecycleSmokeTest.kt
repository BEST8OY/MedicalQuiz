package com.medicalquiz.app

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke lifecycle coverage for activity recreation.
 *
 * Detailed flow/state restoration scenarios are tracked in the lifecycle remediation plan.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleSmokeTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun recreateActivity_doesNotCrash() {
        scenarioRule.scenario.recreate()
        scenarioRule.scenario.onActivity { activity ->
            check(!activity.isFinishing)
        }
    }

    @Test
    fun recreateActivityTwice_doesNotCrash() {
        scenarioRule.scenario.recreate()
        scenarioRule.scenario.recreate()
        scenarioRule.scenario.onActivity { activity ->
            check(!activity.isDestroyed)
        }
    }
}
