package com.navigating.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.navigating.app.data.Feature
import com.navigating.app.databinding.ItemFeatureBinding

class FeatureAdapter(
    private val onNavigate: (Feature) -> Unit,
    private val onEdit: (Feature) -> Unit,
    private val onDelete: (Feature) -> Unit
) : ListAdapter<Feature, FeatureAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemFeatureBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = getItem(position)
        holder.binding.apply {
            tvName.text = f.name
            tvType.text = f.shapeType.name
            btnNavigate.setOnClickListener { onNavigate(f) }
            btnEdit.setOnClickListener { onEdit(f) }
            btnDelete.setOnClickListener { onDelete(f) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Feature>() {
            override fun areItemsTheSame(a: Feature, b: Feature) = a.id == b.id
            override fun areContentsTheSame(a: Feature, b: Feature) = a == b
        }
    }
}
