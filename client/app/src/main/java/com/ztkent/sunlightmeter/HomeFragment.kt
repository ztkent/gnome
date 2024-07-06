package com.ztkent.sunlightmeter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var linkDeviceButton: Button
    private lateinit var recyclerViewHeader: TextView

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
        recyclerView = view.findViewById(R.id.availableDevicesRecyclerView)
        linkDeviceButton = view.findViewById(R.id.refreshDevicesButton)
        recyclerViewHeader = view.findViewById(R.id.availableDevicesHeaderText)

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
        recyclerView.adapter = ConnectedDevicesListAdapter(viewModel.connectedDevices)
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            HomeFragment().apply {
            }
    }
}