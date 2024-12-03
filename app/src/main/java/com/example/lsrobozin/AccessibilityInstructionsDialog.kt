package com.example.lsrobozin.com.example.lsrobozin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.lsrobozin.R

class AccessibilityInstructionsDialog : DialogFragment() {

    interface InstructionsDialogListener {
        fun onInstructionsUnderstood()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_accessibility_instructions, null)

        val dialog = AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle("Enable Accessibility Service")
            .setView(view)
            .setPositiveButton("Continue") { _, _ ->
                (activity as? InstructionsDialogListener)?.onInstructionsUnderstood()
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Ajusta o tamanho do diálogo
        dialog.setOnShowListener {
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, -2) // -2 é wrap_content
        }

        return dialog
    }
}