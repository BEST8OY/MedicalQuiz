package com.medicalquiz.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.medicalquiz.app.databinding.ItemDatabaseBinding

class DatabaseAdapter(
    private val databases: List<DatabaseItem>,
    private val onItemClick: (DatabaseItem) -> Unit
) : RecyclerView.Adapter<DatabaseAdapter.DatabaseViewHolder>() {

    inner class DatabaseViewHolder(private val binding: ItemDatabaseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DatabaseItem) {
            binding.textViewDatabaseName.text = item.name
            binding.textViewDatabasePath.text = item.path
            binding.textViewDatabaseSize.text = item.size

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DatabaseViewHolder {
        val binding = ItemDatabaseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DatabaseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DatabaseViewHolder, position: Int) {
        holder.bind(databases[position])
    }

    override fun getItemCount(): Int = databases.size
}
