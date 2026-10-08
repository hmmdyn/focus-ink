package com.dain.focusink

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.dain.focusink.ui.InkTheme
import com.dain.focusink.ui.Root

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FocusInkApp
        setContent {
            InkTheme {
                Root(
                    app = app,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as FocusInkApp
        app.resumeTick.value = app.resumeTick.value + 1
        app.sync.pullAsync()
    }
}
