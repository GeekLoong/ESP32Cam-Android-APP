package com.kuisec.esp32cam

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BTListAdapter(
    private val list: MutableList<ScanResult>,
    private val itemClickListener: (Int) -> Unit
) :
    RecyclerView.Adapter<BTListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btName: TextView = itemView.findViewById(R.id.btName)
        val btAddress: TextView = itemView.findViewById(R.id.btAddress)
        val btRssi: TextView = itemView.findViewById(R.id.btRssi)

        init {
            itemView.setOnClickListener {
                itemClickListener(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.ble_item, parent, false)
        return ViewHolder(view)
    }


    override fun getItemCount(): Int {
        return list.size
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        list[position].apply {
            holder.btName.text = this.device.name ?: "无名称"
            holder.btAddress.text = this.device.address
            holder.btRssi.text = this.rssi.toString()
        }
    }
}