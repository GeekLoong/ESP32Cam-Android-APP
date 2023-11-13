package com.kuisec.esp32cam

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.kuisec.esp32cam.databinding.ActivityMainBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), OnClickListener {
    private var cameraIconFlag = false
    private var cameraOpenFlag = false
    private var flashFlag = false

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
        //重置网格
        binding.mainGridParent.post {
            val min = min(binding.mainGrid.measuredWidth, binding.mainGrid.measuredHeight)
            binding.mainGrid.layoutParams = LinearLayout.LayoutParams(min, min)
        }
        binding.cameraButton.setOnClickListener(this)
        binding.flashButton.setOnClickListener(this)
        binding.buzzerButton.setOnClickListener(this)
        binding.httpSetting.setOnClickListener(this)
        binding.forwardButton.setOnClickListener(this)
        binding.stopButton.setOnClickListener(this)
        binding.leftButton.setOnClickListener(this)
        binding.rightButton.setOnClickListener(this)
        binding.backButton.setOnClickListener(this)
        //初始化共享偏好设置
        SharedPreferencesUtil.init(this)
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
                            while (reader.nextPart().also { part = it } != null) {
                                val imgData: ByteArray = part!!.body.readByteArray()
                                BitmapFactory.decodeByteArray(imgData, 0, imgData.size)?.let {
                                    runOnUiThread {
                                        if (cameraIconFlag) {
                                            binding.protract.setImageBitmap(it)
                                            binding.cameraButton.setImageResource(R.drawable.camera_open)
                                        } else {
                                            binding.protract.setImageResource(R.drawable.bg_null)
                                            binding.cameraButton.setImageResource(R.drawable.camera_close)
                                        }
                                    }
                                }
                            }
                            cameraOpenFlag = false
                            runOnUiThread {
                                binding.protract.setImageResource(R.drawable.bg_null)
                                binding.cameraButton.setImageResource(R.drawable.camera_close)
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

    private fun sendCmd(cmdKey: String) {
        val ip = SharedPreferencesUtil.get("ip")
        val cmd = SharedPreferencesUtil.get(cmdKey)
        if (ip.isNotEmpty() and cmd.isNotEmpty()) {
            val url = "http://$ip$cmd"
            sendIns(url)
        } else {
            showTip("请先完成相关的配置填写")
        }
    }

    @SuppressLint("InflateParams")
    override fun onClick(v: View) {
        when (v.id) {
            R.id.camera_button -> {
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

            R.id.forward_button -> {
                sendCmd("forward")
            }

            R.id.stop_button -> {
                sendCmd("stop")
            }

            R.id.back_button -> {
                sendCmd("back")
            }

            R.id.left_button -> {
                sendCmd("left")
            }

            R.id.right_button -> {
                sendCmd("right")
            }

            R.id.flash_button -> {
                flashFlag = if (flashFlag) {
                    sendCmd("http_flash_close")
                    binding.flashButton.setImageResource(R.drawable.flash_close)
                    false
                } else {
                    sendCmd("http_flash_open")
                    binding.flashButton.setImageResource(R.drawable.flash_open)
                    true
                }
            }

            R.id.buzzer_button -> {

            }

            R.id.http_setting -> {
                AlertDialog.Builder(this).apply {
                    setTitle("连接设置")
                    val dialogView =
                        LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog, null)
                    val textInputEditTextList = arrayListOf<TextInputEditText>(
                        dialogView.findViewById(R.id.ip_edit),
                        dialogView.findViewById(R.id.stream_edit),
                        dialogView.findViewById(R.id.forward_edit),
                        dialogView.findViewById(R.id.back_edit),
                        dialogView.findViewById(R.id.stop_edit),
                        dialogView.findViewById(R.id.left_edit),
                        dialogView.findViewById(R.id.right_edit),
                        dialogView.findViewById(R.id.http_flash_open_edit),
                        dialogView.findViewById(R.id.http_flash_close_edit),
                        dialogView.findViewById(R.id.ble_flash_open_edit),
                        dialogView.findViewById(R.id.ble_flash_close_edit),
                        dialogView.findViewById(R.id.buzzer_open_edit),
                        dialogView.findViewById(R.id.buzzer_close_edit)
                    )
                    textInputEditTextList.map {
                        it.setText(SharedPreferencesUtil.get(it.tag.toString()))
                    }
                    setPositiveButton(
                        "保存"
                    ) { _, _ ->
                        textInputEditTextList.map {
                            SharedPreferencesUtil.set(it.tag.toString(), it.text.toString())
                        }
                        binding.ipText.text =
                            SharedPreferencesUtil.get("ip").ifEmpty { "请先设置IP地址" }
                        showTip("数据更新完成")
                    }
                    setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                    setView(dialogView)
                }.show()
            }
        }
    }
}