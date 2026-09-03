package com.noam.gate

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import com.noam.gate.databinding.ActivityGateBinding
import kotlin.random.Random

/** Three arithmetic problems, each answered by typing the number. */
class MathTask(
    private val binding: ActivityGateBinding,
    private val onDone: () -> Unit
) : GateTask {

    private data class Problem(val prompt: String, val answer: Int)

    private val context: Context = binding.root.context
    private val problems = generate()
    private var index = 0

    override fun start() {
        binding.mathGroup.visibility = View.VISIBLE
        binding.mathError.visibility = View.INVISIBLE

        binding.mathCheck.setOnClickListener { check() }
        binding.mathInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) check()
            true
        }
        // Typing again is the user having another go, so clear the last verdict.
        binding.mathInput.doAfterTextChanged {
            binding.mathError.visibility = View.INVISIBLE
        }

        show()
        binding.mathInput.requestFocus()
        context.getSystemService<InputMethodManager>()
            ?.showSoftInput(binding.mathInput, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun cancel() {
        hideKeyboard()
    }

    private fun show() {
        binding.taskLabel.text =
            context.getString(R.string.math_progress, index + 1, problems.size)
        binding.mathProblem.text = problems[index].prompt
        binding.mathInput.text?.clear()
    }

    private fun check() {
        val typed = binding.mathInput.text?.toString()?.trim().orEmpty()
        if (typed.isEmpty()) return

        if (typed.toIntOrNull() != problems[index].answer) {
            binding.mathError.visibility = View.VISIBLE
            binding.mathInput.text?.clear()
            return
        }

        index++
        if (index < problems.size) {
            binding.mathError.visibility = View.INVISIBLE
            show()
        } else {
            hideKeyboard()
            onDone()
        }
    }

    private fun hideKeyboard() {
        context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.mathInput.windowToken, 0)
    }

    /** One problem each from three of the four operations, in a random order. */
    private fun generate(): List<Problem> =
        listOf(Op.PLUS, Op.MINUS, Op.TIMES, Op.DIVIDE)
            .shuffled()
            .take(3)
            .map { it.problem() }

    private enum class Op {
        PLUS, MINUS, TIMES, DIVIDE;

        fun problem(): Problem = when (this) {
            PLUS -> {
                val a = Random.nextInt(12, 90)
                val b = Random.nextInt(12, 90)
                Problem("$a + $b", a + b)
            }
            MINUS -> {
                val a = Random.nextInt(30, 100)
                val b = Random.nextInt(11, a)
                Problem("$a − $b", a - b)
            }
            TIMES -> {
                val a = Random.nextInt(3, 13)
                val b = Random.nextInt(3, 13)
                Problem("$a × $b", a * b)
            }
            DIVIDE -> {
                // Built from the answer so it always divides evenly.
                val divisor = Random.nextInt(3, 13)
                val quotient = Random.nextInt(3, 13)
                Problem("${divisor * quotient} ÷ $divisor", quotient)
            }
        }
    }
}
