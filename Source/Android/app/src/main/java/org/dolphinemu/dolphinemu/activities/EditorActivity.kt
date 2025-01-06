package org.dolphinemu.dolphinemu.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.services.GameFileCacheService
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.lang.ref.WeakReference
import com.google.android.material.materialswitch.MaterialSwitch

class EditorActivity : AppCompatActivity() {
    class CheatEntry {
        var name = ""
        var info = ""
        var type = TYPE_NONE
        var active = false

        companion object {
            const val TYPE_NONE = -1
            const val TYPE_AR = 0
            const val TYPE_GECKO = 1
        }
    }

    private class IniTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable) {
            var offset = 0
            val sepsize = System.lineSeparator().length
            val reader = BufferedReader(StringReader(s.toString()))
            try {
                var line = reader.readLine()
                while (line != null) {
                    val len = line.length
                    var isFirstChar = true
                    var commentStart = -1
                    var codenameStart = -1
                    var sectionStart = -1
                    var sectionEnd = -1
                    for (i in 0 until len) {
                        when (line[i]) {
                            '\t', ' ' -> {}
                            '#' -> {
                                if (commentStart == -1) commentStart = i
                                isFirstChar = false
                            }

                            '$' -> {
                                codenameStart = if (isFirstChar) i else -1
                                isFirstChar = false
                                sectionStart = if (isFirstChar) i else -1
                                isFirstChar = false
                            }

                            '[' -> {
                                sectionStart = if (isFirstChar) i else -1
                                isFirstChar = false
                            }

                            ']' -> {
                                if (sectionStart != -1 && sectionEnd == -1) {
                                    sectionEnd = i
                                } else {
                                    sectionStart = -1
                                    sectionEnd = -1
                                }
                                isFirstChar = false
                            }

                            else -> isFirstChar = false
                        }
                    }

                    if (commentStart != -1) {
                        s.setSpan(
                            ForegroundColorSpan(Color.GRAY), offset + commentStart,
                            offset + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else if (codenameStart != -1) {
                        s.setSpan(
                            ForegroundColorSpan(Color.MAGENTA), offset + codenameStart,
                            offset + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else if (sectionStart != -1 && sectionEnd != -1) {
                        s.setSpan(
                            ForegroundColorSpan(Color.BLUE), offset + sectionStart,
                            offset + sectionEnd + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    offset += len + sepsize
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                // ignore
            }
        }
    }

    private class DownloaderTask(editor: EditorActivity?) :
        AsyncTask<String?, String?, String?>() {
        private var selection = 0
        private var content: String? = null
        private val activity =
            WeakReference(editor)

        override fun onPreExecute() {
            content = activity.get()!!.editor!!.text.toString()
            activity.get()!!.progressBar!!.visibility = View.VISIBLE
            selection = activity.get()!!.editor!!.selectionStart
        }

        override fun doInBackground(vararg gametdbId: String?): String? {
            val downloader = OkHttpClient()
            var response: Response? = null
            val request = Request.Builder()
                .url("https://codes.rc24.xyz/txt.php?txt=" + gametdbId[0])
                .addHeader("Cookie", "challenge=BitMitigate.com")
                .build()

            var result = ""
            try {
                response = downloader.newCall(request).execute()
                if (response != null && response.code() == 200 && response.body() != null) {
                    result = response.body()!!.string()
                }
            } catch (e: IOException) {
                // ignore
            }
            return processGeckoCodes(result)
        }

        override fun onPostExecute(param: String?) {
            if (activity.get() != null) {
                activity.get()!!.editor!!.setText(param)
                activity.get()!!.editor!!.setSelection(selection)
                activity.get()!!.progressBar!!.visibility = View.INVISIBLE
                activity.get()!!.loadCheatList()
            }
        }

        fun processGeckoCodes(codes: String): String? {
            if (codes.isEmpty() || codes[0] == '<') return content

            var state = 0
            val codeSB = StringBuilder()
            var reader = BufferedReader(StringReader(codes))
            try {
                reader.readLine()
                reader.readLine()
                reader.readLine()
                //
                var line = reader.readLine()
                while (line != null) {
                    if (state == 0) {
                        // try read name
                        if (line.isNotEmpty()) {
                            if (line[0] == '*') {
                                // read comments
                                codeSB.append(line)
                                codeSB.append('\n')
                            } else {
                                // read name
                                val pos = line.lastIndexOf(' ')
                                if (pos != -1 && line.length > pos + 2 && line[pos + 1] == '[') {
                                    line = line.substring(0, pos)
                                }
                                codeSB.append('$')
                                codeSB.append(line)
                                codeSB.append('\n')
                                state = 1
                            }
                        }
                    } else if (state == 1) {
                        // read codes
                        if (line.length == 17) {
                            val codePart = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                            if (codePart.size != 2 || codePart[0].length != 8 || codePart[1].length != 8) {
                                codeSB.append('*')
                                state = 2
                            }
                            codeSB.append(line)
                            codeSB.append('\n')
                        } else {
                            state = if (line.isEmpty()) 0 else 2
                        }
                    } else if (state == 2) {
                        // read comments
                        if (line.isNotEmpty()) {
                            codeSB.append('*')
                            codeSB.append(line)
                            codeSB.append('\n')
                        } else {
                            // goto read name
                            state = 0
                        }
                    }
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                // ignore
            }

            var isInsert = false
            val configSB = StringBuilder()
            reader = BufferedReader(StringReader(content))
            try {
                var line = reader.readLine()
                while (line != null) {
                    configSB.append(line)
                    configSB.append('\n')
                    if (!isInsert && line.contains("[Gecko]")) {
                        selection = configSB.length
                        configSB.append(codeSB.toString())
                        isInsert = true
                    }
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                // ignore
            }

            if (!isInsert) {
                configSB.append("\n[Gecko]\n")
                selection = configSB.length
                configSB.append(codeSB.toString())
            }

            return configSB.toString()
        }
    }

    internal inner class CheatEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        private var model: CheatEntry? = null
        private val textName: TextView = itemView.findViewById(R.id.text_setting_name)
        private val textDescription: TextView =
            itemView.findViewById(R.id.text_setting_description)
        private val switch: MaterialSwitch = itemView.findViewById(R.id.switch_widget)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(entry: CheatEntry) {
            model = entry
            textName.text = entry.name
            textDescription.text = entry.info
            switch.isChecked = entry.active
        }

        override fun onClick(v: View) {
            toggleCheatEntry(model!!)
            switch.isChecked = model!!.active
        }
    }

    internal inner class CheatEntryAdapter : RecyclerView.Adapter<CheatEntryViewHolder>() {
        private var dataset: List<CheatEntry>? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheatEntryViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemView = inflater.inflate(R.layout.list_item_setting_switch, parent, false)
            return CheatEntryViewHolder(itemView)
        }

        override fun getItemCount(): Int {
            return if (dataset != null) dataset!!.size else 0
        }

        override fun onBindViewHolder(holder: CheatEntryViewHolder, position: Int) {
            holder.bind(dataset!![position])
        }

        fun loadCodes(list: List<CheatEntry>?) {
            dataset = list
            notifyDataSetChanged()
        }
    }

    private val SECTION_NONE = -1
    private val SECTION_AR = 0
    private val SECTION_AR_ENABLED = 1
    private val SECTION_GECKO = 2
    private val SECTION_GECKO_ENABLED = 3
    private val SECTIONS =
        arrayOf("[ActionReplay]", "[ActionReplay_Enabled]", "[Gecko]", "[Gecko_Enabled]")

    private var gameId: String? = null
    private var gameFile: GameFile? = null
    private var cancelSave = false
    private var editor: EditText? = null
    private var btnConfirm: Button? = null
    private var listView: RecyclerView? = null
    private var adapter: CheatEntryAdapter? = null
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val gamePath = intent.getStringExtra(ARG_GAME_PATH)
        val gameFile = GameFileCacheService.addOrGet(gamePath)

        gameId = gameFile.getGameId()

        title = gameId

        // code list
        listView = findViewById(R.id.code_list)
        adapter = CheatEntryAdapter()
        listView!!.setAdapter(adapter)
        listView!!.addItemDecoration(
            DividerItemDecoration(
                this,
                DividerItemDecoration.VERTICAL
            )
        )
        listView!!.setLayoutManager(LinearLayoutManager(this))

        // code editor
        editor = findViewById(R.id.code_content)

        this.gameFile = gameFile
        progressBar = findViewById(R.id.progress_bar)
        progressBar!!.visibility = View.INVISIBLE

        editor!!.addTextChangedListener(IniTextWatcher())
        editor!!.setHorizontallyScrolling(true)

        cancelSave = false
        val buttonCancel = findViewById<Button>(R.id.button_cancel)
        buttonCancel.setOnClickListener {
            cancelSave = true
            finish()
        }

        btnConfirm = findViewById(R.id.button_confirm)
        btnConfirm!!.setOnClickListener { toggleListView(editor!!.visibility == View.VISIBLE) }

        // show
        setGameSettings(editor!!)
        loadCheatList()
        toggleListView(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!cancelSave) {
            acceptCheatCode(editor!!)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_download_gecko) {
            downloadGeckoCodes(gameFile!!.getGameTdbId(), editor)
            return true
        }
        return false
    }

    private fun toggleListView(isShowList: Boolean) {
        if (isShowList) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            listView!!.visibility = View.VISIBLE
            editor!!.visibility = View.INVISIBLE
            loadCheatList()
            btnConfirm!!.setText(R.string.edit_cheat)
        } else {
            listView!!.visibility = View.INVISIBLE
            editor!!.visibility = View.VISIBLE
            btnConfirm!!.setText(R.string.cheat_list)
        }
    }

    private fun toggleCheatEntry(entry: CheatEntry) {
        val section =
            if (entry.type == CheatEntry.TYPE_AR) "[ActionReplay_Enabled]" else "[Gecko_Enabled]"
        var k = -1
        val content = editor!!.text

        if (entry.active) {
            // remove enabled cheat code
            var isEnabledSection = false
            var target = section
            for (i in content.indices) {
                val c = content[i]
                if (c == '[') {
                    // section begin
                    isEnabledSection = false
                    k = i
                } else if (c == '$') {
                    k = if (isEnabledSection) {
                        // cheat name begin
                        i
                    } else {
                        -1
                    }
                } else if (k != -1) {
                    if (target.length > i - k) {
                        // check equals
                        if (target[i - k] != c) {
                            k = -1
                        }
                    } else if (c == '\n') {
                        if (isEnabledSection) {
                            // delete cheat name
                            content.delete(k, i + 1)
                            break
                        } else {
                            // search cheat name
                            isEnabledSection = true
                            target = entry.name
                            k = -1
                        }
                    }
                }
            }
        } else {
            // enable cheat code
            var insert = false
            for (i in content.indices) {
                val c = content[i]
                if (c == '[') {
                    // section begin
                    k = i
                } else if (k != -1) {
                    if (section.length > i - k) {
                        // check equals
                        if (section[i - k] != c) {
                            k = -1
                        }
                    } else if (c == '\n') {
                        // insert cheat name
                        content.insert(i + 1, entry.name + "\n")
                        insert = true
                        break
                    }
                }
            }

            if (!insert) {
                if (k == -1) {
                    if (content[content.length - 1] != '\n') {
                        content.append("\n")
                    }
                    content.append(section)
                }
                content.append("\n")
                content.append(entry.name)
                content.append("\n")
            }
        }

        entry.active = !entry.active
    }

    private fun loadCheatList() {
        var section = SECTION_NONE
        var entry = CheatEntry()
        val lines = editor!!.text.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val list: MutableList<CheatEntry> = ArrayList()
        for (line in lines) {
            if (line.isEmpty()) {
                continue
            }

            when (line[0]) {
                '*' -> if (entry.name.isNotEmpty()) {
                    // cheat description
                    entry.info += line
                }

                '$' -> {
                    // new cheat entry begin
                    if (entry.type != CheatEntry.TYPE_NONE) {
                        addCheatEntry(list, entry)
                        entry = CheatEntry()
                    }
                    entry.name = line
                    when (section) {
                        SECTION_AR -> {
                            entry.type = CheatEntry.TYPE_AR
                            entry.active = false
                        }

                        SECTION_AR_ENABLED -> {
                            entry.type = CheatEntry.TYPE_AR
                            entry.active = true
                        }

                        SECTION_GECKO -> {
                            entry.type = CheatEntry.TYPE_GECKO
                            entry.active = false
                        }

                        SECTION_GECKO_ENABLED -> {
                            entry.type = CheatEntry.TYPE_GECKO
                            entry.active = true
                        }

                        SECTION_NONE -> {}
                    }
                }

                '[' -> {
                    // new section begin
                    if (section != SECTION_NONE) {
                        if (entry.type != CheatEntry.TYPE_NONE) {
                            addCheatEntry(list, entry)
                            entry = CheatEntry()
                        }
                        section = SECTION_NONE
                    }
                    // is cheat section?
                    var i = 0
                    while (i < SECTIONS.size) {
                        val index = line.indexOf(SECTIONS[i])
                        if (index != -1) {
                            section = i
                        }
                        ++i
                    }
                }
            }
        }

        // last one
        if (entry.type != CheatEntry.TYPE_NONE) {
            addCheatEntry(list, entry)
        }

        adapter!!.loadCodes(list)
    }

    private fun addCheatEntry(list: MutableList<CheatEntry>, entry: CheatEntry) {
        var isSaved = false
        for (i in list.indices) {
            val code = list[i]
            if (code.name == entry.name) {
                code.active = entry.active or code.active
                if (entry.info.isNotEmpty()) {
                    code.info = entry.info
                }
                isSaved = true
            }
        }

        if (!isSaved) {
            list.add(entry)
        }
    }

    private val configReader: BufferedReader?
        get() {
            val filename = DirectoryInitialization.getLocalSettingFile(gameId)
            val configFile = File(filename)
            var reader: BufferedReader? = null

            if (configFile.exists()) {
                reader = try {
                    BufferedReader(FileReader(configFile))
                } catch (e: IOException) {
                    null
                }
            } else if (gameId!!.length > 3) {
                try {
                    val path = "Sys/GameSettings/$gameId.ini"
                    reader =
                        BufferedReader(InputStreamReader(assets.open(path)))
                } catch (e: IOException) {
                    reader = null
                }

                if (reader == null) {
                    try {
                        val path =
                            "Sys/GameSettings/" + gameId!!.substring(0, 3) + ".ini"
                        reader =
                            BufferedReader(InputStreamReader(assets.open(path)))
                    } catch (e: IOException) {
                        reader = null
                    }
                }
            }

            return reader
        }

    private fun setGameSettings(editCode: EditText) {
        val reader = configReader ?: return

        var count = 0
        val indices = intArrayOf(-1, -1, -1, -1)
        val sb = StringBuilder()
        try {
            var line = reader.readLine()
            while (line != null) {
                line = trimString(line)
                if (line.isNotEmpty() && line[0] == '[') {
                    // is cheat section?
                    for (i in SECTIONS.indices) {
                        val index = line.indexOf(SECTIONS[i])
                        if (index != -1) {
                            indices[i] = count + index
                        }
                    }
                }
                //
                count += line.length + 1
                sb.append(line)
                sb.append(System.lineSeparator())
                line = reader.readLine()
            }
        } catch (e: Exception) {
            // ignore
        }

        var cursorPos = 0
        for (i in SECTIONS.indices) {
            if (indices[i] < 0) {
                sb.append(System.lineSeparator())
                sb.append(SECTIONS[i])
                sb.append(System.lineSeparator())
                count += SECTIONS[i].length + 2
                cursorPos = count
            } else if (indices[i] > cursorPos) {
                cursorPos = indices[i] + SECTIONS[i].length + 1
            }
        }

        val content = sb.toString()
        if (cursorPos > content.length) cursorPos = content.length
        editCode.setText(content)
        editCode.setSelection(cursorPos)
    }

    private fun downloadGeckoCodes(gametdbId: String, editCode: EditText?) {
        DownloaderTask(this).execute(gametdbId)
    }

    private fun trimString(text: String): String {
        var len = text.length
        var st = 0

        while ((st < len) && (text[st] <= ' ' || text[st].code == 160)) {
            st++
        }

        while ((st < len) && (text[len - 1] <= ' ' || text[len - 1].code == 160)) {
            len--
        }

        return if (((st > 0) || (len < text.length))) text.substring(st, len) else text
    }

    private fun acceptCheatCode(editCode: EditText) {
        var configSB = StringBuilder()
        val content = editCode.text.toString()
        val reader = BufferedReader(StringReader(content))
        try {
            var line = reader.readLine()
            var codeSB = StringBuilder()
            while (line != null) {
                line = trimString(line)
                // encrypted ar code: P28E-EJY7-26PM5
                val blocks = line.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (blocks.size == 3 && blocks[0].length == 4 && blocks[1].length == 4 && blocks[2].length == 5) {
                    codeSB.append(blocks[0])
                    codeSB.append(blocks[1])
                    codeSB.append(blocks[2])
                    codeSB.append('\n')
                } else {
                    if (codeSB.isNotEmpty()) {
                        val encrypted = codeSB.toString()
                        val arcode = NativeLibrary.DecryptARCode(encrypted)
                        if (arcode.isNotEmpty()) {
                            configSB.append(arcode)
                        } else {
                            configSB.append(encrypted)
                        }
                        codeSB = StringBuilder()
                    }
                    configSB.append(line)
                    configSB.append('\n')
                }

                line = reader.readLine()
            }
            if (codeSB.isNotEmpty()) {
                configSB.append(NativeLibrary.DecryptARCode(codeSB.toString()))
            }
        } catch (e: IOException) {
            configSB = StringBuilder()
            configSB.append(content)
        }

        val filename = DirectoryInitialization.getLocalSettingFile(gameId)
        var saved = false
        try {
            val writer = BufferedWriter(FileWriter(filename))
            writer.write(configSB.toString())
            writer.close()
            saved = true
        } catch (e: Exception) {
            // ignore
        }
        Toast.makeText(
            this, if (saved) R.string.toast_save_code_ok else R.string.toast_save_code_no,
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val ARG_GAME_PATH = "game_path"

        @JvmStatic
        fun launch(context: Context, gamePath: String?) {
            val settings = Intent(context, EditorActivity::class.java)
            settings.putExtra(ARG_GAME_PATH, gamePath)
            context.startActivity(settings)
        }
    }
}
