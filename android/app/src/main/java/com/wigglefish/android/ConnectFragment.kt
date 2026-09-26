package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class ConnectFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_connect, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val statusText = view.findViewById<TextView>(R.id.statusText)
        val deviceText = view.findViewById<TextView>(R.id.deviceText)
        val locationText = view.findViewById<TextView>(R.id.locationText)
        val sourceText = view.findViewById<TextView>(R.id.sourceText)
        val phoneCollectionButton = view.findViewById<Button>(R.id.phoneCollectionButton)
        val usbCollectionButton = view.findViewById<Button>(R.id.usbCollectionButton)
        val connectButton = view.findViewById<Button>(R.id.connectButton)
        val host = requireActivity() as SurveyHost

        connectButton.setOnClickListener { host.requestUsbConnection() }
        phoneCollectionButton.setOnClickListener { host.togglePhoneCollection() }
        usbCollectionButton.setOnClickListener { host.toggleUsbCollection() }

        session.ui.observe(viewLifecycleOwner) { state ->
            statusText.text = state.status
            deviceText.text = state.device
            locationText.text = state.location
            sourceText.text = state.source
            connectButton.text = state.connectButtonLabel
            phoneCollectionButton.text =
                if (state.phoneCollectionOn) "PHONE COLLECTION ON" else "PHONE COLLECTION OFF"
            usbCollectionButton.text =
                if (state.usbCollectionOn) "USB COLLECTION ON" else "USB COLLECTION OFF"
        }
    }
}
