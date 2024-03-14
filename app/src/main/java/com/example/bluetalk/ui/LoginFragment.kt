package com.example.bluetalk.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.example.bluetalk.R
import com.example.bluetalk.databinding.FragmentLoginBinding

class LoginFragment():Fragment(){
    private var _binding: FragmentLoginBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonCreateAccount.setOnClickListener{
            updateUsername(binding.editTextUsername.text.toString())
           // findNavController().navigate(R.id.action_login_to_conversations)
        }
    }

    private fun updateUsername(username: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        with(sharedPreferences.edit()) {
            putString("username", username)
            apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}