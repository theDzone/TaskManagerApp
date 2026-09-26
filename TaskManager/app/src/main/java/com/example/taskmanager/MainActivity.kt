package com.example.taskmanager

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.ui.theme.TaskManagerTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// ============================================================
// COLORS
// ============================================================

val Purple = Color(0xFF5146E5)
val PurpleLight = Color(0xFFE8E7FF)
val Background = Color(0xFFF8F8FC)
val CardWhite = Color.White
val HighRed = Color(0xFFFF5A5F)
val HighLight = Color(0xFFFFE4E5)
val MediumOrange = Color(0xFFFFB020)
val MediumLight = Color(0xFFFFF0D2)
val LowGreen = Color(0xFF20B486)
val LowLight = Color(0xFFDDF8EE)
val Blue = Color(0xFF3987FF)

// ============================================================
// DATA
// ============================================================

enum class Priority { LOW, MEDIUM, HIGH }

enum class Category(val label: String, val icon: String) {
    STUDY("Study", "📚"),
    WORK("Work", "💼"),
    PERSONAL("Personal", "🏠"),
    SHOPPING("Shopping", "🛒")
}

enum class TaskStatus(val label: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class TaskFilter(val label: String, val status: TaskStatus?) {
    ALL("All", null),
    TODO("To Do", TaskStatus.TODO),
    IN_PROGRESS("In Progress", TaskStatus.IN_PROGRESS)
}

enum class Screen { HOME, ADD, DETAILS, CALENDAR, STATS, SETTINGS }

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val priority: Priority = Priority.MEDIUM,
    val category: Category = Category.PERSONAL,
    val status: TaskStatus = TaskStatus.TODO,
    val dueAt: Long = defaultDueTime()          // epoch millis
)

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // App colours are hard-coded light: disable system dark mode and dynamic colour
            // so theme-derived colours (text fields, dialogs) stay readable.
            TaskManagerTheme(darkTheme = false, dynamicColor = false) {
                TaskManagerApp()
            }
        }
    }
}

// ============================================================
// APP (state + navigation)
// ============================================================

@Composable
fun TaskManagerApp() {

    val context = LocalContext.current

    // rememberSaveable: survives rotation / process-level config changes
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var returnScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    // Source of truth is SharedPreferences; reloaded after recreation.
    var tasks by remember { mutableStateOf(loadTasks(context)) }

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        saveTasks(context, newTasks)
    }

    fun openDetails(task: Task) {
        selectedTaskId = task.id
        returnScreen = currentScreen
        currentScreen = Screen.DETAILS
    }

    val goHome = { currentScreen = Screen.HOME }

    if (showSplash) {
        SplashScreen()
        LaunchedEffect(Unit) {
            delay(1500)
            showSplash = false
        }
        return
    }

    // System back: return to previous screen instead of closing the app
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = if (currentScreen == Screen.DETAILS) returnScreen else Screen.HOME
    }

    when (currentScreen) {

        Screen.HOME -> HomeScreen(
            tasks = tasks,
            onAdd = { currentScreen = Screen.ADD },
            onTaskClick = { openDetails(it) },
            onToggle = { task ->
                updateTasks(tasks.map {
                    if (it.id == task.id) {
                        it.copy(
                            status = if (it.status == TaskStatus.COMPLETED) TaskStatus.TODO
                            else TaskStatus.COMPLETED
                        )
                    } else it
                })
            },
            onNavigate = { currentScreen = it }
        )

        Screen.ADD -> AddTaskScreen(
            onBack = goHome,
            onSave = { newTask ->
                updateTasks(tasks + newTask)
                currentScreen = Screen.HOME
            }
        )

        Screen.DETAILS -> {
            val task = tasks.firstOrNull { it.id == selectedTaskId }
            if (task == null) {
                LaunchedEffect(Unit) { currentScreen = Screen.HOME }
            } else {
                TaskDetailsScreen(
                    task = task,
                    onBack = { currentScreen = returnScreen },
                    onStatusChange = { newStatus ->
                        updateTasks(tasks.map {
                            if (it.id == task.id) it.copy(status = newStatus) else it
                        })
                    },
                    onDelete = {
                        updateTasks(tasks.filter { it.id != task.id })
                        selectedTaskId = null
                        currentScreen = returnScreen
                    }
                )
            }
        }

        Screen.CALENDAR -> CalendarScreen(
            tasks = tasks,
            onBack = goHome,
            onTaskClick = { openDetails(it) }
        )

        Screen.STATS -> StatisticsScreen(tasks = tasks, onBack = goHome)

        Screen.SETTINGS -> SettingsScreen(onBack = goHome)
    }
}

// ============================================================
// SPLASH SCREEN
// ============================================================

@Composable
fun SplashScreen() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFB86BFF), Purple, Color(0xFF3030D8))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Purple, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))

            Text("Task Manager", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Organize your tasks,\nboost your productivity",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// HOME SCREEN
// ============================================================

@Composable
fun HomeScreen(
    tasks: List<Task>,
    onAdd: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onToggle: (Task) -> Unit,
    onNavigate: (Screen) -> Unit
) {

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }

    val visibleTasks = remember(tasks, query, filter) {
        val q = query.trim()
        tasks.filter { t ->
            (filter.status == null || t.status == filter.status) &&
                    (q.isEmpty() ||
                            t.title.contains(q, ignoreCase = true) ||
                            t.description.contains(q, ignoreCase = true))
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            BottomNavigation(current = Screen.HOME, onNavigate = onNavigate)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Purple,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = "Add task" }
            ) {
                Text("+", fontSize = 30.sp)
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Hi, Sudipta! 👋", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Let's make today productive", color = Color.Gray, fontSize = 13.sp)
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PurpleLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search tasks...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = fieldColors(),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.semantics { contentDescription = "Clear search" }
                        ) {
                            Text("✕", color = Color.Gray)
                        }
                    }
                } else null
            )

            Spacer(Modifier.height(15.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskFilter.values().forEach { f ->
                    val count = tasks.count { f.status == null || it.status == f.status }
                    StatusChip(
                        text = "${f.label} $count",
                        selected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            when {
                tasks.isEmpty() ->
                    EmptyState("No tasks yet", "Create your first task.")

                visibleTasks.isEmpty() ->
                    EmptyState("No matching tasks", "Try another search or filter.")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp), // FAB clearance
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onTaskClick(task) },
                            onToggle = { onToggle(task) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// TASK CARD
// ============================================================

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {

    val completed = task.status == TaskStatus.COMPLETED
    val overdue = !completed && task.dueAt < System.currentTimeMillis()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Checkbox(
                checked = completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Purple)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    color = if (completed) Color.Gray else Color.Black,
                    textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                )

                Text(
                    text = "▣  ${formatDue(task.dueAt)}" + if (overdue) "  ·  Overdue" else "",
                    color = if (overdue) HighRed else Color.Gray,
                    fontSize = 12.sp
                )

                if (task.status == TaskStatus.IN_PROGRESS) {
                    Text("In progress", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            PriorityBadge(priority = task.priority)
        }
    }
}

// ============================================================
// ADD TASK
// ============================================================

@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    onSave: (Task) -> Unit
) {

    val context = LocalContext.current

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var category by rememberSaveable { mutableStateOf(Category.STUDY) }
    var dueAt by rememberSaveable { mutableStateOf(defaultDueTime()) }
    var showTitleError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {

        ScreenHeader(title = "Add Task", onBack = onBack)

        // Scrollable form; Save button stays pinned at the bottom
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            Spacer(Modifier.height(25.dp))

            Text("Task title", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    showTitleError = false
                },
                placeholder = { Text("Enter task title...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text("Enter a task title to save.") }
                } else null,
                colors = fieldColors()
            )

            Spacer(Modifier.height(18.dp))

            Text("Description (optional)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text("Enter description...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )

            Spacer(Modifier.height(18.dp))

            Text("Due date", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable { pickDateTime(context, dueAt) { dueAt = it } }
                    .padding(16.dp)
            ) {
                Text("▣  ${formatDue(dueAt)}")
            }

            Spacer(Modifier.height(18.dp))

            Text("Priority", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentOption("Low", priority == Priority.LOW, { priority = Priority.LOW }, Modifier.weight(1f))
                SegmentOption("Medium", priority == Priority.MEDIUM, { priority = Priority.MEDIUM }, Modifier.weight(1f))
                SegmentOption("High", priority == Priority.HIGH, { priority = Priority.HIGH }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            Text("Category", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Category.values().forEach { c ->
                    CategoryButton(
                        category = c,
                        selected = category == c,
                        onClick = { category = c }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        Button(
            onClick = {
                if (title.isBlank()) {
                    showTitleError = true
                } else {
                    onSave(
                        Task(
                            title = title.trim(),
                            description = description.trim(),
                            priority = priority,
                            category = category,
                            dueAt = dueAt
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text("Save Task", modifier = Modifier.padding(6.dp))
        }
    }
}

// ============================================================
// TASK DETAILS
// ============================================================

@Composable
fun TaskDetailsScreen(
    task: Task,
    onBack: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    onDelete: () -> Unit
) {

    var confirmDelete by remember { mutableStateOf(false) }
    val completed = task.status == TaskStatus.COMPLETED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {

        ScreenHeader(title = "Task Details", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            Spacer(Modifier.height(25.dp))

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                Text(task.category.icon, fontSize = 32.sp)
            }

            Spacer(Modifier.height(15.dp))

            Text(task.title, fontSize = 27.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            PriorityBadge(priority = task.priority)

            Spacer(Modifier.height(22.dp))

            InfoCard(
                title = "Description",
                value = task.description.ifBlank { "No description provided." }
            )
            Spacer(Modifier.height(12.dp))
            InfoCard(title = "Due date", value = formatDue(task.dueAt))
            Spacer(Modifier.height(12.dp))
            InfoCard(title = "Category", value = task.category.label)

            Spacer(Modifier.height(18.dp))

            Text("Status", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskStatus.values().forEach { s ->
                    SegmentOption(
                        text = s.label,
                        selected = task.status == s,
                        onClick = { onStatusChange(s) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        Button(
            onClick = { onStatusChange(TaskStatus.COMPLETED) },
            enabled = !completed,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple,
                disabledContainerColor = PurpleLight,
                disabledContentColor = Purple
            )
        ) {
            Text(if (completed) "✓ Completed" else "✓ Mark as Completed")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp)
        ) {
            Text("Delete Task", color = HighRed)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("\"${task.title}\" will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Delete", color = HighRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = Purple)
                }
            }
        )
    }
}

// ============================================================
// CALENDAR
// ============================================================

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    onBack: () -> Unit,
    onTaskClick: (Task) -> Unit
) {

    var monthOffset by rememberSaveable { mutableStateOf(0) }
    var selectedDay by rememberSaveable { mutableStateOf(startOfDay(System.currentTimeMillis())) }

    val monthStart = remember(monthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)          // set day first: avoids Jan 31 + 1 month overflow
            add(Calendar.MONTH, monthOffset)
            clearTime()
        }
    }

    val year = monthStart.get(Calendar.YEAR)
    val month = monthStart.get(Calendar.MONTH)
    val leadingBlanks = monthStart.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayStart = startOfDay(System.currentTimeMillis())

    // Grid padded to full weeks so every row has 7 equal cells
    val cells: List<Int?> = buildList {
        repeat(leadingBlanks) { add(null) }
        for (d in 1..daysInMonth) add(d)
        while (size % 7 != 0) add(null)
    }

    val daysWithTasks = remember(tasks, year, month) {
        tasks.mapNotNull { t ->
            val c = Calendar.getInstance().apply { timeInMillis = t.dueAt }
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) {
                c.get(Calendar.DAY_OF_MONTH)
            } else null
        }.toSet()
    }

    val dayTasks = tasks
        .filter { startOfDay(it.dueAt) == selectedDay }
        .sortedBy { it.dueAt }

    fun dayMillis(day: Int): Long =
        (monthStart.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {

        ScreenHeader(title = "Calendar", onBack = onBack)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { monthOffset-- },
                    modifier = Modifier.semantics { contentDescription = "Previous month" }
                ) { Text("‹", fontSize = 28.sp) }

                Text(
                    text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthStart.time),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                IconButton(
                    onClick = { monthOffset++ },
                    modifier = Modifier.semantics { contentDescription = "Next month" }
                ) { Text("›", fontSize = 28.sp) }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                    Text(
                        text = it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .then(
                                    if (day != null) Modifier.clickable { selectedDay = dayMillis(day) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val millis = dayMillis(day)
                                val isSelected = millis == selectedDay
                                val isToday = millis == todayStart

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> Purple
                                                    isToday -> PurpleLight
                                                    else -> Color.Transparent
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            color = if (isSelected) Color.White else Color.Black,
                                            fontSize = 14.sp
                                        )
                                    }
                                    // Dot marks days that have tasks
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (day in daysWithTasks && !isSelected) Purple
                                                else Color.Transparent
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(25.dp))

            Text(
                text = "Tasks on " +
                        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(selectedDay)),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(15.dp))

            if (dayTasks.isEmpty()) {
                Text("No tasks scheduled for this day.", color = Color.Gray)
            } else {
                dayTasks.forEach { task ->
                    CalendarTask(task = task, onClick = { onTaskClick(task) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

// ============================================================
// STATISTICS
// ============================================================

@Composable
fun StatisticsScreen(
    tasks: List<Task>,
    onBack: () -> Unit
) {

    val total = tasks.size
    val completed = tasks.count { it.status == TaskStatus.COMPLETED }
    val inProgress = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val pending = tasks.count { it.status == TaskStatus.TODO }
    val rate = if (total == 0) 0 else (completed * 100) / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {

        ScreenHeader(title = "Statistics", onBack = onBack)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            Spacer(Modifier.height(25.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(total.toString(), "Total Tasks", Modifier.weight(1f))
                StatCard(completed.toString(), "Completed", Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(inProgress.toString(), "In Progress", Modifier.weight(1f))
                StatCard(pending.toString(), "Pending", Modifier.weight(1f))
            }

            Spacer(Modifier.height(25.dp))

            Text("Task Distribution", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(15.dp))

            DistributionRow("High", tasks.count { it.priority == Priority.HIGH }, HighRed)
            DistributionRow("Medium", tasks.count { it.priority == Priority.MEDIUM }, MediumOrange)
            DistributionRow("Low", tasks.count { it.priority == Priority.LOW }, LowGreen)

            Spacer(Modifier.height(25.dp))

            Text("Completion Rate", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(15.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$rate%", fontSize = 35.sp, fontWeight = FontWeight.Bold, color = Purple)
                    Spacer(Modifier.width(20.dp))
                    Text(if (rate >= 70) "Great work!" else "Keep going!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
// SETTINGS
// ============================================================

@Composable
fun SettingsScreen(onBack: () -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp)
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Spacer(Modifier.height(25.dp))

        SettingRow("Notifications", "Task reminders")
        HorizontalDivider()
        SettingRow("Appearance", "Light mode")
        HorizontalDivider()
        SettingRow("About", "Task Manager v1.1")
    }
}

// ============================================================
// BOTTOM NAVIGATION
// ============================================================

@Composable
fun BottomNavigation(
    current: Screen,
    onNavigate: (Screen) -> Unit
) {

    val items = listOf(
        Screen.HOME to "Home",
        Screen.CALENDAR to "Calendar",
        Screen.STATS to "Stats",
        Screen.SETTINGS to "Settings"
    )

    Surface(shadowElevation = 8.dp, color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (screen, label) ->
                NavItem(
                    text = label,
                    selected = current == screen,
                    onClick = { onNavigate(screen) }
                )
            }
        }
    }
}

// ============================================================
// SMALL COMPONENTS
// ============================================================

@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Purple,
    cursorColor = Purple,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // IconButton guarantees the 48dp minimum touch target
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "Back" }
        ) {
            Text("‹", fontSize = 35.sp)
        }

        Spacer(Modifier.width(4.dp))

        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Purple else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.DarkGray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun PriorityBadge(priority: Priority) {

    val (background, textColor) = when (priority) {
        Priority.HIGH -> HighLight to HighRed
        Priority.MEDIUM -> MediumLight to MediumOrange
        Priority.LOW -> LowLight to LowGreen
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(priority.name, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SegmentOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Purple else Color.White,
            contentColor = if (selected) Color.White else Color.DarkGray
        )
    ) {
        Text(text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun CategoryButton(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) PurpleLight else Color.White)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(category.icon, fontSize = 22.sp)
        Text(category.label, fontSize = 10.sp)
    }
}

@Composable
fun InfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun StatCard(number: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(number, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Purple)
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun DistributionRow(label: String, value: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CalendarTask(task: Task, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(45.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Purple)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                textDecoration = if (task.status == TaskStatus.COMPLETED) TextDecoration.LineThrough
                else TextDecoration.None
            )
            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(task.dueAt)),
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        PriorityBadge(priority = task.priority)
    }
}

@Composable
fun NavItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 16.dp),   // ≥48dp touch height
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Purple else Color.Gray,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✓", fontSize = 50.sp, color = Purple)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray, fontSize = 12.sp)
    }
}

// ============================================================
// DATE HELPERS  (java.util.Calendar: works on minSdk 24 without desugaring)
// ============================================================

fun Calendar.clearTime(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply { timeInMillis = millis }.clearTime().timeInMillis

/** Next full hour from now, so a new task is never overdue at creation. */
fun defaultDueTime(): Long =
    Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun formatDue(millis: Long): String {
    val due = startOfDay(millis)
    val today = startOfDay(System.currentTimeMillis())
    val tomorrow = startOfDay(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
    )
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    return when (due) {
        today -> "Today, $time"
        tomorrow -> "Tomorrow, $time"
        else -> SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))
    }
}

fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }

    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(year, month, day, hour, minute, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

// ============================================================
// PERSISTENCE
// ============================================================

private const val PREFS_NAME = "task_manager"
private const val KEY_TASKS = "tasks"

inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

fun saveTasks(context: Context, tasks: List<Task>) {

    val array = JSONArray()

    tasks.forEach { task ->
        array.put(
            JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("description", task.description)
                put("priority", task.priority.name)
                put("category", task.category.name)
                put("status", task.status.name)
                put("dueAt", task.dueAt)
            }
        )
    }

    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_TASKS, array.toString())
        .apply()
}

fun loadTasks(context: Context): List<Task> {

    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_TASKS, "[]") ?: "[]"

    val array = try {
        JSONArray(raw)
    } catch (e: JSONException) {
        return emptyList()                       // corrupt data: start clean instead of crashing
    }

    val seenIds = mutableSetOf<String>()

    return (0 until array.length()).mapNotNull { i ->

        val obj = array.optJSONObject(i) ?: return@mapNotNull null
        val title = obj.optString("title").trim()
        if (title.isEmpty()) return@mapNotNull null

        // Old Long ids are kept as strings; missing or duplicate ids get a fresh UUID
        var id = if (obj.isNull("id")) "" else obj.optString("id")
        if (id.isEmpty() || !seenIds.add(id)) {
            id = UUID.randomUUID().toString()
            seenIds.add(id)
        }

        Task(
            id = id,
            title = title,
            description = obj.optString("description"),
            priority = enumOrDefault(obj.optString("priority"), Priority.MEDIUM),
            category = enumOrDefault(obj.optString("category"), Category.PERSONAL),
            status = enumOrDefault(obj.optString("status"), TaskStatus.TODO),
            // Legacy tasks stored a text "dueDate"; they get the default due time
            dueAt = if (obj.has("dueAt")) obj.optLong("dueAt", defaultDueTime()) else defaultDueTime()
        )
    }
}