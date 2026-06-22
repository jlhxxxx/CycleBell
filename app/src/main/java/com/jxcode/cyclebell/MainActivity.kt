package com.jxcode.cyclebell

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jxcode.cyclebell.alarm.AlarmScheduler
import com.jxcode.cyclebell.alarm.ReminderRecovery
import com.jxcode.cyclebell.alarm.ReminderSchedule
import com.jxcode.cyclebell.alarm.ReminderSchedule.intervalSecondsTotal
import com.jxcode.cyclebell.data.AppDatabase
import com.jxcode.cyclebell.data.ReminderDao
import com.jxcode.cyclebell.data.ReminderEntity
import com.jxcode.cyclebell.data.RepeatEndType
import com.jxcode.cyclebell.ui.theme.CycleBellTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.getInstance(applicationContext).reminderDao()
        val scheduler = AlarmScheduler(applicationContext)

        enableEdgeToEdge()
        setContent {
            CycleBellTheme {
                CycleBellApp(reminderDao = dao, alarmScheduler = scheduler)
            }
        }
    }
}

private enum class Screen { LIST, CREATE, EDIT }

@Composable
fun CycleBellApp(reminderDao: ReminderDao, alarmScheduler: AlarmScheduler) {
    var screen by remember { mutableStateOf(Screen.LIST) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var reminders by remember { mutableStateOf(emptyList<ReminderEntity>()) }
    var pendingScrollReminderId by remember { mutableStateOf<Long?>(null) }
    val homeListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(reminderDao) {
        reminderDao.observeReminders().collect { reminders = it }
    }
    LaunchedEffect(Unit) {
        ReminderRecovery.recover(reminderDao, alarmScheduler)
    }

    when (screen) {
        Screen.LIST -> HomeScreen(
            reminders = reminders,
            listState = homeListState,
            scrollToReminderId = pendingScrollReminderId,
            onScrolledToReminder = { pendingScrollReminderId = null },
            onCreate = { screen = Screen.CREATE },
            onEdit = {
                editingReminder = it
                screen = Screen.EDIT
            },
            onToggle = { reminder, enabled ->
                scope.launch {
                    if (enabled) {
                        val restarted = ReminderSchedule.withFreshSchedule(reminder.copy(enabled = true))
                        reminderDao.restartReminder(
                            id = reminder.id,
                            enabled = true,
                            nextTriggerAtMillis = restarted.nextTriggerAtMillis,
                            scheduleAnchorAtMillis = restarted.scheduleAnchorAtMillis,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        alarmScheduler.schedule(restarted)
                    } else {
                        alarmScheduler.cancel(reminder.id)
                        reminderDao.disableReminder(reminder.id, System.currentTimeMillis())
                    }
                }
            },
            onDelete = {
                scope.launch {
                    alarmScheduler.cancel(it.id)
                    reminderDao.deleteReminder(it)
                }
            }
        )

        Screen.CREATE -> ReminderFormScreen(
            existingReminder = null,
            onBack = { screen = Screen.LIST },
            onSave = { draft ->
                scope.launch {
                    val id = reminderDao.insertReminder(draft)
                    alarmScheduler.schedule(draft.copy(id = id))
                    pendingScrollReminderId = id
                    screen = Screen.LIST
                }
            }
        )

        Screen.EDIT -> ReminderFormScreen(
            existingReminder = editingReminder,
            onBack = { screen = Screen.LIST },
            onSave = { draft ->
                scope.launch {
                    alarmScheduler.cancel(draft.id)
                    reminderDao.updateReminder(draft)
                    alarmScheduler.schedule(draft)
                    editingReminder = null
                    pendingScrollReminderId = draft.id
                    screen = Screen.LIST
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    reminders: List<ReminderEntity>,
    listState: LazyListState,
    scrollToReminderId: Long?,
    onScrolledToReminder: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (ReminderEntity) -> Unit,
    onToggle: (ReminderEntity, Boolean) -> Unit,
    onDelete: (ReminderEntity) -> Unit
) {
    val nextReminder = reminders.filter { it.enabled && it.nextTriggerAtMillis != null }
        .minByOrNull { it.nextTriggerAtMillis ?: Long.MAX_VALUE }

    LaunchedEffect(reminders, scrollToReminderId) {
        val reminderId = scrollToReminderId ?: return@LaunchedEffect
        val reminderIndex = reminders.indexOfFirst { it.id == reminderId }
        if (reminderIndex >= 0) {
            listState.animateScrollToItem(reminderIndex + 2)
            onScrolledToReminder()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Cycle Bell") }) }) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { NextAlarmCard(nextReminder) }
            item {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onCreate) {
                    Text("New reminder")
                }
            }
            if (reminders.isEmpty()) {
                item {
                    Text("No reminders yet", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { onToggle(reminder, it) },
                        onEdit = { onEdit(reminder) },
                        onDelete = { onDelete(reminder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmCard(reminder: ReminderEntity?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Next alarm", style = MaterialTheme.typography.titleMedium)
            Text(reminder?.title ?: "None", style = MaterialTheme.typography.headlineSmall)
            Text(formatDateTime(reminder?.nextTriggerAtMillis), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleLarge)
                    Text(reminder.summary(), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = reminder.enabled, onCheckedChange = onToggle)
            }
            Text("Next: ${formatDateTime(reminder.nextTriggerAtMillis)}")
            Text("Completed: ${reminder.completedCount}")
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderFormScreen(
    existingReminder: ReminderEntity?,
    onBack: () -> Unit,
    onSave: (ReminderEntity) -> Unit
) {
    val nowPlusOne = remember { LocalTime.now().plusHours(1).withMinute(0).withSecond(0) }
    var title by remember { mutableStateOf(existingReminder?.title ?: "Reminder") }
    var startEnabled by remember { mutableStateOf(existingReminder?.startTimeEnabled ?: true) }
    var startH by remember { mutableStateOf((existingReminder?.startTimeHour ?: nowPlusOne.hour).toString().padStart(2, '0')) }
    var startM by remember { mutableStateOf((existingReminder?.startTimeMinute ?: 0).toString().padStart(2, '0')) }
    var startS by remember { mutableStateOf((existingReminder?.startTimeSecond ?: 0).toString().padStart(2, '0')) }
    var repeatEnabled by remember { mutableStateOf(existingReminder?.repeatEnabled ?: false) }
    var intervalD by remember { mutableStateOf(existingReminder?.intervalDays?.nonZeroString() ?: "") }
    var intervalH by remember { mutableStateOf(existingReminder?.intervalHours?.nonZeroString() ?: "") }
    var intervalM by remember { mutableStateOf(existingReminder?.intervalMinutes?.nonZeroString() ?: "") }
    var intervalS by remember { mutableStateOf(existingReminder?.intervalSeconds?.nonZeroString() ?: "") }
    var endType by remember { mutableStateOf(existingReminder?.repeatEndType ?: RepeatEndType.NEVER) }
    var afterTimes by remember { mutableStateOf(existingReminder?.repeatEndAfterTimes?.toString() ?: "") }
    var endH by remember { mutableStateOf((existingReminder?.repeatEndAtHour ?: 0).toString().padStart(2, '0')) }
    var endM by remember { mutableStateOf((existingReminder?.repeatEndAtMinute ?: 0).toString().padStart(2, '0')) }
    var endS by remember { mutableStateOf((existingReminder?.repeatEndAtSecond ?: 0).toString().padStart(2, '0')) }
    var ringSeconds by remember { mutableStateOf((existingReminder?.ringDurationSeconds ?: 5).toString()) }
    var vibrate by remember { mutableStateOf(existingReminder?.vibrate ?: false) }
    var ringtoneUri by remember { mutableStateOf(existingReminder?.ringtoneUri?.let(Uri::parse) ?: defaultAlarmRingtoneUri()) }
    var ringtoneTitle by remember { mutableStateOf("Default alarm sound") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.pickedRingtoneUri()?.let {
                ringtoneUri = it
                ringtoneTitle = ringtoneTitle(context, it)
            }
        }
    }

    val save = {
        val draft = buildReminder(
            existingReminder = existingReminder,
            title = title,
            startEnabled = startEnabled,
            startH = startH,
            startM = startM,
            startS = startS,
            repeatEnabled = repeatEnabled,
            intervalD = intervalD,
            intervalH = intervalH,
            intervalM = intervalM,
            intervalS = intervalS,
            endType = endType,
            afterTimes = afterTimes,
            endH = endH,
            endM = endM,
            endS = endS,
            ringSeconds = ringSeconds,
            vibrate = vibrate,
            ringtoneUri = ringtoneUri?.toString()
        )
        if (draft == null) error = "Please check the reminder settings." else {
            error = null
            onSave(draft)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingReminder == null) "New reminder" else "Edit reminder") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = save) {
                    Text(if (existingReminder == null) "Save reminder" else "Save changes")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SwitchSection("Start time", startEnabled, { startEnabled = it }) }
            if (startEnabled) item { TimeFields(startH, { startH = it }, startM, { startM = it }, startS, { startS = it }) }
            item { SwitchSection("Repeat", repeatEnabled, { repeatEnabled = it }) }
            if (repeatEnabled) {
                item { Text("Every", style = MaterialTheme.typography.titleMedium) }
                item { IntervalFields(intervalD, { intervalD = it }, intervalH, { intervalH = it }, intervalM, { intervalM = it }, intervalS, { intervalS = it }) }
                item { Text("Repeat ends", style = MaterialTheme.typography.titleMedium) }
                item { EndTypeRow(endType, { endType = it }) }
                if (endType == RepeatEndType.AFTER_TIMES) {
                    item { NumberField(afterTimes, { afterTimes = it }, "Times", 6, Modifier.fillMaxWidth()) }
                }
                if (endType == RepeatEndType.AT_TIME) {
                    item { TimeFields(endH, { endH = it }, endM, { endM = it }, endS, { endS = it }) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(ringSeconds, { ringSeconds = it }, "Ring for seconds", 3, Modifier.weight(1f), "5")
                    Spacer(Modifier.width(12.dp))
                    Text("Vibrate")
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sound", style = MaterialTheme.typography.titleMedium)
                            Text(ringtoneTitle)
                        }
                        TextButton(onClick = { ringtonePicker.launch(ringtonePickerIntent(ringtoneUri)) }) {
                            Text("Choose")
                        }
                    }
                }
            }
            item {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SwitchSection(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimeFields(h: String, onH: (String) -> Unit, m: String, onM: (String) -> Unit, s: String, onS: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        NumberField(h, onH, "h", 2, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        NumberField(m, onM, "m", 2, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        NumberField(s, onS, "s", 2, Modifier.weight(1f))
    }
}

@Composable
private fun IntervalFields(d: String, onD: (String) -> Unit, h: String, onH: (String) -> Unit, m: String, onM: (String) -> Unit, s: String, onS: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        NumberField(d, onD, "d", 4, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        NumberField(h, onH, "h", 2, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        NumberField(m, onM, "m", 2, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        NumberField(s, onS, "s", 2, Modifier.weight(1f))
    }
}

@Composable
private fun EndTypeRow(selected: RepeatEndType, onSelected: (RepeatEndType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RepeatEndType.entries.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == type, onClick = { onSelected(type) })
                Text(
                    when (type) {
                        RepeatEndType.NEVER -> "Never"
                        RepeatEndType.AFTER_TIMES -> "After"
                        RepeatEndType.AT_TIME -> "At"
                    }
                )
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, maxLength: Int, modifier: Modifier = Modifier, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxLength)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun buildReminder(
    existingReminder: ReminderEntity?,
    title: String,
    startEnabled: Boolean,
    startH: String,
    startM: String,
    startS: String,
    repeatEnabled: Boolean,
    intervalD: String,
    intervalH: String,
    intervalM: String,
    intervalS: String,
    endType: RepeatEndType,
    afterTimes: String,
    endH: String,
    endM: String,
    endS: String,
    ringSeconds: String,
    vibrate: Boolean,
    ringtoneUri: String?
): ReminderEntity? {
    val sh = if (startEnabled) startH.toBoundedInt(0, 23) ?: return null else null
    val sm = if (startEnabled) startM.toBoundedInt(0, 59) ?: return null else null
    val ss = if (startEnabled) startS.toBoundedInt(0, 59) ?: return null else null
    val days = intervalD.blankToZeroInt() ?: return null
    val hours = intervalH.toBoundedInt(0, 23, blankAsZero = true) ?: return null
    val minutes = intervalM.toBoundedInt(0, 59, blankAsZero = true) ?: return null
    val seconds = intervalS.toBoundedInt(0, 59, blankAsZero = true) ?: return null
    val after = if (repeatEnabled && endType == RepeatEndType.AFTER_TIMES) afterTimes.toIntOrNull()?.takeIf { it > 0 } ?: return null else null
    val eh = if (repeatEnabled && endType == RepeatEndType.AT_TIME) endH.toBoundedInt(0, 23) ?: return null else null
    val em = if (repeatEnabled && endType == RepeatEndType.AT_TIME) endM.toBoundedInt(0, 59) ?: return null else null
    val es = if (repeatEnabled && endType == RepeatEndType.AT_TIME) endS.toBoundedInt(0, 59) ?: return null else null
    val ring = ringSeconds.trim().ifEmpty { "5" }.toIntOrNull()?.takeIf { it > 0 } ?: return null

    if (repeatEnabled && days == 0 && hours == 0 && minutes == 0 && seconds == 0) return null

    val now = System.currentTimeMillis()
    val reminder = ReminderEntity(
        id = existingReminder?.id ?: 0,
        title = title.trim().ifBlank { "Reminder" },
        enabled = true,
        startTimeEnabled = startEnabled,
        startTimeHour = sh,
        startTimeMinute = sm,
        startTimeSecond = ss,
        repeatEnabled = repeatEnabled,
        intervalDays = days,
        intervalHours = hours,
        intervalMinutes = minutes,
        intervalSeconds = seconds,
        repeatEndType = if (repeatEnabled) endType else RepeatEndType.AFTER_TIMES,
        repeatEndAfterTimes = if (repeatEnabled) after else 1,
        repeatEndAtHour = eh,
        repeatEndAtMinute = em,
        repeatEndAtSecond = es,
        ringDurationSeconds = ring,
        vibrate = vibrate,
        ringtoneUri = ringtoneUri,
        completedCount = 0,
        nextTriggerAtMillis = null,
        scheduleAnchorAtMillis = null,
        createdAtMillis = existingReminder?.createdAtMillis ?: now,
        updatedAtMillis = now
    )
    return ReminderSchedule.withFreshSchedule(reminder, now)
}

private fun String.toBoundedInt(min: Int, max: Int, blankAsZero: Boolean = false): Int? {
    val text = trim().ifEmpty { if (blankAsZero) "0" else return null }
    return text.toIntOrNull()?.takeIf { it in min..max }
}

private fun String.blankToZeroInt(): Int? = trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 }
private fun Int.nonZeroString(): String = takeIf { it > 0 }?.toString() ?: ""

private fun ReminderEntity.summary(): String {
    if (!repeatEnabled) return "One-time alarm"
    val every = formatInterval(intervalSecondsTotal())
    val ends = when (repeatEndType) {
        RepeatEndType.NEVER -> "forever"
        RepeatEndType.AFTER_TIMES -> "after ${repeatEndAfterTimes ?: 1} times"
        RepeatEndType.AT_TIME -> "until ${timeText(repeatEndAtHour ?: 0, repeatEndAtMinute ?: 0, repeatEndAtSecond ?: 0)}"
    }
    return "Every $every, $ends"
}

private fun formatInterval(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0s"
    var remaining = totalSeconds
    val d = remaining / 86_400L
    remaining %= 86_400L
    val h = remaining / 3_600L
    remaining %= 3_600L
    val m = remaining / 60L
    val s = remaining % 60L
    return listOfNotNull(
        d.takeIf { it > 0 }?.let { "${it}d" },
        h.takeIf { it > 0 }?.let { "${it}h" },
        m.takeIf { it > 0 }?.let { "${it}m" },
        s.takeIf { it > 0 }?.let { "${it}s" }
    ).joinToString(" ")
}

private fun timeText(h: Int, m: Int, s: Int): String = "%02d:%02d:%02d".format(h, m, s)

private fun formatDateTime(epochMillis: Long?): String {
    if (epochMillis == null) return "--"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("M-d HH:mm:ss"))
}

private fun ringtonePickerIntent(currentUri: Uri?): Intent {
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose reminder sound")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
    }
}

private fun Intent.pickedRingtoneUri(): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}

private fun defaultAlarmRingtoneUri(): Uri? {
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}

private fun ringtoneTitle(context: Context, uri: Uri): String {
    return RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Selected sound"
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    CycleBellTheme {
        HomeScreen(emptyList(), rememberLazyListState(), null, {}, {}, {}, { _, _ -> }, {})
    }
}
