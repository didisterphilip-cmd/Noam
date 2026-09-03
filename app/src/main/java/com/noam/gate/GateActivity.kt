package com.noam.gate

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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.noam.gate.databinding.ActivityGateBinding

/**
 * The pause screen. It runs whichever task was chosen in setup — breathe, three
 * sums, or a chapter of Tehillim — and only once that is done does it show the
 * last day at this gate and the two buttons.
 */
class GateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGateBinding
    private lateinit var prefs: Prefs
    private lateinit var usage: UsageLog

    private var targetPackage: String = ""

    /** Set when the gate was raised for a website rather than an app. */
    private var targetSite: String? = null

    /** What the pass and the usage record are kept under: the site, or the app. */
    private val targetKey: String
        get() = targetSite?.let { GateTarget.forSite(it) } ?: targetPackage

    private var task: GateTask? = null
    private val handler = Handler(Looper.getMainLooper())

    /** A preview from the setup screen must not land in the real record. */
    private var isPreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        usage = UsageLog(this)

        // While the task runs there is no way out: back does nothing, and
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
        targetSite = intent.getStringExtra(EXTRA_SITE)
        isPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)

        // The icon is the only cue about which app this is — naming it invites
        // you to think about the app instead of the task.
        binding.appIcon.setImageDrawable(AppRepository.iconOf(this, packageName))

        // Reaching for it counts whether or not the user goes through.
        if (!isPreview) usage.recordAttempt(targetKey)

        startTask()
    }

    private fun startTask() {
        task?.cancel()
        binding.decisionGroup.visibility = View.INVISIBLE
        binding.breathGroup.visibility = View.GONE
        binding.mathGroup.visibility = View.GONE
        binding.psalmGroup.visibility = View.GONE

        task = when (prefs.taskType) {
            TaskType.BREATH -> BreathTask(binding, ::revealButtons)
            TaskType.MATH -> MathTask(binding, ::revealButtons)
            TaskType.PSALM -> PsalmTask(binding, ::revealButtons)
        }
        task?.start()
    }

    private fun revealButtons() {
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
        val lastEntry = usage.lastEntry(targetKey)
        if (lastEntry == null) {
            binding.statLastUsed.setText(R.string.stat_last_never)
        } else {
            val ago = Ago.format(this, lastEntry)
            highlight(binding.statLastUsed, getString(R.string.stat_last_used, ago), ago)
        }

        val tried = usage.attemptsInWindow(targetKey).coerceAtLeast(1)
        highlight(
            binding.statTried,
            resources.getQuantityString(R.plurals.stat_tried, tried, tried),
            if (tried == 1) null else tried.toString()
        )

        val declined = usage.declinesInWindow(targetKey)
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
        val start = value?.let { sentence.indexOf(it) } ?: -1
        if (value == null || start < 0) {
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

    /** Continue: hand out a pass so the gate does not fire again, then go through. */
    private fun continueToApp() {
        if (!isPreview) usage.recordEntry(targetKey)
        prefs.grantPass(targetKey)

        // A guarded site is already loaded in the browser behind this screen, so
        // there is nothing to launch — getting out of the way is enough.
        if (targetSite == null) {
            packageManager.getLaunchIntentForPackage(targetPackage)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    /**
     * Decline: leave the target app, ask Android to kill what is left of it in the
     * background, and take this screen out of the way as well.
     */
    private fun declineAndClose() {
        if (!isPreview) usage.recordDecline(targetKey)
        prefs.clearPass(targetKey)

        // For a site: step back off the page and leave the browser running, so the
        // user's other tabs survive. For an app: leave it and close it.
        val handled =
            if (targetSite != null) GateAccessibilityService.closeSite()
            else GateAccessibilityService.closeApp(targetPackage)

        if (!handled) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            if (targetSite == null) {
                val packageToKill = targetPackage
                handler.postDelayed({
                    runCatching {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        am.killBackgroundProcesses(packageToKill)
                    }
                }, KILL_DELAY_MS)
            }
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
        task?.cancel()
        task = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "com.noam.gate.extra.PACKAGE"
        const val EXTRA_SITE = "com.noam.gate.extra.SITE"
        const val EXTRA_PREVIEW = "com.noam.gate.extra.PREVIEW"

        private const val KILL_DELAY_MS = 400L

        /** Read by the service so it never stacks a second gate on top of this one. */
        @Volatile
        var isShowing: Boolean = false

        fun intentFor(
            context: Context,
            packageName: String,
            site: String? = null,
            preview: Boolean = false
        ): Intent =
            Intent(context, GateActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_SITE, site)
                .putExtra(EXTRA_PREVIEW, preview)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
    }
}
