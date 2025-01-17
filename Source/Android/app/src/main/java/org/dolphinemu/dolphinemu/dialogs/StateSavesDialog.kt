package org.dolphinemu.dolphinemu.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class StateSavesDialog : DialogFragment() {
    inner class StateSaveModel
        (val index: Int, val filename: String, val lastModified: Long) {
        val name: String
            get() {
                val idx = filename.lastIndexOf(File.separatorChar)
                if (idx != -1 && idx < filename.length) return filename.substring(idx + 1)
                return ""
            }
    }

    inner class StateSaveViewHolder(private val dialog: DialogFragment, itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.state_name)
        private val date: TextView = itemView.findViewById(R.id.state_time)
        private val btnLoad: Button =
            itemView.findViewById(R.id.button_load_state)
        private val btnSave: Button =
            itemView.findViewById(R.id.button_save_state)
        private val btnDelete: Button =
            itemView.findViewById(R.id.button_delete)

        fun bind(item: StateSaveModel) {
            val lastModified = item.lastModified
            if (lastModified > 0) {
                name.text = item.name
                date.text =
                    SimpleDateFormat.getDateTimeInstance()
                        .format(Date(lastModified))
                btnDelete.visibility = View.VISIBLE
            } else {
                name.text = ""
                date.text = ""
                btnDelete.visibility = View.INVISIBLE
            }
            btnLoad.isEnabled = item.name.isNotEmpty()
            btnLoad.setOnClickListener {
                NativeLibrary.LoadState(item.index)
                dialog.dismiss()
            }
            btnSave.setOnClickListener {
                NativeLibrary.SaveState(item.index, false)
                dialog.dismiss()
            }
            btnDelete.setOnClickListener {
                val file = File(item.filename)
                if (file.delete()) {
                    name.text = ""
                    date.text = ""
                    btnDelete.visibility = View.INVISIBLE
                }
            }
        }
    }

    inner class StateSavesAdapter(dialog: DialogFragment, gameId: String?) :
        RecyclerView.Adapter<StateSaveViewHolder>() {
        private val dialog: DialogFragment
        private val stateSaves: ArrayList<StateSaveModel>

        init {
            val statePath = DirectoryInitialization.getUserDirectory() + "/StateSaves/"
            val indices = ArrayList<Int>()
            this.dialog = dialog
            stateSaves = ArrayList()
            for (i in 0 until Companion.NUM_STATES) {
                val filename = String.format("%s%s.s%02d", statePath, gameId, i)
                val stateFile = File(filename)
                if (stateFile.exists()) {
                    stateSaves.add(StateSaveModel(i, filename, stateFile.lastModified()))
                } else {
                    indices.add(i)
                }
            }

            for (idx in indices) {
                stateSaves.add(StateSaveModel(idx, "", 0))
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StateSaveViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemView = inflater.inflate(R.layout.list_item_statesave, parent, false)
            return StateSaveViewHolder(dialog, itemView)
        }

        override fun getItemCount(): Int {
            return stateSaves.size
        }

        override fun getItemViewType(position: Int): Int {
            return 0
        }

        override fun onBindViewHolder(holder: StateSaveViewHolder, position: Int) {
            holder.bind(stateSaves[position])
        }
    }

    private var adapter: StateSavesAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val contents = requireActivity().layoutInflater
            .inflate(R.layout.dialog_running_settings, null) as ViewGroup

        val textTitle = contents.findViewById<TextView>(R.id.text_title)
        textTitle.setText(R.string.state_saves)

        val columns = 1
        val recyclerView = contents.findViewById<RecyclerView>(R.id.list_settings)
        val layoutManager: RecyclerView.LayoutManager = GridLayoutManager(context, columns)
        recyclerView.layoutManager = layoutManager
        adapter = StateSavesAdapter(this, requireArguments().getString(ARG_GAME_ID))
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL
            )
        )
        builder.setView(contents)
        return builder.create()
    }

    companion object {
        fun newInstance(gameId: String?): StateSavesDialog {
            val fragment = StateSavesDialog()
            val arguments = Bundle()
            arguments.putString(ARG_GAME_ID, gameId)
            fragment.arguments = arguments
            return fragment
        }

        private const val NUM_STATES = 10
        private const val ARG_GAME_ID = "game_id"
    }
}
