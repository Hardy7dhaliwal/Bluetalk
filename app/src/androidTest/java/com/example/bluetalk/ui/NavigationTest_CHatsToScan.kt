package com.example.bluetalk.ui


import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.example.bluetalk.R
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.IsInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavigationTest_CHatsToScan {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Rule
    @JvmField
    var mGrantPermissionRule =
            GrantPermissionRule.grant(
                    "android.permission.BLUETOOTH_ADVERTISE",
"android.permission.ACCESS_FINE_LOCATION",
"android.permission.BLUETOOTH_SCAN",
"android.permission.ACCESS_COARSE_LOCATION",
"android.permission.BLUETOOTH_CONNECT",
"android.permission.RECORD_AUDIO")

    @Test
    fun navigationTest_CHatsToScan() {

    val bottomNavigationItemView = onView(
        allOf(withId(R.id.UsersScanFragment), withContentDescription("Users"),
        childAtPosition(
        childAtPosition(
        withId(R.id.bottom_navigation),
        0),
        0),
        isDisplayed()))
            bottomNavigationItemView.perform(click())

            val textView = onView(
        allOf(withId(R.id.toolbarTitle), withText("Active Users"),
        withParent(allOf(withId(R.id.toolbar),
        withParent(IsInstanceOf.instanceOf(android.widget.LinearLayout::class.java)))),
        isDisplayed()))
        textView.check(matches(withText("Active Users")))
         }

    private fun childAtPosition(
            parentMatcher: Matcher<View>, position: Int): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }
}
