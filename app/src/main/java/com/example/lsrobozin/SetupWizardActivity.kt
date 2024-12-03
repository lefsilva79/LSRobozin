package com.example.lsrobozin

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2

class SetupWizardActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        viewPager = findViewById(R.id.setupViewPager)
        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)

        // Configurar ViewPager
        viewPager.adapter = SetupPagerAdapter(this)
        viewPager.isUserInputEnabled = false // Desabilita o swipe

        updateNavigationButtons()

        btnBack.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem = viewPager.currentItem - 1
            }
        }

        btnNext.setOnClickListener {
            if (viewPager.currentItem < 1) { // Alterado de 2 para 1 pois agora são 2 passos
                viewPager.currentItem = viewPager.currentItem + 1
            } else {
                // Último passo completado
                finish()
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigationButtons()
            }
        })
    }

    private fun updateNavigationButtons() {
        btnBack.isVisible = viewPager.currentItem > 0
        btnNext.text = if (viewPager.currentItem == 1) "Finish" else "Continue" // Alterado de 2 para 1
    }
}