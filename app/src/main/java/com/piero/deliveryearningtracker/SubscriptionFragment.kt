package com.piero.deliveryearningtracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class SubscriptionViewModel : ViewModel() {
    private val _subscriptions = MutableLiveData<List<SubscriptionModel>>()
    val subscriptions: LiveData<List<SubscriptionModel>> get() = _subscriptions
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    fun loadSubscriptions(billingManager: BillingManager) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            billingManager.querySubscriptions { subscriptions, error ->
                _isLoading.postValue(false)
                if (error != null) {
                    _error.postValue(error)
                    _subscriptions.postValue(emptyList())
                } else {
                    _error.postValue(null)
                    _subscriptions.postValue(subscriptions ?: emptyList())
                }
            }
        }
    }
}

class SubscriptionFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorMessage: TextView
    private lateinit var adapter: SubscriptionAdapter
    private lateinit var billingManager: BillingManager
    private val viewModel: SubscriptionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("SubscriptionFragment", "onCreateView chiamato")
        return inflater.inflate(R.layout.subscription, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SubscriptionFragment", "onViewCreated chiamato")

        recyclerView = view.findViewById(R.id.subscription_list)
        progressBar = view.findViewById(R.id.progress_bar)
        errorMessage = view.findViewById(R.id.error_message)

        // Log dimensioni contenitore padre
        view.post {
            Log.d("SubscriptionFragment", "Contenitore padre dimensioni: width=${view.width}, height=${view.height}")
        }

        billingManager = BillingManager.getInstance(requireContext())
        adapter = SubscriptionAdapter(billingManager)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(
            DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
        )
        Log.d("SubscriptionFragment", "RecyclerView inizializzato, adapter impostato")

        recyclerView.post {
            Log.d("SubscriptionFragment", "RecyclerView dimensioni: width=${recyclerView.width}, height=${recyclerView.height}")
        }

        // Osserva i dati del ViewModel
        viewModel.subscriptions.observe(viewLifecycleOwner) { subscriptions ->
            Log.d("SubscriptionFragment", "Sottoscrizioni ricevute: ${subscriptions.size}")
            adapter.submitSubscriptions(subscriptions)
            recyclerView.visibility = if (subscriptions.isNotEmpty()) View.VISIBLE else View.GONE
            Log.d("SubscriptionFragment", "Sottoscrizioni inviate all'adapter")
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                errorMessage.text = "Errore: $error"
                errorMessage.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                Log.e("SubscriptionFragment", "Errore: $error")
            } else {
                errorMessage.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Carica le sottoscrizioni
        viewModel.loadSubscriptions(billingManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("SubscriptionFragment", "onDestroyView chiamato")
    }
}