package com.noam.gate

import android.os.Handler
import android.os.Looper
import android.view.View
import com.noam.gate.databinding.ActivityGateBinding

/**
 * A short chapter of Tehillim in Hebrew. The button that says you have read it
 * only turns up once there has been time to.
 */
class PsalmTask(
    private val binding: ActivityGateBinding,
    private val onDone: () -> Unit
) : GateTask {

    private val handler = Handler(Looper.getMainLooper())

    override fun start() {
        val psalm = PsalmRepository.random(binding.root.context)
        if (psalm == null) {
            // The asset could not be read. Better to let the user through than
            // to trap them behind a task that cannot be finished.
            onDone()
            return
        }

        binding.psalmGroup.visibility = View.VISIBLE
        binding.taskLabel.text = psalm.reference
        binding.taskLabel.letterSpacing = 0f      // tracking is for the Latin labels
        binding.psalmText.text = psalm.text

        binding.psalmRead.visibility = View.INVISIBLE
        binding.psalmRead.setOnClickListener { onDone() }

        handler.postDelayed({
            binding.psalmRead.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(280).start()
            }
        }, READ_DELAY_MS)
    }

    override fun cancel() {
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        /** Long enough that the button cannot be tapped before the words are read. */
        private const val READ_DELAY_MS = 5_000L
    }
}
