package org.xwiki.android.sync.auth

import android.accounts.AccountManager
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xwiki.android.sync.ACCOUNT_TYPE
import org.xwiki.android.sync.AUTHTOKEN_TYPE_FULL_ACCESS
import org.xwiki.android.sync.R
import org.xwiki.android.sync.appContext

private const val TEST_USERNAME = "aa700"

private const val TEST_PASSWORD = "a7890"

@RunWith(AndroidJUnit4::class)
@MediumTest
class test{
    @Rule
    @JvmField
    val rule = ActivityTestRule(AuthenticatorActivity::class.java,true,false)

    @Test
    fun testSignIn()
    {
        val i = Intent(appContext, AuthenticatorActivity::class.java)
        val authTokenType = AUTHTOKEN_TYPE_FULL_ACCESS + "android.uid.system"
        i.putExtra(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE)
        i.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType)
        i.putExtra(IS_SETTING_SYNC_TYPE, false)
        i.putExtra("Test", true)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        rule.launchActivity(i)
        onView(withId(R.id.btViewSignInFlipper)).perform(click())
        onView(withId(R.id.accountName))
            .perform(typeText(TEST_USERNAME))      // Test user, for log in
        onView(withId(R.id.accountPassword))
            .perform(typeText(TEST_PASSWORD), closeSoftKeyboard())
        onView(withId(R.id.signInButton)).perform(click())
    }
}