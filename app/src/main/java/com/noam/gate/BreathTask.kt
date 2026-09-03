package com.noam.gate

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import com.noam.gate.databinding.ActivityGateBinding
import kotlin.math.ceil

/**
 * Ten seconds of breathing: the line rises for five while the count runs 5 → 1,
 * then falls for five as it runs 5 → 1 again.
 */
class BreathTask(
    private val binding: ActivityGateBinding,
    private val onDone: () -> Unit
) : GateTask {

    private var animator: ValueAnimator? = null

    /** Which of the two words is on screen, so it is only set when it changes. */
    private var shownWord = 0

    override fun start() {
        binding.breathGroup.visibility = View.VISIBLE
        binding.sweepLine.progress = 0f
        binding.countdownText.text = PHASE_SECONDS.toString()
        binding.taskLabel.setText(R.string.breath_inhale)
        shownWord = R.string.breath_inhale

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TOTAL_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction

                // First half sweeps up, second half comes back down.
                binding.sweepLine.progress =
                    if (fraction <= 0.5f) fraction * 2f else (1f - fraction) * 2f

                // Two counts of five rather than one count of ten: the number
                // restarts at 5 when the line turns around.
                val intoPhase = (fraction * TOTAL_MS) % PHASE_MS
                val shown = ceil((PHASE_MS - intoPhase) / 1000f).toInt()
                    .coerceIn(0, PHASE_SECONDS)
                if (binding.countdownText.text != shown.toString()) {
                    binding.countdownText.text = shown.toString()
                }

                // The line rising is the breath in, the line falling the breath out.
                val word = if (fraction < 0.5f) R.string.breath_inhale else R.string.breath_exhale
                if (word != shownWord) {
                    shownWord = word
                    binding.taskLabel.setText(word)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    binding.countdownText.text = "0"
                    binding.sweepLine.progress = 0f
                    onDone()
                }
            })
            start()
        }
    }

    override fun cancel() {
        animator?.cancel()
        animator = null
    }

    companion object {
        /** Five seconds up, then five seconds back down. */
        private const val PHASE_SECONDS = 5
        private const val PHASE_MS = PHASE_SECONDS * 1000f
        private const val TOTAL_MS = 2 * PHASE_SECONDS * 1000L
    }
}
