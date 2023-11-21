package com.kuisec.esp32cam

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationSet
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.kuisec.esp32cam.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit
import kotlin.math.min

const val TAG = "MainActivity"

/**
 * 蓝牙模式开发：
 * 1.APP支持模式切换，切换后另外一个通信功能处于分支禁用状态。
 * 2.APP需要分离出WiFi设置保存和蓝牙设置保存，由通信状态决定。
 * 3.蓝牙模式沿用设计界面，参考WiFi模式界面，目前拟定为蓝牙搜索的设备显示列表放在写在WiFi模式的ImageView位置。
 * 4.底部图标新增蓝牙搜索、蓝牙开关、蜂鸣器功能、前进后退停止功能，提供相应的16进制指令保存功能。
 * 5.蓝牙传输使用无感SPP透传，实现数据收发，无需手动在设置中进行配对。
 */
class MainActivity : AppCompatActivity(), OnClickListener {
    private var cameraIconFlag = false
    private var cameraOpenFlag = false
    private var flashFlag = false
    private var isBle = false

    private lateinit var results: MutableList<ScanResult>

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var binding: ActivityMainBinding
    private var client = OkHttpClient
        .Builder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        bluetoothAdapter =
            this@MainActivity.getSystemService(BluetoothManager::class.java).adapter
        //重置网格
        binding.mainGridParent.post {
            val min = min(binding.mainGrid.measuredWidth, binding.mainGrid.measuredHeight)
            binding.mainGrid.layoutParams = LinearLayout.LayoutParams(min, min)
        }
        binding.firstButton.setOnClickListener(this)
        binding.secondButton.setOnClickListener(this)
        binding.thirdButton.setOnClickListener(this)
        binding.storageSetting.setOnClickListener(this)
        binding.forwardButton.setOnClickListener(this)
        binding.stopButton.setOnClickListener(this)
        binding.leftButton.setOnClickListener(this)
        binding.rightButton.setOnClickListener(this)
        binding.backButton.setOnClickListener(this)
        //初始化共享偏好设置
        SharedPreferencesUtil.init(this)
        binding.handoff.setOnCheckedChangeListener { switchButton, isChecked ->
            isBle = isChecked
            if (isChecked) {
                switchButton.isChecked = if (bluetoothAdapter.isEnabled) {
                    checkBLEPermission {
                        binding.listParent.visibility = View.VISIBLE
                        binding.firstButton.setImageResource(R.drawable.ble_disconnect)
                    }
                } else {
                    Toast.makeText(this, "蓝牙未开启，请开启蓝牙后重试", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                binding.listParent.visibility = View.GONE
                binding.firstButton.setImageResource(R.drawable.camera_close)
                cameraOpenFlag = false
            }
        }
    }

    private fun showVideo() {
        val request = Request.Builder()
            .url("http://${SharedPreferencesUtil.get("ip")}:81${SharedPreferencesUtil.get("stream")}")
            .build()
        showTip("发起请求")
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showTip("请求失败，请检查网络和地址是否异常")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        showTip("请求返回码非200段")
                    } else {
                        val mediaType = response.body!!.contentType()
                        if (mediaType != null && mediaType.type.equals(
                                "multipart", ignoreCase = true
                            )
                        ) {
                            cameraOpenFlag = true
                            cameraIconFlag = true
                            val reader = MultipartReader(response.body!!)
                            var part: MultipartReader.Part?
                            while (reader.nextPart().also { part = it } != null && cameraOpenFlag) {
                                val imgData: ByteArray = part!!.body.readByteArray()
                                BitmapFactory.decodeByteArray(imgData, 0, imgData.size)?.let {
                                    runOnUiThread {
                                        if (cameraIconFlag) {
                                            binding.protract.setImageBitmap(it)
                                            binding.firstButton.setImageResource(R.drawable.camera_open)
                                        } else {
                                            binding.protract.setImageResource(R.drawable.bg_null)
                                            binding.firstButton.setImageResource(R.drawable.camera_close)
                                        }
                                    }
                                }
                            }
                            cameraOpenFlag = false
                            runOnUiThread {
                                binding.protract.setImageResource(R.drawable.bg_null)
                                binding.firstButton.setImageResource(R.drawable.camera_close)
                            }
                        } else {
                            showTip("摄像头错误：无效的推流类型，请检查链接是否错误")
                        }
                    }
                } catch (e: Exception) {
                    showTip("出现异常，异常信息【${e.message}】")
                }
            }
        }
        client.newCall(request).enqueue(callback)
    }

    private fun showTip(tip: String) {
        Log.d(TAG, tip)
        runOnUiThread {
            Toast.makeText(this@MainActivity, tip, Toast.LENGTH_SHORT).show();
        }
    }

    private fun sendIns(url: String) {
        Log.d(TAG, "发起请求致【${url}】")
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showTip("请求失败，请检查网络和地址是否异常")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        showTip("请求返回码非200段")
                    } else {
                        showTip("对象设备已接收")
                    }
                } catch (e: Exception) {
                    showTip("出现异常，异常信息【${e.message}】")
                }
            }
        }
        Request.Builder().url(url).build().run {
            client.newCall(this).enqueue(callback)
        }
    }

    private fun sendWiFiCmd(cmdKey: String) {
        val ip = SharedPreferencesUtil.get("ip")
        val cmd = SharedPreferencesUtil.get(cmdKey)
        if (ip.isNotEmpty() and cmd.isNotEmpty()) {
            val url = "http://$ip$cmd"
            sendIns(url)
        } else {
            showTip("请先完成相关的配置填写")
        }
    }

    @SuppressLint("InflateParams", "Recycle", "MissingPermission")
    override fun onClick(v: View) {
        when (v.id) {
            R.id.first_button -> {
                if (isBle) {
                    val rotationAnimate =
                        ObjectAnimator.ofFloat(binding.firstButton, "rotation", 0f, 360f).apply {
                            duration = 2000
                            repeatCount = ObjectAnimator.INFINITE
                            interpolator = AccelerateDecelerateInterpolator()
                        }
                    if (bluetoothAdapter.isEnabled) {
                        //蓝牙扫描设置
                        val scanSettings = ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 设置扫描模式，低延迟扫描
                            .build()
                        devices.clear()
                        val waitDialog = AlertDialog.Builder(this).setTitle("请稍等")
                            .setMessage("正在扫描蓝牙设备中...")
                            .setCancelable(false).create()
                        Thread {
                            bluetoothAdapter.bluetoothLeScanner.startScan(
                                null,
                                scanSettings,
                                scanCallback
                            )
                            runOnUiThread {
                                waitDialog.show()
                                rotationAnimate.start()
                            }
                            Thread.sleep(10000)
                            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
                            results = devices.map { it.value }.sortedByDescending { it.rssi }
                                .toMutableList()
                            runOnUiThread {
                                waitDialog.dismiss()
                                val btListAdapter = BTListAdapter(results, itemClickListener)
                                binding.bleList.adapter = btListAdapter
                                binding.bleList.layoutManager = LinearLayoutManager(this)
                                rotationAnimate.end()
                            }
                        }.start()
                    } else {
                        Toast.makeText(this, "请打开蓝牙后重试", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (SharedPreferencesUtil.get("ip").isNotEmpty()) {
                        if (cameraOpenFlag) {
                            cameraIconFlag = !cameraIconFlag
                        } else {
                            showVideo()
                        }
                    } else {
                        showTip("请先设置IP地址")
                    }
                }
            }

            R.id.forward_button -> {
                sendWiFiCmd("forward")
            }

            R.id.stop_button -> {
                sendWiFiCmd("stop")
            }

            R.id.back_button -> {
                sendWiFiCmd("back")
            }

            R.id.left_button -> {
                sendWiFiCmd("left")
            }

            R.id.right_button -> {
                sendWiFiCmd("right")
            }

            R.id.second_button -> {
                flashFlag = if (flashFlag) {
                    sendWiFiCmd("http_flash_close")
                    binding.secondButton.setImageResource(R.drawable.flash_close)
                    false
                } else {
                    sendWiFiCmd("http_flash_open")
                    binding.secondButton.setImageResource(R.drawable.flash_open)
                    true
                }
            }

            R.id.third_button -> {

            }

            R.id.storage_setting -> {
                val dialogView: View?
                val textInputEditTextList: ArrayList<TextInputEditText> = arrayListOf()
                if (isBle) {
                    dialogView =
                        LayoutInflater.from(this@MainActivity).inflate(R.layout.ble_dialog, null)
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_forward_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_back_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_stop_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_left_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_right_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_flash_open_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_flash_close_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_buzzer_open_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_buzzer_close_edit))
                } else {
                    dialogView =
                        LayoutInflater.from(this@MainActivity).inflate(R.layout.wifi_dialog, null)
                    textInputEditTextList.add(dialogView.findViewById(R.id.ip_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.stream_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_forward_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_back_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_stop_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_left_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_right_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_flash_open_edit))
                    textInputEditTextList.add(dialogView.findViewById(R.id.wifi_flash_close_edit))
                }
                AlertDialog.Builder(this).apply {
                    setTitle("连接设置")
                    textInputEditTextList.map {
                        it.setText(SharedPreferencesUtil.get(it.tag.toString()))
                    }
                    setPositiveButton(
                        "保存"
                    ) { _, _ ->
                        textInputEditTextList.map {
                            SharedPreferencesUtil.set(it.tag.toString(), it.text.toString())
                        }
                        if (isBle) {
                            binding.addressText.text = ""
                        } else {
                            binding.addressText.text =
                                SharedPreferencesUtil.get("ip").ifEmpty { "请先设置IP地址" }
                        }
                        showTip("数据更新完成")
                    }
                    setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                    setView(dialogView)
                }.show()
            }
        }
    }

    private fun checkBLEPermission(checkCallback: () -> Unit): Boolean {
        return if (
            PermissionUtil.checkNormalPermission(
                this,
                PermissionUtil.PermissionData(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    "访问精确位置"
                )
            )
            and PermissionUtil.checkNormalPermission(
                this,
                PermissionUtil.PermissionData(
                    Manifest.permission.BLUETOOTH_SCAN,
                    "扫描蓝牙设备"
                )
            )
            and PermissionUtil.checkNormalPermission(
                this,
                PermissionUtil.PermissionData(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    "连接蓝牙设备"
                )
            )
            and PermissionUtil.checkNormalPermission(
                this,
                PermissionUtil.PermissionData(
                    Manifest.permission.BLUETOOTH_ADMIN,
                    "蓝牙管理"
                )
            )
        ) {
            checkCallback()
            true
        } else {
            Toast.makeText(this, "请先授予相关权限后再操作", Toast.LENGTH_SHORT).show()
            false
        }
    }

    //蓝牙设备列表
    private var devices = LinkedHashMap<String, ScanResult>()
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                devices.put(result.device.address, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d("BLE", "扫描失败")
        }
    }

    private val itemClickListener: (Int) -> Unit = {

        Toast.makeText(
            this,
            "地址：${results[it].device.address}\n信号：${results[it].rssi}",
            Toast.LENGTH_SHORT
        ).show()
    }
}