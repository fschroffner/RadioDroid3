package com.github.fschroffner.radiodroid3.ui.player

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentManager
import com.github.fschroffner.radiodroid3.FragmentPlayerFull
import com.github.fschroffner.radiodroid3.FragmentPlayerSmall
import com.github.fschroffner.radiodroid3.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Manages player bottom sheet behavior, nested scrolling and coordination
 * between SmallPlayer and FullPlayer fragments.
 */
class PlayerBottomSheetManager(
    private val activity: AppCompatActivity,
    private val fragmentManager: FragmentManager,
    private val bottomSheetView: View,
    private val appBarLayout: AppBarLayout,
    private val containerViewId: Int
) {

    private val playerBottomSheet: BottomSheetBehavior<View> = BottomSheetBehavior.from(bottomSheetView)
    lateinit var smallPlayerFragment: FragmentPlayerSmall
        private set
    lateinit var fullPlayerFragment: FragmentPlayerFull
        private set

    fun setup() {
        var foundSmall = fragmentManager.findFragmentById(R.id.fragment_player_small) as? FragmentPlayerSmall
        var foundFull = fragmentManager.findFragmentById(R.id.fragment_player_full) as? FragmentPlayerFull

        if (foundSmall == null || foundFull == null) {
            foundSmall = FragmentPlayerSmall()
            foundFull = FragmentPlayerFull()

            val ft = fragmentManager.beginTransaction()
            ft.hide(foundFull)
            ft.replace(R.id.fragment_player_small, foundSmall)
            ft.replace(R.id.fragment_player_full, foundFull)
            ft.commit()
        }

        smallPlayerFragment = foundSmall
        fullPlayerFragment = foundFull

        smallPlayerFragment.setCallback(object : FragmentPlayerSmall.Callback {
            override fun onToggle() {
                toggleBottomSheetState()
            }
        })

        fullPlayerFragment.setTouchInterceptListener(object : FragmentPlayerFull.TouchInterceptListener {
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                bottomSheetView.parent.requestDisallowInterceptTouchEvent(disallow)
            }
        })

        val coordinatorLayoutParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val appBarLayoutBehavior = object : AppBarLayout.Behavior() {
            override fun onStartNestedScroll(
                parent: CoordinatorLayout,
                child: AppBarLayout,
                directTargetChild: View,
                target: View,
                nestedScrollAxes: Int,
                type: Int
            ): Boolean {
                return playerBottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        coordinatorLayoutParams.behavior = appBarLayoutBehavior

        playerBottomSheet.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            private var oldState = BottomSheetBehavior.STATE_COLLAPSED

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING && oldState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (fullPlayerFragment.isScrolled()) {
                        playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        return
                    }
                }

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (smallPlayerFragment.context == null) return
                    appBarLayout.setExpanded(false)
                    smallPlayerFragment.setRole(FragmentPlayerSmall.Role.HEADER)

                    fragmentManager.findFragmentById(containerViewId)?.let {
                        fragmentManager.beginTransaction().hide(it).commit()
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    appBarLayout.setExpanded(true)
                    smallPlayerFragment.setRole(FragmentPlayerSmall.Role.PLAYER)
                    fullPlayerFragment.resetScroll()

                    fragmentManager.beginTransaction().hide(fullPlayerFragment).commit()
                }

                if (oldState == BottomSheetBehavior.STATE_EXPANDED && newState != BottomSheetBehavior.STATE_EXPANDED) {
                    fragmentManager.findFragmentById(containerViewId)?.let {
                        fragmentManager.beginTransaction().show(it).commit()
                    }
                }

                if (oldState == BottomSheetBehavior.STATE_COLLAPSED && newState != oldState) {
                    fullPlayerFragment.init()
                    fragmentManager.beginTransaction().show(fullPlayerFragment).commit()
                }

                oldState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    fun toggleBottomSheetState() {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun collapse(): Boolean {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            return true
        }
        return false
    }

    fun isExpanded(): Boolean = playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED
}
