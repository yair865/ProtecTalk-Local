package com.example.protectalk.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.protectalk.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SignUpFragment : Fragment(R.layout.fragment_sign_up) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputDob = view.findViewById<TextInputEditText>(R.id.inputDob)
        val layoutDob = view.findViewById<TextInputLayout>(R.id.layoutDob)
        val btnCreate = view.findViewById<MaterialButton>(R.id.btnCreateAccount)

        // Build constraints: allow selecting from (today - 120y) .. today
        val dobConstraints = CalendarConstraints.Builder().apply {
            val todayUtc = MaterialDatePicker.todayInUtcMilliseconds()
            setEnd(todayUtc)

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = todayUtc
            cal.add(Calendar.YEAR, -120)
            setStart(cal.timeInMillis)

            setValidator(DateValidatorPointBackward.now())
        }.build()

        // One picker instance per creation
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.date_of_birth)) // make sure this exists in strings.xml
            .setCalendarConstraints(dobConstraints)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        // When a date is chosen, format and set into the text field
        datePicker.addOnPositiveButtonClickListener { selection ->
            // selection is UTC millis; format to local date
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCal.timeInMillis = selection
            inputDob.setText(sdf.format(utcCal.time))
        }

        // Open picker when clicking the field or the end icon
        val showPicker: () -> Unit = {
            if (parentFragmentManager.findFragmentByTag("dobPicker") == null) {
                datePicker.show(parentFragmentManager, "dobPicker")
            }
        }
        inputDob.setOnClickListener { showPicker() }
        layoutDob.setEndIconOnClickListener { showPicker() }

        // Navigate forward (leave as-is if you already wired validation)
        btnCreate.setOnClickListener {
            findNavController().navigate(R.id.action_signUp_to_home)
        }
    }
}
