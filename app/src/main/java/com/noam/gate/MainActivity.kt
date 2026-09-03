package com.noam.gate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.noam.gate.databinding.ActivityMainBinding

/** Setup screen: turn the service on, grant the overlay permission, pick the apps. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.serviceButton.setOnClickListener {
            startActivity(GateAccessibilityService.settingsIntent())
        }

        binding.overlayButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        binding.appsButton.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        binding.passSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.passMinutes = value.toInt()
                updatePassLabel()
            }
        }

        binding.resetSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.resetOnLeave = checked
        }

        binding.previewButton.setOnClickListener {
            val preview = prefs.guardedPackages.firstOrNull() ?: packageName
            startActivity(GateActivity.intentFor(this, preview))
        }
    }

    override fun onResume() {
        super.onResume()

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

        val guarded = prefs.guardedPackages.size
        binding.appsBody.text = if (guarded == 0) {
            getString(R.string.step_apps_none)
        } else {
            resources.getQuantityString(R.plurals.apps_guarded, guarded, guarded)
        }

        binding.passSlider.value = prefs.passMinutes.toFloat()
        binding.resetSwitch.isChecked = prefs.resetOnLeave
        updatePassLabel()
    }

    private fun updatePassLabel() {
        binding.passLabel.text = getString(R.string.pass_minutes_label, prefs.passMinutes)
    }
}
