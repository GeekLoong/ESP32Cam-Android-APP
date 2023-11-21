package com.kuisec.esp32cam

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


/**
 * 授权单例工具类删减版，删除监听，只保留单权限申请
 * @author Jinsn
 * @date 2022/10/8 21:19:35
 * 根据权限使用条例来看，一次性申请多个权限是不明智的选择，所以封装了一个动态申请权限的权限类。
 *
 * 普通权限使用示例：
 * if (PermissionUtil.checkNormalPermission(
 *         MainActivity.activity, PermissionUtil.PermissionData(
 *             Manifest.permission.ACCESS_COARSE_LOCATION, "查看精准定位"
 *         )
 *     ) and PermissionUtil.checkNormalPermission(
 *         MainActivity.activity, PermissionUtil.PermissionData(
 *             Manifest.permission.BLUETOOTH, "蓝牙访问"
 *         )
 *     )
 * ) {
 *     Toast.makeText(this, "您已授予全部权限", Toast.LENGTH_SHORT).show()
 * }
 *
 */
object PermissionUtil {

    //最后申请的权限，用于判断回调是否生效，避免重复执行出现问题
    private var lastNormalPermission: PermissionData? = null


    /**
     * 检查普通权限
     * @param context Activity 上下文
     * @param permission 权限
     */
    fun checkNormalPermission(context: Context, permission: PermissionData): Boolean {
        //判断权限是否没有授权
        if (ContextCompat.checkSelfPermission(
                context,
                permission.permissionName
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //未授权
            val builder = AlertDialog.Builder(context)
            builder.setTitle("权限申请")
                .setMessage("为保证软件功能正常，请授予【${permission.permissionDescription}】权限")
                .setCancelable(false)
                .setPositiveButton("去授权") { _: DialogInterface?, _: Int ->
                    lastNormalPermission = permission
                    ActivityCompat.requestPermissions(
                        (context as Activity),
                        arrayOf(permission.permissionName),
                        1
                    )
                }
            builder.show()
            return false
        }
        return true
    }

    /**
     * 数据类
     * @param permissionName 权限名
     * @param permissionDescription 权限说明
     */
    data class PermissionData(val permissionName: String, val permissionDescription: String)

}