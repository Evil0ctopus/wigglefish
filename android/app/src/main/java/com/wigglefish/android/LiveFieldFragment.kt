package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.osmdroid.views.MapView

class LiveFieldFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()
    private var mapView: MapView? = null
    private var lastMarkerSig: String = ""

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
        val sessionText = view.findViewById<TextView>(R.id.sessionText)
        val coverageText = view.findViewById<TextView>(R.id.coverageText)
        val locationText = view.findViewById<TextView>(R.id.locationText)
        val mapHint = view.findViewById<TextView>(R.id.mapHintText)
        mapView = view.findViewById(R.id.surveyMap)
        val host = requireActivity() as SurveyHost

        SurveyMapController.bindMapView(mapView!!)

        view.findViewById<Button>(R.id.allButton).setOnClickListener {
            session.setSelectedView("ALL")
        }
        view.findViewById<Button>(R.id.wifiButton).setOnClickListener {
            session.setSelectedView("WIFI")
        }
        view.findViewById<Button>(R.id.bleButton).setOnClickListener {
            session.setSelectedView("BLE")
        }
        view.findViewById<Button>(R.id.stopAllButton).setOnClickListener {
            host.stopAllCollection()
        }
        view.findViewById<Button>(R.id.refreshMapButton).setOnClickListener {
            lastMarkerSig = ""
            refreshMap(force = true)
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
            gpsCountText.text = "${state.gpsFixCount}\nGPS PTS"
            strongestText.text = state.strongest
            sessionText.text = state.session
            coverageText.text = state.coverageHint
            locationText.text = state.location
            val geo = state.observationsWithGps
            val dist = when {
                state.distanceMeters <= 0.0 -> "track n/a"
                state.distanceMeters < 1000 -> "~%.0fm".format(state.distanceMeters)
                else -> "~%.2fkm".format(state.distanceMeters / 1000.0)
            }
            mapHint.text = "MAP  geo'd APs/BLE: $geo   GPS pts: ${state.gpsFixCount}   $dist   (OSM / osmdroid)"
            refreshMap(force = false)
        }
    }

    private fun refreshMap(force: Boolean) {
        val map = mapView ?: return
        val fixes = session.snapshotGpsFixes()
        val markers = session.mapMarkers()
        val sig = "${fixes.size}:${markers.size}:${markers.sumOf { it.hitCount }}:${fixes.lastOrNull()?.timestampMs ?: 0L}"
        if (!force && sig == lastMarkerSig) return
        lastMarkerSig = sig
        SurveyMapController.render(map, fixes, markers)
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }
}
