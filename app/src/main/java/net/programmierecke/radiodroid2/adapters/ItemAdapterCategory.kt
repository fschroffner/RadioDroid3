package net.programmierecke.radiodroid2.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.data.DataCategory

class ItemAdapterCategory(private val resourceId: Int) :
    RecyclerView.Adapter<ItemAdapterCategory.CategoryViewHolder>() {

    interface CategoryClickListener {
        fun onCategoryClick(category: DataCategory)
    }

    private var categoriesList: List<DataCategory> = emptyList()
    var categoryClickListener: CategoryClickListener? = null

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val textViewName: TextView = itemView.findViewById(R.id.textViewTop)
        val textViewCount: TextView = itemView.findViewById(R.id.textViewBottom)
        val iconView: ImageView = itemView.findViewById(R.id.iconCategoryViewIcon)

        init { itemView.setOnClickListener(this) }

        override fun onClick(view: View) {
            categoryClickListener?.onCategoryClick(categoriesList[adapterPosition])
        }
    }

    fun updateList(categoriesList: List<DataCategory>) {
        this.categoriesList = categoriesList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(resourceId, parent, false)
        return CategoryViewHolder(v)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categoriesList[position]
        holder.textViewName.text = category.Label ?: category.Name
        category.Icon?.let { holder.iconView.setImageDrawable(it) }
        holder.textViewCount.text = category.UsedCount.toString()
    }

    override fun getItemCount(): Int = categoriesList.size
}
