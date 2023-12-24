package com.kuisec.esp32cam

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.kuisec.esp32cam.databinding.ActivityMainBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

const val TAG = "MainActivity"

/**
 * 蓝牙模式开发：
 * 1.APP支持模式切换，切换后另外一个通信功能处于分支禁用状态。√
 * 2.APP需要分离出WiFi设置保存和蓝牙设置保存，由通信状态决定。√
 * 3.蓝牙模式沿用设计界面，参考WiFi模式界面，目前拟定为蓝牙搜索的设备显示列表放在写在WiFi模式的ImageView位置。√
 * 4.底部图标更改为蓝牙搜索开关、闪光灯功能、蜂鸣器功能、前进后退停止功能，提供相应的16进制指令保存功能。
 * 5.蓝牙传输使用无感SPP透传，实现数据收发，无需手动在设置中进行配对。√
 */

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), OnClickListener {
    private var cameraIconFlag = false
    private var cameraOpenFlag = false
    private var flashFlag = false
    private var buzzerFlag = false
    private var isBle = false

    private lateinit var results: MutableList<ScanResult>
    private lateinit var classicResult: List<BluetoothDevice>

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

    @SuppressLint("SetTextI18n")
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
                binding.stateText.text = "蓝牙"
                binding.addressText.text = "蓝牙模式无IP地址"
            } else {
                binding.listParent.visibility = View.GONE
                binding.firstButton.setImageResource(R.drawable.camera_close)
                binding.stateText.text = "WiFi"
                cameraOpenFlag = false
                mHandler.sendMessage(mHandler.obtainMessage(BT_DISCONNECT, 1))
                binding.addressText.text =
                    SharedPreferencesUtil.query("ip").ifEmpty { "请先设置IP地址" }
            }
        }
        binding.addressText.text = SharedPreferencesUtil.query("ip").ifEmpty { "请先设置IP地址" }
    }

    private fun showVideo() {
        val request = Request.Builder()
            .url("http://${SharedPreferencesUtil.query("ip")}:81${SharedPreferencesUtil.query("stream")}")
            .build()
        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "发起请求"))
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendMessage(
                    mHandler.obtainMessage(
                        SHOW,
                        "请求失败，请检查网络和地址是否异常"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "请求返回码非200段"))
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
                            mHandler.sendMessage(
                                mHandler.obtainMessage(
                                    SHOW,
                                    "摄像头错误：无效的推流类型，请检查链接是否错误"
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    mHandler.sendMessage(
                        mHandler.obtainMessage(
                            SHOW,
                            "出现异常，异常信息【${e.message}】"
                        )
                    )
                }
            }
        }
        client.newCall(request).enqueue(callback)
    }


    private fun sendIns(url: String) {
        Log.d(TAG, "发起请求致【${url}】")
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendMessage(
                    mHandler.obtainMessage(
                        SHOW,
                        "请求失败，请检查网络和地址是否异常"
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "请求返回码非200段"))
                    } else {
                        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "对象设备已接收"))
                    }
                } catch (e: Exception) {
                    mHandler.sendMessage(
                        mHandler.obtainMessage(
                            SHOW,
                            "出现异常，异常信息【${e.message}】"
                        )
                    )
                }
            }
        }
        Request.Builder().url(url).build().run {
            client.newCall(this).enqueue(callback)
        }
    }

    private fun sendWiFiCmd(cmdKey: String) {
        val ip = SharedPreferencesUtil.query("ip")
        val cmd = SharedPreferencesUtil.query(cmdKey)
        if (ip.isNotEmpty() and cmd.isNotEmpty()) {
            val url = "http://$ip$cmd"
            sendIns(url)
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(SHOW, "请先完成相关的配置填写"))
        }
    }

    private fun sendBTCmd(cmdKey: String) {
        var cmd = SharedPreferencesUtil.query(cmdKey)
        if (cmd.isNotEmpty()) {
            cmd = cmd.replace(" ", "")
            Log.d(TAG, cmd)
            if (cmd.length % 2 == 0) {
                val value = mutableListOf<Byte>()
                cmd.chunked(2).map {
                    value.add(it.toInt(16).toByte())
                }
                //写入经典蓝牙
                if (SharedPreferencesUtil.query("ble_mode") == "是") {
                    btOutputStream?.write(value.toByteArray())
                } else {//写入低功耗蓝牙
                    if (connectGatt != null && writeCharacteristic != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            connectGatt!!.writeCharacteristic(
                                writeCharacteristic!!,
                                value.toByteArray(),
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                        } else {
                            writeCharacteristic!!.writeType =
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            writeCharacteristic!!.value = value.toByteArray()
                            connectGatt!!.writeCharacteristic(writeCharacteristic)
                        }
                    } else {
                        mHandler.sendMessage(
                            mHandler.obtainMessage(
                                SHOW,
                                "你的蓝牙设备存在问题，请确保连接的蓝牙设备支持低功耗模式！"
                            )
                        )
                    }
                }
            } else {
                mHandler.sendMessage(
                    mHandler.obtainMessage(
                        SHOW,
                        "您所填写的数据有误，请按照【55 A2 C4 E2】这样的格式填写！"
                    )
                )
            }
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(SHOW, "请先完成相关的配置填写"))
        }
    }

    @SuppressLint("InflateParams", "Recycle")
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
                        mHandler.sendMessage(mHandler.obtainMessage(BT_DISCONNECT))
                        if (SharedPreferencesUtil.query("ble_mode") == "是") {
                            Thread {
                                classicResult = bluetoothAdapter.bondedDevices.toList()
                                runOnUiThread {
                                    val btListAdapter =
                                        ClassicBTListAdapter(classicResult, itemSocketListener)
                                    binding.bleList.adapter = btListAdapter
                                    binding.bleList.layoutManager = LinearLayoutManager(this)
                                }
                            }.start()
                        } else {
                            val waitDialog = AlertDialog.Builder(this).setTitle("请稍等")
                                .setMessage("正在扫描蓝牙设备中...")
                                .setCancelable(false).create()
                            Thread {
                                //蓝牙扫描设置
                                val scanSettings = ScanSettings.Builder()
                                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 设置扫描模式，低延迟扫描
                                    .build()
                                bluetoothAdapter.bluetoothLeScanner.startScan(
                                    null,
                                    scanSettings,
                                    scanCallback
                                )
                                runOnUiThread {
                                    waitDialog.show()
                                    rotationAnimate.start()
                                }
                                Thread.sleep(5000)
                                bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
                                results = devices.map { it.value }.sortedByDescending { it.rssi }
                                    .toMutableList()
                                runOnUiThread {
                                    waitDialog.dismiss()
                                    rotationAnimate.end()
                                    val btListAdapter = BTListAdapter(results, itemClickListener)
                                    binding.bleList.adapter = btListAdapter
                                    binding.bleList.layoutManager = LinearLayoutManager(this)
                                }
                            }.start()
                        }
                    } else {
                        Toast.makeText(this, "请打开蓝牙后重试", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (SharedPreferencesUtil.query("ip").isNotEmpty()) {
                        if (cameraOpenFlag) {
                            cameraIconFlag = !cameraIconFlag
                        } else {
                            showVideo()
                        }
                    } else {
                        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "请先设置IP地址"))
                    }
                }
            }

            R.id.forward_button -> {
                if (isBle) {
                    sendBTCmd("ble_forward")
                } else {
                    sendWiFiCmd("wifi_forward")
                }
            }

            R.id.stop_button -> {
                if (isBle) {
                    sendBTCmd("ble_stop")
                } else {
                    sendWiFiCmd("wifi_stop")
                }
            }

            R.id.back_button -> {
                if (isBle) {
                    sendBTCmd("ble_back")
                } else {
                    sendWiFiCmd("wifi_back")
                }
            }

            R.id.left_button -> {
                if (isBle) {
                    sendBTCmd("ble_left")
                } else {
                    sendWiFiCmd("wifi_left")
                }
            }

            R.id.right_button -> {
                if (isBle) {
                    sendBTCmd("ble_right")
                } else {
                    sendWiFiCmd("wifi_right")
                }
            }

            R.id.second_button -> {
                if (isBle) {
                    flashFlag = if (flashFlag) {
                        sendBTCmd("ble_flash_close")
                        binding.secondButton.setImageResource(R.drawable.flash_close)
                        false
                    } else {
                        sendBTCmd("ble_flash_open")
                        binding.secondButton.setImageResource(R.drawable.flash_open)
                        true
                    }
                } else {
                    flashFlag = if (flashFlag) {
                        sendWiFiCmd("wifi_flash_close")
                        binding.secondButton.setImageResource(R.drawable.flash_close)
                        false
                    } else {
                        sendWiFiCmd("wifi_flash_open")
                        binding.secondButton.setImageResource(R.drawable.flash_open)
                        true
                    }
                }
            }

            R.id.third_button -> {
                if (isBle) {
                    buzzerFlag = if (buzzerFlag) {
                        sendBTCmd("ble_buzzer_close")
                        binding.thirdButton.setImageResource(R.drawable.buzzer_close)
                        false
                    } else {
                        sendBTCmd("ble_buzzer_open")
                        binding.thirdButton.setImageResource(R.drawable.buzzer_open)
                        true
                    }
                } else {
                    mHandler.sendMessage(mHandler.obtainMessage(SHOW, "WiFi模式没有蜂鸣器功能！"))
                }
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
                    textInputEditTextList.add(dialogView.findViewById(R.id.ble_mode_edit))
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
                        it.setText(SharedPreferencesUtil.query(it.tag.toString()))
                    }
                    setPositiveButton(
                        "保存"
                    ) { _, _ ->
                        textInputEditTextList.map {
                            SharedPreferencesUtil.insert(it.tag.toString(), it.text.toString())
                        }
                        if (!isBle) {
                            binding.addressText.text =
                                SharedPreferencesUtil.query("ip").ifEmpty { "请先设置IP地址" }
                        }
                        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "数据更新完成"))
                    }
                    setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                    setView(dialogView)
                }.show()
            }
        }
    }

    private val UPDATE = 1
    private val SHOW = 2
    private val BT_CONNECT = 3
    private val BT_DISCONNECT = 4
    private val mHandler = object : Handler(Looper.getMainLooper()) {
        @SuppressLint("SetTextI18n")
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                UPDATE -> {

                }

                SHOW -> {
                    Toast.makeText(this@MainActivity, msg.obj as String, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg.obj as String)
                }

                BT_CONNECT -> {
                    Toast.makeText(this@MainActivity, "蓝牙已连接", Toast.LENGTH_SHORT).show()
                    Log.d("BT", "蓝牙已连接")
                    binding.firstButton.setImageResource(R.drawable.ble_connect)
                    if (SharedPreferencesUtil.query("ble_mode") == "是") {
                        binding.btState.text = ""
                    } else {
                        binding.btState.text = "当前连接设备为【${connectGatt!!.device.name}】"
                    }
                }

                BT_DISCONNECT -> {
                    val flag = msg.obj ?: 0
                    if (flag != 1) {
                        binding.firstButton.setImageResource(R.drawable.ble_disconnect)
                    }
                    devices.clear()
                    connectGatt?.also {
                        it.close()
                        connectGatt = null
                    }
                    btSocket?.run {
                        close()
                        outputStream.close()
                        btSocket = null
                        btOutputStream = null
                    }
                    Toast.makeText(this@MainActivity, "已重置蓝牙连接", Toast.LENGTH_SHORT).show()
                    Log.d("BT", "已重置蓝牙连接")
                    binding.btState.text = "当前未连接设备"
                }
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
                if (result.device.name != null) {
                    devices[result.device.address] = result
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d("BLE", "扫描失败")
        }
    }


    private var connectGatt: BluetoothGatt? = null
    private val itemClickListener: (Int) -> Unit = {
        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "开始连接，请稍等！请勿重复点击！"))
        connectGatt = results[it].device.connectGatt(this, false, gattCallback)
    }

    private var btSocket: BluetoothSocket? = null
    private var btOutputStream: OutputStream? = null
    private val itemSocketListener: (Int) -> Unit = {
        mHandler.sendMessage(mHandler.obtainMessage(SHOW, "开始连接，请稍等！请勿重复点击！"))
        btSocket = classicResult[it].run {
            createRfcommSocketToServiceRecord(uuids.first().uuid)
        }
        btSocket?.run {
            connect()
            if (isConnected) {
                mHandler.sendMessage(mHandler.obtainMessage(BT_CONNECT))
                btOutputStream = outputStream
            }
        }
    }

    var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            //判断连接成功与失败
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt?.discoverServices()
                mHandler.sendMessage(mHandler.obtainMessage(BT_CONNECT))
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                mHandler.sendMessage(
                    mHandler.obtainMessage(
                        SHOW,
                        "连接失败，对象蓝牙设备不允许普通SPP连接！"
                    )
                )
                mHandler.sendMessage(mHandler.obtainMessage(BT_DISCONNECT))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.services?.also {
                    writeCharacteristic = it[it.size - 1]?.characteristics?.get(1)
                    mHandler.sendMessage(mHandler.obtainMessage(SHOW, "服务状态监听成功"))
                }
            } else {
                mHandler.sendMessage(mHandler.obtainMessage(SHOW, "服务状态监听失败"))
            }
        }
    }
}