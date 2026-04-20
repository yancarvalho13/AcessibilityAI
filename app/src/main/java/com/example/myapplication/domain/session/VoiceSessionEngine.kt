package com.example.myapplication.domain.session

import com.example.myapplication.domain.session.steps.BootStep
import com.example.myapplication.domain.session.steps.EndedStep
import com.example.myapplication.domain.session.steps.VoiceSessionStep
import com.example.myapplication.domain.session.steps.VoiceStepResult

class VoiceSessionEngine {
    private val context = VoiceSessionContext()
    private var currentStep: VoiceSessionStep = BootStep()

    fun start(): List<VoiceSessionAction> {
        currentStep = BootStep()
        return runStepEntry(currentStep)
    }

    fun onUserInput(input: String): List<VoiceSessionAction> {
        val result = currentStep.onUserInput(input, context)
        return applyResult(result)
    }

    fun onTimeout(): List<VoiceSessionAction> {
        val timeoutResult = VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.Speak("Sessao encerrada por inatividade"),
            ),
            nextStep = EndedStep(),
        )
        return applyResult(timeoutResult)
    }

    private fun runStepEntry(step: VoiceSessionStep): List<VoiceSessionAction> {
        val result = step.onEnter(context)
        return applyResult(result)
    }

    private fun applyResult(initial: VoiceStepResult): List<VoiceSessionAction> {
        val allActions = mutableListOf<VoiceSessionAction>()
        var currentResult: VoiceStepResult? = initial

        while (currentResult != null) {
            allActions += currentResult.actions

            val next = currentResult.nextStep
            if (next == null) {
                currentResult = null
            } else {
                currentStep = next
                currentResult = next.onEnter(context)
            }
        }

        return allActions
    }
}
