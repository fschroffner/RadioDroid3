package com.github.fschroffner.radiodroid3

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.fschroffner.radiodroid3.adapters.ItemAdapterCategory
import com.github.fschroffner.radiodroid3.category.CategoriesUiState
import com.github.fschroffner.radiodroid3.category.CategoriesViewModel
import com.github.fschroffner.radiodroid3.data.DataCategory
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import com.github.fschroffner.radiodroid3.databinding.FragmentStationsRemoteBinding
import com.github.fschroffner.radiodroid3.interfaces.IFragmentRefreshable
import com.github.fschroffner.radiodroid3.station.StationsFilter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment rendering category lists (tags, countries, languages).
 * Backed by [CategoriesViewModel] and [com.github.fschroffner.radiodroid3.data.CategoryRepository].
 */
@AndroidEntryPoint
class FragmentCategories : Fragment(), IFragmentRefreshable {

    companion object {
        private const val TAG = "FragmentCategories"
    }

    private val categoriesViewModel: CategoriesViewModel by viewModels()

    private var _binding: FragmentStationsRemoteBinding? = null
    private val binding get() = _binding!!

    private var relativeUrl: String = ""
    private var searchStyle = StationsFilter.SearchStyle.ByName
    private var singleUseFilter = false

    fun SetBaseSearchLink(searchStyle: StationsFilter.SearchStyle) {
        this.searchStyle = searchStyle
    }

    fun EnableSingleUseFilter(b: Boolean) {
        singleUseFilter = b
    }

    fun ClickOnItem(theData: DataCategory) {
        (requireActivity() as ActivityMain).Search(searchStyle, theData.Name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relativeUrl = arguments?.getString("url").orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStationsRemoteBinding.inflate(inflater, container, false)

        val adapterCategory = ItemAdapterCategory(R.layout.list_item_category)
        adapterCategory.categoryClickListener = object : ItemAdapterCategory.CategoryClickListener {
            override fun onCategoryClick(category: DataCategory) {
                ClickOnItem(category)
            }
        }

        binding.recyclerViewStations.adapter = adapterCategory
        binding.recyclerViewStations.layoutManager = LinearLayoutManager(context)

        binding.swiperefresh.setOnRefreshListener {
            loadCategories(forceUpdate = true)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        if (categoriesViewModel.uiState.value is CategoriesUiState.Idle) {
            loadCategories(forceUpdate = false)
        }
    }

    private fun loadCategories(forceUpdate: Boolean) {
        val showSingleUseTags = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean("single_use_tags", false)
        if (context != null) {
            LoadingStateHolder.show()
        }
        categoriesViewModel.load(relativeUrl, forceUpdate, searchStyle, singleUseFilter, showSingleUseTags)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                categoriesViewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect
                    when (state) {
                        is CategoriesUiState.Idle -> {
                            b.layoutError.visibility = View.GONE
                            b.swiperefresh.isRefreshing = false
                        }
                        is CategoriesUiState.Loading -> {
                            b.layoutError.visibility = View.GONE
                            b.swiperefresh.isRefreshing = true
                        }
                        is CategoriesUiState.Success -> {
                            LoadingStateHolder.hide()
                            b.layoutError.visibility = View.GONE
                            b.swiperefresh.isRefreshing = false
                            (b.recyclerViewStations.adapter as? ItemAdapterCategory)?.updateList(state.categories)
                        }
                        is CategoriesUiState.Error -> {
                            LoadingStateHolder.hide()
                            b.swiperefresh.isRefreshing = false
                            if ((b.recyclerViewStations.adapter?.itemCount ?: 0) == 0) {
                                b.layoutError.visibility = View.VISIBLE
                            }
                            if (context != null) {
                                Toast.makeText(context, R.string.error_list_update, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun Refresh() {
        loadCategories(forceUpdate = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
