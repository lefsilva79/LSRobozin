package com.example.lsrobozin

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SetupPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2 // Alterado de 3 para 2 passos

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RestrictedSettingsStepFragment()  // Agora este é o primeiro passo
            1 -> AccessibilityStepFragment()       // Agora este é o segundo passo
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}