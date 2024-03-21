package com.example.bluetalk.ui

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.example.bluetalk.R
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.databinding.FragmentWalkieTalkieBinding
import java.io.File


class WalkieTalkie(private val device: String, private val connectionType: Int = 0) : DialogFragment() {

    private var _binding: FragmentWalkieTalkieBinding?= null
    private val binding
        get() = _binding!!
    private lateinit var pushToTalkButton:ImageButton
    private var audioRecorder:MediaRecorder?=null
    private var audioFile:File?=null

    private val incomingAudioObserver = Observer<Boolean>{isIncoming->
        if(isIncoming){
            binding.speakerImg.visibility=View.VISIBLE
        }else{
            binding.speakerImg.visibility=View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        BluetalkServer.audioObserver.observe(viewLifecycleOwner, incomingAudioObserver)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
       _binding = FragmentWalkieTalkieBinding.inflate(inflater,container,false)
        pushToTalkButton = binding.pushButton
        binding.speakerImg.visibility=View.GONE
        pushToTalkButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // User is pressing and holding the button
                    // Start recording or transmitting
                    v.background = ContextCompat.getDrawable(requireContext(),
                        R.drawable.ic_push_to_talk_pushed
                    )
                    pushToTalkButton.setImageResource(R.drawable.ic_push_to_talk_pushed)
                    startRecording()
                    true // Return true to indicate the event was handled
                }
                MotionEvent.ACTION_UP -> {
                    // User released the button
                    // Stop recording or transmitting
                    v.background = ContextCompat.getDrawable(requireContext(),
                        R.drawable.ic_push_to_talk_not_pushed
                    )
                    pushToTalkButton.setImageResource(R.drawable.ic_push_to_talk_not_pushed)
                    stopRecording()
                    audioFile?.readBytes()?.let {
                        if(connectionType==0) {
                            BluetalkServer.sendAudio(device, it)
                        }else{
                            //BluetalkServer.forwardAudio()
                        }
                    }
                    true // Return true to indicate the event was handled
                }
                else -> false // Return false for other actions
            }
        }
        return binding.root
    }


    private fun startRecording() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S ) {
            audioRecorder = MediaRecorder(requireContext())
        }else{
            audioRecorder = MediaRecorder()
        }
        audioRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            audioFile = File.createTempFile("recorded_audio", ".3gp", context?.externalCacheDir)
            setOutputFile(audioFile?.absolutePath)
            prepare()
            start()
        }
    }

    private fun stopRecording() {
        audioRecorder?.apply {
            stop()
            release()
        }
    }
}