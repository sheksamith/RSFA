package com.samith.rsfa.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.samith.rsfa.utils.COMMAND_ID
import com.samith.rsfa.utils.COMMAND_START
import com.samith.rsfa.R
import com.samith.rsfa.utils.ForegroundService

/**
 * Created by samithchow on 3/7/2023.
 */

class MainActivity : AppCompatActivity() {
    private val permission: String = Manifest.permission.READ_SMS
    private val s: String = Manifest.permission.RECEIVE_SMS
    private val requestCode: Int = 1



    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Handler(Looper.getMainLooper()).postDelayed({

            if (ContextCompat.checkSelfPermission(this@MainActivity, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(permission), requestCode)
            } else {
                if (ContextCompat.checkSelfPermission(this, s)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(s), 2)
                } else {
                    val stopIntent = Intent(this, ForegroundService::class.java)
                    stopIntent.putExtra(COMMAND_ID, COMMAND_START)
                    startService(stopIntent)
                }
            }


        }, 1000)

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            val stopIntent = Intent(this, ForegroundService::class.java)
            stopIntent.putExtra(COMMAND_ID, COMMAND_START)
            startService(stopIntent)

        }

        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            if (ContextCompat.checkSelfPermission(this, s)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(s), 2)
            } else {
                val stopIntent = Intent(this, ForegroundService::class.java)
                stopIntent.putExtra(COMMAND_ID, COMMAND_START)
                startService(stopIntent)
            }
        }
    }
}