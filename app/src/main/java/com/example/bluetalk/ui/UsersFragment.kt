package com.example.bluetalk.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.adapter.UserListAdapter
import com.example.bluetalk.model.User


class UsersFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_users, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycleView: RecyclerView = view.findViewById(R.id.recyclerViewBluetoothUsers)
        recycleView.layoutManager = LinearLayoutManager(context)
        recycleView.adapter = UserListAdapter(requireContext(), listOf(User("122", "Hardy", "")))
    }
}