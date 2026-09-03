package com.noam.gate

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.noam.gate.databinding.ActivityGateBinding
import kotlin.math.ceil

/**
 * The pause screen. Ten seconds run down while a line sweeps up the screen for
 * five seconds and back down for five, and only then do the two buttons appear.
 */
class GateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGateBinding
    private lateinit var prefs: Prefs

    private var targetPackage: String = ""
    private var animator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        // While the countdown runs there is no way out: back does nothing, and
        // afterwards it means the same thing as declining.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.decisionGroup.visibility == View.VISIBLE) declineAndClose()
            }
        })

        binding.buttonStop.setOnClickListener { declineAndClose() }
        binding.buttonContinue.setOnClickListener { continueToApp() }

        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrEmpty()) {
            finish()
            return
        }
        targetPackage = packageName

        val label = AppRepository.labelOf(this, packageName)
        binding.appName.text = label
        binding.appIcon.setImageDrawable(AppRepository.iconOf(this, packageName))
        binding.buttonContinue.text = getString(R.string.gate_continue)
        binding.buttonStop.text = getString(R.string.gate_stop)

        startCountdown()
    }

    private fun startCountdown() {
        animator?.cancel()
        binding.decisionGroup.visibility = View.INVISIBLE
        binding.sweepLine.progress = 0f
        binding.countdownText.text = PHASE_SECONDS.toString()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COUNTDOWN_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                // First half sweeps up, second half comes back down.
                binding.sweepLine.progress =
                    if (fraction <= 0.5f) fraction * 2f else (1f - fraction) * 2f

                // Two counts of five rather than one count of ten: the number
                // restarts at 5 when the line turns around.
                val elapsedMs = fraction * COUNTDOWN_MS
                val intoPhase = elapsedMs % PHASE_MS
                val shown = ceil((PHASE_MS - intoPhase) / 1000f).toInt()
                    .coerceIn(0, PHASE_SECONDS)
                if (binding.countdownText.text != shown.toString()) {
                    binding.countdownText.text = shown.toString()
                }
            }
            doOnEndCompat { revealButtons() }
            start()
        }
    }

    private fun revealButtons() {
        binding.countdownText.text = "0"
        binding.sweepLine.progress = 0f

        binding.decisionGroup.apply {
            alpha = 0f
            translationY = 24f * resources.displayMetrics.density
            visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).setDuration(320).start()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Continue: hand out a pass so the gate does not fire again, then open the app. */
    private fun continueToApp() {
        prefs.grantPass(targetPackage)
        val launch = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }

    /**
     * Decline: leave the target app, ask Android to kill what is left of it in the
     * background, and take this screen out of the way as well.
     */
    private fun declineAndClose() {
        prefs.clearPass(targetPackage)

        // The service can go home and then kill the app; without it we can at
        // least leave the app.
        if (!GateAccessibilityService.closeApp(targetPackage)) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val packageToKill = targetPackage
            handler.postDelayed({
                runCatching {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    am.killBackgroundProcesses(packageToKill)
                }
            }, KILL_DELAY_MS)
        }

        finishAndRemoveTask()
    }

    override fun onStart() {
        super.onStart()
        isShowing = true
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
    }

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        super.onDestroy()
    }

    private fun ValueAnimator.doOnEndCompat(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!cancelled) block()
            }
        })
    }

    companion object {
        const val EXTRA_PACKAGE = "com.noam.gate.extra.PACKAGE"

        /** Five seconds up, then five seconds back down. */
        private const val PHASE_SECONDS = 5
        private const val PHASE_MS = PHASE_SECONDS * 1000f
        private const val COUNTDOWN_MS = 2 * PHASE_SECONDS * 1000L
        private const val KILL_DELAY_MS = 400L

        /** Read by the service so it never stacks a second gate on top of this one. */
        @Volatile
        var isShowing: Boolean = false

        fun intentFor(context: Context, packageName: String): Intent =
            Intent(context, GateActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
    }
}
