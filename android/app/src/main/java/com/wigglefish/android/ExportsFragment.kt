package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class ExportsFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_exports, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sessionText = view.findViewById<TextView>(R.id.sessionText)
        val sourceText = view.findViewById<TextView>(R.id.sourceText)
        val host = requireActivity() as SurveyHost

        view.findViewById<Button>(R.id.exportButton).setOnClickListener { host.shareSessionJson() }
        view.findViewById<Button>(R.id.csvButton).setOnClickListener { host.shareSessionCsv() }
        view.findViewById<Button>(R.id.clearButton).setOnClickListener { host.clearSession() }

        session.ui.observe(viewLifecycleOwner) { state ->
            sessionText.text = state.session
            sourceText.text = state.source
        }
    }
}
