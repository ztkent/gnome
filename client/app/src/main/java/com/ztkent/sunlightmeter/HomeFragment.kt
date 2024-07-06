package com.ztkent.sunlightmeter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var linkDeviceButton: Button
    private lateinit var recyclerViewHeader: TextView
    
    // Manage connected devices
    private lateinit var connectedDevices: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the view elements
        recyclerView = view.findViewById(R.id.buttonRecyclerView)
        linkDeviceButton = view.findViewById(R.id.refreshDevicesButton)
        recyclerViewHeader = view.findViewById(R.id.availableDevicesHeaderText)
        connectedDevices = mutableListOf("Test String1", "Test String 2", "Test String 3")

        // Set up the button links
        linkDeviceButton.setOnClickListener {
            val fragmentManager: FragmentManager = requireActivity().supportFragmentManager
            val fragmentTransaction = fragmentManager.beginTransaction()
            val connectDevicesFragment = ConnectDevicesFragment()
            fragmentTransaction.addToBackStack("HomeFragment")
            fragmentTransaction.replace(R.id.fragment_container, connectDevicesFragment)
            fragmentTransaction.commit()
        }

        // Set the page content
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ConnectedDevicesListAdapter(connectedDevices)
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            HomeFragment().apply {
            }
    }
}