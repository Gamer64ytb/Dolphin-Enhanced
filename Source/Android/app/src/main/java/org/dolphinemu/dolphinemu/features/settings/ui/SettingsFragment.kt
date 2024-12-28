package org.dolphinemu.dolphinemu.features.settings.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import java.util.EnumMap

class SettingsFragment : Fragment(), SettingsFragmentView {
    private lateinit var mPresenter: SettingsFragmentPresenter
    private var mSettingsList: ArrayList<SettingsItem>? = null
    private var mActivity: SettingsActivity? = null
    private var mAdapter: SettingsAdapter? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mActivity = context as SettingsActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true
        val args = arguments
        val menuTag = args!!.getSerializable(ARGUMENT_MENU_TAG) as MenuTag?
        val gameId = requireArguments().getString(ARGUMENT_GAME_ID)

        mPresenter = SettingsFragmentPresenter(mActivity!!)
        mAdapter = SettingsAdapter(mActivity!!)

        mPresenter.onCreate(menuTag!!, gameId, args)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments
        val menuTag = args!!.getSerializable(ARGUMENT_MENU_TAG) as MenuTag?

        if (titles.containsKey(menuTag)) {
            requireActivity().setTitle(titles[menuTag]!!)
        }

        //LinearLayoutManager manager = new LinearLayoutManager(mActivity);
        val recyclerView = view.findViewById<RecyclerView>(R.id.list_settings)

        val mgr = GridLayoutManager(mActivity, 2)
        mgr.spanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = mAdapter!!.getItemViewType(position)
                if (SettingsItem.TYPE_INPUT_BINDING == viewType &&
                    Settings.SECTION_BINDINGS == mAdapter!!.getSettingSection(
                        position
                    )
                ) return 1
                return 2
            }
        }

        recyclerView.adapter = mAdapter
        recyclerView.layoutManager = mgr
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL
            )
        )

        showSettingsList(mActivity!!.getSettings())
    }

    override fun onDetach() {
        super.onDetach()
        mActivity = null

        if (mAdapter != null) {
            mAdapter!!.closeDialog()
        }
    }

    override fun showSettingsList(settings: Settings?) {
        if (mSettingsList == null && settings != null) {
            mSettingsList = mPresenter.loadSettingsList(settings)
        }

        if (mSettingsList != null) {
            mAdapter!!.setSettings(mSettingsList)
        }
    }

    fun closeDialog() {
        mAdapter!!.closeDialog()
    }

    companion object {
        private const val ARGUMENT_MENU_TAG = "menu_tag"
        private const val ARGUMENT_GAME_ID = "game_id"

        private val titles: MutableMap<MenuTag, Int> = EnumMap(MenuTag::class.java)

        init {
            titles[MenuTag.CONFIG] = R.string.preferences_settings
            titles[MenuTag.CONFIG_GENERAL] = R.string.general_submenu
            titles[MenuTag.CONFIG_INTERFACE] = R.string.interface_submenu
            titles[MenuTag.CONFIG_GAME_CUBE] = R.string.gamecube_submenu
            titles[MenuTag.CONFIG_WII] = R.string.wii_submenu
            titles[MenuTag.WIIMOTE] = R.string.grid_menu_wiimote_settings
            titles[MenuTag.WIIMOTE_EXTENSION] =
                R.string.wiimote_extensions
            titles[MenuTag.GCPAD_TYPE] =
                R.string.grid_menu_gcpad_settings
            titles[MenuTag.GRAPHICS] =
                R.string.grid_menu_graphics_settings
            titles[MenuTag.HACKS] = R.string.hacks_submenu
            titles[MenuTag.DEBUG] = R.string.debug_submenu
            titles[MenuTag.ENHANCEMENTS] = R.string.enhancements_submenu
            titles[MenuTag.GCPAD_1] = R.string.controller_0
            titles[MenuTag.GCPAD_2] = R.string.controller_1
            titles[MenuTag.GCPAD_3] = R.string.controller_2
            titles[MenuTag.GCPAD_4] = R.string.controller_3
            titles[MenuTag.WIIMOTE_1] = R.string.wiimote_4
            titles[MenuTag.WIIMOTE_2] = R.string.wiimote_5
            titles[MenuTag.WIIMOTE_3] = R.string.wiimote_6
            titles[MenuTag.WIIMOTE_4] = R.string.wiimote_7
            titles[MenuTag.WIIMOTE_EXTENSION_1] =
                R.string.wiimote_extension_4
            titles[MenuTag.WIIMOTE_EXTENSION_2] =
                R.string.wiimote_extension_5
            titles[MenuTag.WIIMOTE_EXTENSION_3] =
                R.string.wiimote_extension_6
            titles[MenuTag.WIIMOTE_EXTENSION_4] =
                R.string.wiimote_extension_7
        }

        fun newInstance(menuTag: MenuTag?, gameId: String?, extras: Bundle?): Fragment {
            val fragment = SettingsFragment()

            val arguments = Bundle()
            if (extras != null) {
                arguments.putAll(extras)
            }

            arguments.putSerializable(ARGUMENT_MENU_TAG, menuTag)
            arguments.putString(ARGUMENT_GAME_ID, gameId)

            fragment.arguments = arguments
            return fragment
        }
    }
}
