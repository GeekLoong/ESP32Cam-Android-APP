package com.kuisec.esp32cam

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesUtil {
    private lateinit var sp: SharedPreferences
    private lateinit var spe: SharedPreferences.Editor
    fun init(context: Context) {
        sp = context.getSharedPreferences("setting", Context.MODE_PRIVATE)
        spe = sp.edit()
    }

    fun query(key: String): String {
        return if (::sp.isInitialized) {
            sp.getString(key, "") ?: ""
        } else {
            "请先初始化后再尝试"
        }
    }

    fun insert(key: String, value: String) {
        spe.putString(key, value)
        spe.commit()
    }
}