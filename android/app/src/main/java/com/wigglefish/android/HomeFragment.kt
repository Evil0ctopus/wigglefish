package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class HomeFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val statusText = view.findViewById<TextView>(R.id.statusText)
        val strongestText = view.findViewById<TextView>(R.id.strongestText)
        val sessionText = view.findViewById<TextView>(R.id.sessionText)
        val coverageText = view.findViewById<TextView>(R.id.coverageText)
        val countText = view.findViewById<TextView>(R.id.countText)
        val bleCountText = view.findViewById<TextView>(R.id.bleCountText)
        val gpsCountText = view.findViewById<TextView>(R.id.gpsCountText)
        val deviceText = view.findViewById<TextView>(R.id.deviceText)
        val host = requireActivity() as SurveyHost

        view.findViewById<Button>(R.id.openConnectButton).setOnClickListener {
            host.navigateTo(R.id.connectFragment)
        }
        view.findViewById<Button>(R.id.openLiveFieldButton).setOnClickListener {
            host.navigateTo(R.id.liveFieldFragment)
        }
        view.findViewById<Button>(R.id.openExportsButton).setOnClickListener {
            host.navigateTo(R.id.exportsFragment)
        }
        view.findViewById<Button>(R.id.openFlashButton).setOnClickListener {
            host.navigateTo(R.id.flashFragment)
        }
        view.findViewById<Button>(R.id.stopAllButton).setOnClickListener {
            host.stopAllCollection()
        }

        session.ui.observe(viewLifecycleOwner) { state ->
            statusText.text = state.status
            strongestText.text = state.strongest
            sessionText.text = state.session
            coverageText.text = state.coverageHint
            countText.text = "${state.wifiCount}\nWi-Fi"
            bleCountText.text = "${state.bleCount}\nBLE"
            gpsCountText.text = if (state.hasGpsFix) {
                "${state.gpsFixCount}\nGPS OK"
            } else {
                "${state.gpsSatCount}\nGPS SAT"
            }
            deviceText.text = state.device
        }
    }
}
