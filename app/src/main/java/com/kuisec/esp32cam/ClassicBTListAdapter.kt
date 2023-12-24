package com.kuisec.esp32cam

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClassicBTListAdapter(
    private val deviceSet: List<BluetoothDevice>,
    private val itemClickListener: (Int) -> Unit
) :
    RecyclerView.Adapter<ClassicBTListAdapter.ViewHolder>() {

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
        return deviceSet.size
    }

    @SuppressLint("MissingPermission", "SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        deviceSet.toList()[position].apply {
            holder.btName.text = this.name ?: "无名称"
            holder.btAddress.text = this.address
            holder.btRssi.text = "None"
        }
    }
}