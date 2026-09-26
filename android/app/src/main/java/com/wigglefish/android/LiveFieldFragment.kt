package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class LiveFieldFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_live_field, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val spectrumText = view.findViewById<TextView>(R.id.spectrumText)
        val resultsText = view.findViewById<TextView>(R.id.resultsText)
        val viewText = view.findViewById<TextView>(R.id.viewText)
        val categoryText = view.findViewById<TextView>(R.id.categoryText)
        val decodeText = view.findViewById<TextView>(R.id.decodeText)
        val radarView = view.findViewById<SignalRadarView>(R.id.radarView)
        val countText = view.findViewById<TextView>(R.id.countText)
        val bleCountText = view.findViewById<TextView>(R.id.bleCountText)
        val gpsCountText = view.findViewById<TextView>(R.id.gpsCountText)
        val strongestText = view.findViewById<TextView>(R.id.strongestText)

        view.findViewById<Button>(R.id.allButton).setOnClickListener {
            session.setSelectedView("ALL")
        }
        view.findViewById<Button>(R.id.wifiButton).setOnClickListener {
            session.setSelectedView("WIFI")
        }
        view.findViewById<Button>(R.id.bleButton).setOnClickListener {
            session.setSelectedView("BLE")
        }

        session.ui.observe(viewLifecycleOwner) { state ->
            spectrumText.text = state.spectrum
            resultsText.text = state.results
            viewText.text = state.viewLabel
            categoryText.text = state.category
            decodeText.text = state.decode
            radarView.setSignalCount(state.signalCount)
            countText.text = "${state.wifiCount}\nWi-Fi"
            bleCountText.text = "${state.bleCount}\nBLE"
            gpsCountText.text = "${state.gpsSatCount}\nGPS SAT"
            strongestText.text = state.strongest
        }
    }
}
