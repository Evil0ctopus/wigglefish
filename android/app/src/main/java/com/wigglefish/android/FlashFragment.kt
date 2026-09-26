package com.wigglefish.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Honest placeholder for future in-app firmware flash.
 * Vision: detect → flash compatible firmware → drive the field session.
 * Today: use the browser / ESP Web Tools flash path documented in the repo.
 */
class FlashFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_flash, container, false)
    }
}
