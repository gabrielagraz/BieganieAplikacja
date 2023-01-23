package com.example.bieganieaplikacja.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.bieganieaplikacja.R
import com.example.bieganieaplikacja.databinding.ItemRunBinding
import com.example.bieganieaplikacja.db.Run
import com.example.bieganieaplikacja.other.TrackingUtility
import java.text.SimpleDateFormat
import java.util.*

class RunAdapter : RecyclerView.Adapter<RunAdapter.RunViewHolder>() {

    inner class RunViewHolder(val binding: ItemRunBinding) : RecyclerView.ViewHolder(binding.root)

        private val diffCallBack = object : DiffUtil.ItemCallback<Run>() {
            override fun areItemsTheSame(oldItem: Run, newItem: Run): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Run, newItem: Run): Boolean {
                return oldItem.hashCode() == newItem.hashCode()
            }
        }

        private val differ = AsyncListDiffer(this, diffCallBack)

        fun submitList(list: List<Run>) = differ.submitList(list)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
            return RunViewHolder(
                ItemRunBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
        override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
            val run = differ.currentList[position]
            holder.binding.apply {
                run.img?.let {
                    ivRunImage.load(run.img) {
                        placeholder(R.drawable.loading_img)
                        error(R.drawable.ic_broken_image)
                    }
                }
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = run.timestamp
                }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                tvDate.text = dateFormat.format(calendar.time)

                val avgSpeed = "${run.avgSpeedInKMH} km/h"
                tvAvgSpeed.text = avgSpeed

                val distanceInKm = "${run.distanceInMeter / 1000f} km"
                tvDistance.text = distanceInKm

                tvTime.text = TrackingUtility.getFormattedStopWatchTime(run.timeInMillis)
                val caloriesBurned = "${run.caloriesBurned}kcal"
                tvCalories.text = caloriesBurned
            }
        }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}