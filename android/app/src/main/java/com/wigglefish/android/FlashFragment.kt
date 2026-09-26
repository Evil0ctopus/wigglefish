package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.wigglefish.android.esp.FirmwareCatalog
import com.wigglefish.android.esp.FirmwareImage

/**
 * Flash / Firmware page — Identify + in-app flash write (PR2).
 */
class FlashFragment : Fragment() {
    private val session: SurveySessionViewModel by activityViewModels()
    private var catalog: List<FirmwareImage> = emptyList()
    private var shownImages: List<FirmwareImage> = emptyList()
    private var selectedImage: FirmwareImage? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_flash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        catalog = try {
            FirmwareCatalog.load(requireContext())
        } catch (_: Exception) {
            emptyList()
        }

        val statusText = view.findViewById<TextView>(R.id.flashStatusText)
        val deviceText = view.findViewById<TextView>(R.id.flashDeviceText)
        val resultText = view.findViewById<TextView>(R.id.identifyResultText)
        val connectButton = view.findViewById<Button>(R.id.flashConnectButton)
        val identifyButton = view.findViewById<Button>(R.id.identifyChipButton)
        val flashButton = view.findViewById<Button>(R.id.flashWriteButton)
        val spinner = view.findViewById<Spinner>(R.id.firmwareSpinner)
        val notesText = view.findViewById<TextView>(R.id.firmwareNotesText)
        val progressBar = view.findViewById<ProgressBar>(R.id.flashProgressBar)
        val progressText = view.findViewById<TextView>(R.id.flashProgressText)
        val logText = view.findViewById<TextView>(R.id.flashLogText)
        val host = requireActivity() as SurveyHost

        connectButton.setOnClickListener { host.requestUsbConnectionForFlash() }
        identifyButton.setOnClickListener { host.requestEspIdentify() }
        flashButton.setOnClickListener {
            val image = selectedImage
            if (image == null || !image.available) {
                notesText.text = "Select an available firmware image first (Identify a compatible chip)."
                return@setOnClickListener
            }
            host.requestEspFlash(image.id)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedImage = shownImages.getOrNull(position)
                val img = selectedImage
                notesText.text = when {
                    img == null -> "No compatible images for this chip."
                    !img.available -> "Unavailable: ${img.notes}"
                    else -> img.notes
                }
                val busy = session.flash.value?.busy == true || session.identify.value?.busy == true
                flashButton.isEnabled = img?.available == true && !busy
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedImage = null
                flashButton.isEnabled = false
            }
        }

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
            identifyButton.isEnabled = !state.busy && session.flash.value?.busy != true
            identifyButton.text = if (state.busy) "IDENTIFYING…" else "IDENTIFY CHIP"
            resultText.text = state.resultText
            refreshFirmwareList(spinner, notesText, flashButton, state.chipFamily, state.profileId)
        }

        session.flash.observe(viewLifecycleOwner) { state ->
            progressBar.progress = state.percent
            progressText.text = "${state.stageLabel}  ${state.percent}% — ${state.message}"
            if (state.logText.isNotBlank()) {
                logText.text = state.logText
            }
            val idle = !state.busy && session.identify.value?.busy != true
            identifyButton.isEnabled = idle
            flashButton.isEnabled = idle && selectedImage?.available == true
            flashButton.text = if (state.busy) "FLASHING…" else "FLASH SELECTED IMAGE"
            if (state.status.isNotBlank()) {
                statusText.text = state.status
            }
        }
    }

    private fun refreshFirmwareList(
        spinner: Spinner,
        notesText: TextView,
        flashButton: Button,
        chipFamily: String?,
        profileId: String?,
    ) {
        shownImages = if (chipFamily.isNullOrBlank()) {
            // Show all catalog entries so stubs are visible before identify.
            catalog
        } else {
            val matched = FirmwareCatalog.compatible(catalog, chipFamily, profileId)
            if (matched.isNotEmpty()) matched else catalog.filter { it.matchesChip(chipFamily) }
        }
        val labels = if (shownImages.isEmpty()) {
            listOf("(no images in catalog)")
        } else {
            shownImages.map { img ->
                val mark = if (img.available) "READY" else "STUB"
                "[$mark] ${img.label}"
            }
        }
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
        selectedImage = shownImages.firstOrNull()
        val img = selectedImage
        notesText.text = when {
            chipFamily.isNullOrBlank() -> "Identify a chip to filter compatible images. C5 ships with packaged Wigglefish firmware."
            img == null -> "No catalog entry for $chipFamily."
            !img.available -> "Unavailable: ${img.notes}"
            else -> img.notes
        }
        flashButton.isEnabled = img?.available == true && session.flash.value?.busy != true
    }
}
