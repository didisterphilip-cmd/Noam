package com.noam.gate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noam.gate.databinding.ActivityMainBinding

/**
 * Setup screen, and the first-run walkthrough.
 *
 * On the first open it takes the user through the whole setup in order — pick
 * the apps, then one permission at a time, each with a plain explanation and a
 * button that lands on the exact settings page. Afterwards the same three cards
 * stay here as the place to change anything.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private var dialog: AlertDialog? = null

    /** True between sending the user to Accessibility settings and their return. */
    private var awaitingAccessibility = false

    /** The greyed-out-toggle explanation is only worth showing once. */
    private var restrictedHintShown = false

    private var pickerLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.serviceButton.setOnClickListener { openAccessibilitySettings() }
        binding.overlayButton.setOnClickListener { SettingsNavigator.openOverlaySettings(this) }
        binding.appsButton.setOnClickListener { openAppPicker(onboarding = false) }

        binding.taskGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.taskType = when (checkedId) {
                R.id.taskMath -> TaskType.MATH
                R.id.taskPsalm -> TaskType.PSALM
                else -> TaskType.BREATH
            }
        }

        binding.previewButton.setOnClickListener {
            val sample = prefs.guardedPackages.firstOrNull() ?: packageName
            startActivity(GateActivity.intentFor(this, sample, preview = true))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()

        // First run after install: the phone's app list comes first, so the
        // permissions are being asked for something the user has already chosen.
        if (!prefs.setupDone) {
            if (!pickerLaunched) {
                pickerLaunched = true
                prefs.setupDone = true
                openAppPicker(onboarding = true)
            }
            return
        }

        promptForNextMissingPermission()
    }

    override fun onPause() {
        dialog?.dismiss()
        dialog = null
        super.onPause()
    }

    private fun refreshStatus() {
        val serviceOn = GateAccessibilityService.isEnabled(this)
        binding.serviceBody.setText(
            if (serviceOn) R.string.step_service_body_on else R.string.step_service_body_off
        )
        binding.serviceButton.isEnabled = !serviceOn

        val overlayOn = Settings.canDrawOverlays(this)
        binding.overlayBody.setText(
            if (overlayOn) R.string.step_overlay_body_on else R.string.step_overlay_body_off
        )
        binding.overlayButton.isEnabled = !overlayOn

        binding.taskGroup.check(
            when (prefs.taskType) {
                TaskType.BREATH -> R.id.taskBreath
                TaskType.MATH -> R.id.taskMath
                TaskType.PSALM -> R.id.taskPsalm
            }
        )

        val guarded = prefs.guardedPackages.size
        binding.appsBody.text = if (guarded == 0) {
            getString(R.string.step_apps_none)
        } else {
            resources.getQuantityString(R.plurals.apps_guarded, guarded, guarded)
        }
    }

    /** Ask for one thing at a time, in order, and only during the first run. */
    private fun promptForNextMissingPermission() {
        if (prefs.onboardingComplete || dialog?.isShowing == true) return

        when {
            !GateAccessibilityService.isEnabled(this) -> {
                // They have just come back from settings without switching it on.
                // On Android 13+ that is usually the restricted-settings block.
                if (awaitingAccessibility) {
                    awaitingAccessibility = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !restrictedHintShown) {
                        restrictedHintShown = true
                        showRestrictedSettingsHint()
                        return
                    }
                }
                showAsk(
                    title = R.string.ask_service_title,
                    message = R.string.ask_service_body,
                    positive = R.string.ask_open_settings,
                    onPositive = { openAccessibilitySettings() }
                )
            }

            !Settings.canDrawOverlays(this) -> showAsk(
                title = R.string.ask_overlay_title,
                message = R.string.ask_overlay_body,
                positive = R.string.ask_open_settings,
                onPositive = { SettingsNavigator.openOverlaySettings(this) }
            )

            else -> prefs.onboardingComplete = true
        }
    }

    private fun showAsk(
        title: Int,
        message: Int,
        positive: Int,
        onPositive: () -> Unit
    ) {
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(positive) { _, _ -> onPositive() }
            // Declining ends the walkthrough rather than asking again on every
            // visit; the cards on this screen stay as the way back in.
            .setNegativeButton(R.string.ask_later) { _, _ -> prefs.onboardingComplete = true }
            .show()
    }

    private fun showRestrictedSettingsHint() {
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restricted_title)
            .setMessage(R.string.restricted_body)
            .setPositiveButton(R.string.restricted_open_app_info) { _, _ ->
                SettingsNavigator.openAppInfo(this)
            }
            .setNegativeButton(R.string.ask_later, null)
            .show()
    }

    private fun openAccessibilitySettings() {
        awaitingAccessibility = true
        SettingsNavigator.openAccessibilitySettings(this)
    }

    private fun openAppPicker(onboarding: Boolean) {
        startActivity(
            Intent(this, AppPickerActivity::class.java)
                .putExtra(AppPickerActivity.EXTRA_ONBOARDING, onboarding)
        )
    }
}
