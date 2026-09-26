package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

/**
 * Flash / Firmware page — PR1 ships Identify only (ROM probe over USB OTG).
 * Full firmware write is intentionally deferred to a later PR.
 */
class FlashFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_flash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val statusText = view.findViewById<TextView>(R.id.flashStatusText)
        val deviceText = view.findViewById<TextView>(R.id.flashDeviceText)
        val resultText = view.findViewById<TextView>(R.id.identifyResultText)
        val connectButton = view.findViewById<Button>(R.id.flashConnectButton)
        val identifyButton = view.findViewById<Button>(R.id.identifyChipButton)
        val host = requireActivity() as SurveyHost

        connectButton.setOnClickListener { host.requestUsbConnectionForFlash() }
        identifyButton.setOnClickListener { host.requestEspIdentify() }

        session.ui.observe(viewLifecycleOwner) { state ->
            deviceText.text = state.device
            connectButton.text = if (state.connectButtonLabel == "CONNECTED") {
                "USB DEVICE READY"
            } else {
                "CONNECT / USE USB DEVICE"
            }
        }

        session.identify.observe(viewLifecycleOwner) { state ->
            statusText.text = state.status
            identifyButton.isEnabled = !state.busy
            identifyButton.text = if (state.busy) "IDENTIFYING…" else "IDENTIFY CHIP"
            resultText.text = state.resultText
        }
    }
}
