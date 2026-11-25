package com.aryan.directsim

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesActivity : Activity() {

    private lateinit var searchInput: EditText
    private lateinit var sortBtn: Button
    private lateinit var clearAllBtn: Button
    private lateinit var notesContainer: LinearLayout
    private lateinit var filterButtons: List<Button>

    private var selectedCategory = "All"
    private var newestFirst = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        renderNotes()
    }

    override fun onResume() {
        super.onResume()
        renderNotes()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun roundedBg(fillHex: String, strokeHex: String? = null, radiusDp: Int = 14): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun buildUi() {
        val screenRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Call Notes"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        sortBtn = Button(this).apply {
            text = "Newest ↓"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#262626", "#404040", 10)
            setTextColor(Color.parseColor("#E5E5E5"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                newestFirst = !newestFirst
                text = if (newestFirst) "Newest ↓" else "Oldest ↑"
                renderNotes()
            }
        }

        clearAllBtn = Button(this).apply {
            text = "Clear All"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#262626", "#404040", 10)
            setTextColor(Color.parseColor("#F87171"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
            setOnClickListener {
                Storage.clearAllCallNotes(this@NotesActivity)
                renderNotes()
                Toast.makeText(this@NotesActivity, "All call notes cleared", Toast.LENGTH_SHORT).show()
            }
        }

        titleRow.addView(title)
        titleRow.addView(sortBtn)
        titleRow.addView(clearAllBtn)

        searchInput = EditText(this).apply {
            hint = "Search caller number or message..."
            setHintTextColor(Color.parseColor("#737373"))
            setTextColor(Color.parseColor("#F5F5F5"))
            textSize = 14f
            isSingleLine = true
            background = roundedBg("#1E1E1E", "#383838", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderNotes()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val categories = listOf("All", "Urgent", "Callback", "General")
        filterButtons = categories.map { cat ->
            Button(this).apply {
                text = cat
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                tag = cat
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    selectedCategory = cat
                    updateFilterChips()
                    renderNotes()
                }
            }
        }
        filterButtons.forEach { chipRow.addView(it) }
        chipScroll.addView(chipRow)

        topSection.addView(titleRow)
        topSection.addView(searchInput)
        topSection.addView(chipScroll)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(24))
        }
        scroll.addView(notesContainer)

        // Bottom Navigation Bar
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }

        val dashboardNavBtn = Button(this).apply {
            text = "Dashboard"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#181818", "#383838", 12)
            setTextColor(Color.parseColor("#A3A3A3"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = dp(8) }
            setOnClickListener { finish() }
        }

        val notesNavBtn = Button(this).apply {
            text = "Call Notes"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#262626", "#525252", 12)
            setTextColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(8) }
        }

        bottomNav.addView(dashboardNavBtn)
        bottomNav.addView(notesNavBtn)

        screenRoot.addView(topSection)
        screenRoot.addView(scroll)
        screenRoot.addView(bottomNav)

        screenRoot.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                topInset = sys.top
                bottomInset = sys.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            topSection.setPadding(dp(20), topInset + dp(14), dp(20), dp(10))
            bottomNav.setPadding(dp(16), dp(10), dp(16), bottomInset + dp(10))
            insets
        }

        setContentView(screenRoot)
        updateFilterChips()
    }

    private fun updateFilterChips() {
        filterButtons.forEach { btn ->
            val cat = btn.tag as String
            if (cat == selectedCategory) {
                btn.background = roundedBg("#F5F5F5", null, 20)
                btn.setTextColor(Color.parseColor("#121212"))
            } else {
                btn.background = roundedBg("#1E1E1E", "#383838", 20)
                btn.setTextColor(Color.parseColor("#D4D4D4"))
            }
        }
    }

    private fun renderNotes() {
        notesContainer.removeAllViews()
        val query = searchInput.text.toString().trim().lowercase(Locale.getDefault())

        var notes = Storage.getCallNotes(this)
        if (selectedCategory != "All") {
            notes = notes.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        if (query.isNotEmpty()) {
            notes = notes.filter {
                it.callerNumber.lowercase(Locale.getDefault()).contains(query) ||
                    it.summary.lowercase(Locale.getDefault()).contains(query) ||
                    it.transcript.lowercase(Locale.getDefault()).contains(query)
            }
        }
        notes = if (newestFirst) {
            notes.sortedByDescending { it.timestampMs }
        } else {
            notes.sortedBy { it.timestampMs }
        }

        if (notes.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg("#1E1E1E", "#383838", 16)
                setPadding(dp(24), dp(28), dp(24), dp(28))
                gravity = Gravity.CENTER
            }
            val emptyTitle = TextView(this).apply {
                text = "No Call Notes Found"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#F5F5F5"))
                gravity = Gravity.CENTER
            }
            val emptySub = TextView(this).apply {
                text = "Whenever the AI assistant handles a call, the caller number, summary, and full transcript appear here."
                textSize = 13f
                setTextColor(Color.parseColor("#A3A3A3"))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }
            emptyCard.addView(emptyTitle)
            emptyCard.addView(emptySub)
            notesContainer.addView(emptyCard)
            return
        }

        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        notes.forEach { note ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg("#1E1E1E", "#383838", 16)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val callerTv = TextView(this).apply {
                text = note.callerNumber
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tagColor = when (note.category) {
                "Urgent" -> "#F87171"
                "Callback" -> "#FBBF24"
                else -> "#34D399"
            }

            val categoryTag = TextView(this).apply {
                text = note.category.uppercase(Locale.getDefault())
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(tagColor))
                background = roundedBg("#262626", "#404040", 8)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            topRow.addView(callerTv)
            topRow.addView(categoryTag)

            val dateTv = TextView(this).apply {
                text = formatter.format(Date(note.timestampMs))
                textSize = 12f
                setTextColor(Color.parseColor("#A3A3A3"))
                setPadding(0, dp(4), 0, dp(10))
            }

            val transcriptTv = TextView(this).apply {
                text = note.transcript.ifBlank { note.summary }
                textSize = 13f
                setTextColor(Color.parseColor("#E5E5E5"))
                background = roundedBg("#161616", "#2E2E2E", 10)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            }

            val copyBtn = Button(this).apply {
                text = "Copy"
                textSize = 12f
                background = roundedBg("#262626", "#404040", 8)
                setTextColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Caller", note.callerNumber))
                    Toast.makeText(this@NotesActivity, "Copied ${note.callerNumber}", Toast.LENGTH_SHORT).show()
                }
            }

            val callBackBtn = Button(this).apply {
                text = "Call Back"
                textSize = 12f
                background = roundedBg("#262626", "#404040", 8)
                setTextColor(Color.parseColor("#34D399"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(6)
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${note.callerNumber}"))
                    startActivity(dialIntent)
                }
            }

            val deleteBtn = Button(this).apply {
                text = "Delete"
                textSize = 12f
                background = roundedBg("#262626", "#404040", 8)
                setTextColor(Color.parseColor("#F87171"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(6)
                }
                setOnClickListener {
                    Storage.deleteCallNote(this@NotesActivity, note.id)
                    renderNotes()
                }
            }

            btnRow.addView(copyBtn)
            btnRow.addView(callBackBtn)
            btnRow.addView(deleteBtn)

            card.addView(topRow)
            card.addView(dateTv)
            card.addView(transcriptTv)
            card.addView(btnRow)
            notesContainer.addView(card)
        }
    }
}