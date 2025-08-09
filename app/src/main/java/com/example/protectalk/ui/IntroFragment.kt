package com.example.protectalk.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.protectalk.R
import com.google.android.material.button.MaterialButton

class IntroFragment : Fragment(R.layout.fragment_intro) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.btnSignIn).setOnClickListener {
            findNavController().navigate(R.id.action_intro_to_signUp)
        }
    }
}
