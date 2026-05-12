package com.jj.myclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jj.myclock.data.AppDatabase
import com.jj.myclock.ui.theme.MyClockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDatabase.getInstance(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyClockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReminderListScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderListScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "循环闹钟",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "提醒数据层已接入，下一步可以开始创建提醒列表和新建页面。",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = {}) {
            Text(text = "新建提醒")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReminderListPreview() {
    MyClockTheme {
        ReminderListScreen()
    }
}
