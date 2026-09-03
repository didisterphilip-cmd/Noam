package com.noam.gate

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.noam.gate.databinding.ActivityGateBinding
import kotlin.math.ceil

/**
 * The pause screen. Ten seconds run down while a line sweeps up the screen for
 * five seconds and back down for five, and only then do the two buttons appear.
 */
class GateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGateBinding
    private lateinit var prefs: Prefs
    private lateinit var usage: UsageLog

    /** A preview from the setup screen must not land in the real record. */
    private var isPreview = false

    private var targetPackage: String = ""
    private var animator: ValueAnimator? = null

    /** Which of the two breath words is on screen, so it is only set when it changes. */
    private var shownBreath: Int = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        usage = UsageLog(this)

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
        isPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)

        // The icon is the only cue about which app this is — naming it invites
        // you to think about the app instead of the breath.
        binding.appIcon.setImageDrawable(AppRepository.iconOf(this, packageName))

        // Reaching for the app counts whether or not the user goes through.
        if (!isPreview) usage.recordAttempt(packageName)

        startCountdown()
    }

    private fun startCountdown() {
        animator?.cancel()
        shownBreath = R.string.breath_inhale
        binding.decisionGroup.visibility = View.INVISIBLE
        binding.sweepLine.progress = 0f
        binding.countdownText.text = PHASE_SECONDS.toString()
        binding.breathText.setText(R.string.breath_inhale)

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

                // The line rising is the breath in, the line falling the breath out.
                val breath = if (fraction < 0.5f) R.string.breath_inhale else R.string.breath_exhale
                if (breath != shownBreath) {
                    shownBreath = breath
                    binding.breathText.setText(breath)
                }
            }
            doOnEndCompat { revealButtons() }
            start()
        }
    }

    private fun revealButtons() {
        binding.countdownText.text = "0"
        binding.sweepLine.progress = 0f
        showUsage()

        binding.decisionGroup.apply {
            alpha = 0f
            translationY = 24f * resources.displayMetrics.density
            visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).setDuration(320).start()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** The last day at this gate, shown alongside the buttons to decide with. */
    private fun showUsage() {
        val lastEntry = usage.lastEntry(targetPackage)
        if (lastEntry == null) {
            binding.statLastUsed.setText(R.string.stat_last_never)
        } else {
            val ago = formatAgo(System.currentTimeMillis() - lastEntry)
            highlight(binding.statLastUsed, getString(R.string.stat_last_used, ago), ago)
        }

        val tried = usage.attemptsInWindow(targetPackage).coerceAtLeast(1)
        highlight(
            binding.statTried,
            resources.getQuantityString(R.plurals.stat_tried, tried, tried),
            if (tried == 1) null else tried.toString()
        )

        val declined = usage.declinesInWindow(targetPackage)
        if (declined == 0) {
            binding.statDeclined.setText(R.string.stat_declined_none)
        } else {
            highlight(
                binding.statDeclined,
                resources.getQuantityString(R.plurals.stat_declined, declined, declined),
                declined.toString()
            )
        }
    }

    /** Picks out the figure in a sentence so the eye lands on it first. */
    private fun highlight(view: TextView, sentence: String, value: String?) {
        if (value == null) {
            view.text = sentence
            return
        }
        val start = sentence.indexOf(value)
        if (start < 0) {
            view.text = sentence
            return
        }
        val span = SpannableString(sentence)
        val end = start + value.length
        val accent = ContextCompat.getColor(this, R.color.gate_accent)
        span.setSpan(ForegroundColorSpan(accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.text = span
    }

    private fun formatAgo(elapsed: Long): String {
        val minutes = elapsed / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1L -> getString(R.string.ago_moments)
            hours < 1L -> resources.getQuantityString(R.plurals.ago_minutes, minutes.toInt(), minutes.toInt())
            days < 1L -> resources.getQuantityString(R.plurals.ago_hours, hours.toInt(), hours.toInt())
            else -> resources.getQuantityString(R.plurals.ago_days, days.toInt(), days.toInt())
        }
    }

    /** Continue: hand out a pass so the gate does not fire again, then open the app. */
    private fun continueToApp() {
        if (!isPreview) usage.recordEntry(targetPackage)
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
        if (!isPreview) usage.recordDecline(targetPackage)
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
        const val EXTRA_PREVIEW = "com.noam.gate.extra.PREVIEW"

        /** Five seconds up, then five seconds back down. */
        private const val PHASE_SECONDS = 5
        private const val PHASE_MS = PHASE_SECONDS * 1000f
        private const val COUNTDOWN_MS = 2 * PHASE_SECONDS * 1000L
        private const val KILL_DELAY_MS = 400L

        /** Read by the service so it never stacks a second gate on top of this one. */
        @Volatile
        var isShowing: Boolean = false

        fun intentFor(context: Context, packageName: String, preview: Boolean = false): Intent =
            Intent(context, GateActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_PREVIEW, preview)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
    }
}
