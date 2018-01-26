package com.ecjtu.flesh.presenter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.preference.PreferenceManager
import android.support.design.widget.AppBarLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.ashokvarma.bottomnavigation.BottomNavigationBar
import com.ashokvarma.bottomnavigation.BottomNavigationItem
import com.bumptech.glide.Glide
import com.ecjtu.componentes.activity.AppThemeActivity
import com.ecjtu.flesh.R
import com.ecjtu.flesh.cache.impl.MenuListCacheHelper
import com.ecjtu.flesh.cache.impl.V33CacheHelper
import com.ecjtu.flesh.model.models.V33Model
import com.ecjtu.flesh.ui.activity.MainActivity
import com.ecjtu.flesh.ui.adapter.TabPagerAdapter
import com.ecjtu.flesh.ui.adapter.VideoTabPagerAdapter
import com.ecjtu.flesh.ui.fragment.PageHistoryFragment
import com.ecjtu.flesh.ui.fragment.PageLikeFragment
import com.ecjtu.flesh.util.file.FileUtil
import com.ecjtu.netcore.Constants
import com.ecjtu.netcore.jsoup.SoupFactory
import com.ecjtu.netcore.jsoup.impl.MenuSoup
import com.ecjtu.netcore.model.MenuModel
import com.ecjtu.netcore.network.AsyncNetwork
import com.ecjtu.netcore.network.IRequestCallback
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import kotlin.concurrent.thread


/**
 * Created by KerriGan on 2017/6/2.
 */
class MainActivityDelegate(owner: MainActivity) : Delegate<MainActivity>(owner) {

    companion object {
        private const val KEY_LAST_TAB_ITEM = "key_last_tab_item"
        private const val KEY_APPBAR_LAYOUT_COLLAPSED = "key_appbar_layout_collapse"
        private const val CACHE_MENU_LIST = "menu_list_cache"
    }

    private val mFloatButton = owner.findViewById(R.id.float_button) as FloatingActionButton
    private val mViewPager = owner.findViewById(R.id.view_pager) as ViewPager
    private val mTabLayout = owner.findViewById(R.id.tab_layout) as TabLayout
    private val mAppbarLayout = owner.findViewById(R.id.app_bar) as AppBarLayout
    private var mAppbarExpand = true
    private val mAdapterArray = Array<PagerAdapter?>(2, { index -> null })
    private var mCurrentPagerIndex = 0
    private var mLoadingDialog: AlertDialog? = null
    private var mV33Menu: List<MenuModel>? = null
    private var mV33Cache: Map<String, List<V33Model>>? = null

    init {
        val helper = MenuListCacheHelper(owner.filesDir.absolutePath)
        val lastTabItem = PreferenceManager.getDefaultSharedPreferences(owner).getInt(KEY_LAST_TAB_ITEM + "_" + 0, 0)
        var menuList: MutableList<MenuModel>? = null
        if (helper.get<Any>(CACHE_MENU_LIST + "_" + 0) != null) {
            menuList = helper.get(CACHE_MENU_LIST + "_" + 0)
        }
        if (menuList != null) {
            mViewPager.adapter = TabPagerAdapter(menuList)
            mTabLayout.setupWithViewPager(mViewPager)
            mViewPager.setCurrentItem(lastTabItem)
            mAdapterArray[0] = mViewPager.adapter
        }
        val request = AsyncNetwork()
        request.request(Constants.HOST_MOBILE_URL, null)
        request.setRequestCallback(object : IRequestCallback {
            override fun onSuccess(httpURLConnection: HttpURLConnection?, response: String) {
                val values = SoupFactory.parseHtml(MenuSoup::class.java, response)
                if (values != null) {
                    owner.runOnUiThread {
                        if (mCurrentPagerIndex != 0) {
                            return@runOnUiThread
                        }
                        var localList: List<MenuModel>? = null
                        if (values[MenuSoup::class.java.simpleName] != null) {
                            localList = values[MenuSoup::class.java.simpleName] as List<MenuModel>
                            if (menuList == null && localList != null) {
                                mViewPager.adapter = TabPagerAdapter(localList)
                                mTabLayout.setupWithViewPager(mViewPager)
                                mViewPager.setCurrentItem(lastTabItem)
                                mAdapterArray[0] = mViewPager.adapter
                            } else {
                                var needUpdate = false
                                for (obj in localList) {
                                    if (menuList?.indexOf(obj) ?: 0 < 0) {
                                        menuList?.add(0, obj)
                                        needUpdate = true
                                    }
                                }
                                if (needUpdate) {
                                    mViewPager.adapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
            }
        })

        initView()
    }

    private fun initView() {
        val cacheSize = PreferenceManager.getDefaultSharedPreferences(owner).getLong(com.ecjtu.flesh.Constants.PREF_CACHE_SIZE, com.ecjtu.flesh.Constants.DEFAULT_GLIDE_CACHE_SIZE)
        val cacheStr = Formatter.formatFileSize(owner, cacheSize)
        val glideSize = FileUtil.getGlideCacheSize(owner)
        val glideStr = Formatter.formatFileSize(owner, glideSize)
        val textView = findViewById(R.id.size) as TextView?
        textView?.let {
            textView.setText(String.format("%s/%s", glideStr, cacheStr))
        }
        mFloatButton.setOnClickListener {
            doFloatButton()
        }

        findViewById(R.id.like)?.setOnClickListener {
            val intent = AppThemeActivity.newInstance(owner, PageLikeFragment::class.java)
            owner.startActivity(intent)
            val drawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
            drawerLayout.closeDrawer(Gravity.START)
        }

        findViewById(R.id.cache)?.setOnClickListener {
            val cacheFile = File(owner.cacheDir.absolutePath + "/image_manager_disk_cache")
            val list = FileUtil.getFilesByFolder(cacheFile)
            var ret = 0L
            for (child in list) {
                ret += child.length()
            }
            val size = Formatter.formatFileSize(owner, ret)
            AlertDialog.Builder(owner).setTitle(R.string.cache_size).setMessage(owner.getString(R.string.cached_data_cleaned_or_not, size))
                    .setPositiveButton(R.string.ok, { dialog, which -> thread { Glide.get(owner).clearDiskCache() } })
                    .setNegativeButton(R.string.cancel, null)
                    .create().show()
        }

        findViewById(R.id.disclaimer)?.setOnClickListener {
            AlertDialog.Builder(owner).setTitle(R.string.statement).setMessage(R.string.statement_content)
                    .setPositiveButton(R.string.ok, null)
                    .create().show()
        }

        findViewById(R.id.history)?.setOnClickListener {
            val intent = AppThemeActivity.newInstance(owner, PageHistoryFragment::class.java)
            owner.startActivity(intent)
            val drawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
            drawerLayout.closeDrawer(Gravity.START)
        }

        mAppbarExpand = PreferenceManager.getDefaultSharedPreferences(owner).getBoolean(KEY_APPBAR_LAYOUT_COLLAPSED, false)
        val expand = isAppbarLayoutExpand()
        if (expand) {
            mAppbarLayout.setExpanded(true)
        } else {
            mAppbarLayout.setExpanded(false)
        }
        mAppbarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (verticalOffset == 0) {
                mAppbarExpand = true
            } else if (verticalOffset == -(appBarLayout.height - mTabLayout.height)) {
                mAppbarExpand = false
            }
        }

//        val content = findViewById(R.id.drawer_layout)
//        content?.let {
//            showBg()
//            val bitmap = BitmapFactory.decodeFile(owner.filesDir.absolutePath + "/bg.png")
//            if (bitmap != null) {
//                content.setBackgroundDrawable(BitmapDrawable(bitmap))
//            }
//        }

        val bottomNav = findViewById(R.id.bottom_navigation_bar) as BottomNavigationBar
        bottomNav
                .addItem(BottomNavigationItem(R.drawable.ic_image, "Image"))
                .addItem(BottomNavigationItem(R.drawable.ic_video, "Video"))
                .initialise()
        bottomNav.setTabSelectedListener(object : BottomNavigationBar.OnTabSelectedListener {
            override fun onTabUnselected(position: Int) {
            }

            override fun onTabSelected(position: Int) {
                mCurrentPagerIndex = position
                when (position) {
                    0 -> {
                        mViewPager.adapter = mAdapterArray[0]
                    }

                    1 -> {
                        if (mAdapterArray[1] != null) {
                            mViewPager.adapter = mAdapterArray[1]
                        } else {
                            mViewPager.adapter = null
                            val req = AsyncNetwork().apply {
                                request("https://Kerr1Gan.github.io/flesh/v33a.json", null)
                                setRequestCallback(object : IRequestCallback {
                                    override fun onSuccess(httpURLConnection: HttpURLConnection?, response: String) {
                                        val menuModel = arrayListOf<MenuModel>()
                                        val map = linkedMapOf<String, List<V33Model>>()
                                        try {
                                            val jObj = JSONArray(response)
                                            for (i in 0 until jObj.length()) {
                                                val jTitle = jObj[i] as JSONObject
                                                val title = jTitle.optString("title")
                                                val list = jTitle.optJSONArray("list")
                                                val modelList = arrayListOf<V33Model>()
                                                for (j in 0 until list.length()) {
                                                    val v33Model = V33Model()
                                                    val jItem = list[j] as JSONObject
                                                    v33Model.baseUrl = jItem.optString("baseUrl")
                                                    v33Model.imageUrl = jItem.optString("imageUrl")
                                                    v33Model.title = jItem.optString("title")
                                                    v33Model.videoUrl = jItem.optString("videoUrl")
                                                    modelList.add(v33Model)
                                                }
                                                map.put(title, modelList)
                                                val model = MenuModel(title, "")
                                                menuModel.add(model)
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }

                                        mLoadingDialog?.cancel()
                                        mLoadingDialog = null
                                        owner.runOnUiThread {
                                            if (mCurrentPagerIndex == 1) {
                                                mViewPager.adapter = if (mAdapterArray[1] == null) {
                                                    mAdapterArray[1] = VideoTabPagerAdapter(menuModel)
                                                    (mAdapterArray[1] as VideoTabPagerAdapter).setMenuChildList(map)
                                                    mAdapterArray[1]
                                                } else mAdapterArray[1]
                                                mViewPager.adapter?.notifyDataSetChanged()
                                                mV33Menu = menuModel
                                                mV33Cache = map
                                            }
                                        }
                                    }
                                })
                            }
                            if (mLoadingDialog == null) {
                                mLoadingDialog = AlertDialog.Builder(owner).setTitle("加载中").setMessage("需要一小会时间")
                                        .setNegativeButton("取消", { dialog, which ->
                                            req.cancel()
                                        })
                                        .setCancelable(false)
                                        .setOnCancelListener {
                                            mLoadingDialog = null
                                        }.create()
                                mLoadingDialog?.show()
                            }
                            thread {
                                val helper = V33CacheHelper(owner.filesDir.absolutePath)
                                val helper2 = MenuListCacheHelper(owner.filesDir.absolutePath)
                                mV33Menu = helper2.get("v33menu")
                                mV33Cache = helper.get("v33cache")
                                val localMenu = mV33Menu
                                val localCache = mV33Cache
                                if (localMenu != null && localCache != null) {
                                    mLoadingDialog?.cancel()
                                    mLoadingDialog = null
                                }
                                owner.runOnUiThread {
                                    if (mCurrentPagerIndex == 1 && localMenu != null && localCache != null) {
                                        mViewPager.adapter = if (mAdapterArray[1] == null) {
                                            mAdapterArray[1] = VideoTabPagerAdapter(localMenu)
                                            (mAdapterArray[1] as VideoTabPagerAdapter).setMenuChildList(localCache as MutableMap<String, List<V33Model>>)
                                            mAdapterArray[1]
                                        } else mAdapterArray[1]
                                        mViewPager.adapter?.notifyDataSetChanged()
                                    }
                                }
                            }
                        }
                    }
                }
                mViewPager.adapter?.notifyDataSetChanged()
            }

            override fun onTabReselected(position: Int) {
            }

        })
    }

    fun onStop() {
        var index = 0
        for (adapter in mAdapterArray) {
            adapter?.let {
                (adapter as TabPagerAdapter).onStop(owner)
                val helper = MenuListCacheHelper(owner.filesDir.absolutePath)
                helper.put(CACHE_MENU_LIST + "_" + index, (adapter as TabPagerAdapter).menu)

                PreferenceManager.getDefaultSharedPreferences(owner).edit().
                        putInt(KEY_LAST_TAB_ITEM + "_" + index, mTabLayout.selectedTabPosition).
                        putBoolean(KEY_APPBAR_LAYOUT_COLLAPSED, isAppbarLayoutExpand()).
                        apply()
            }
            index++
        }

        thread {
            val helper = V33CacheHelper(owner.filesDir.absolutePath)
            val helper2 = MenuListCacheHelper(owner.filesDir.absolutePath)
            if (mV33Menu != null && mV33Cache != null) {
                helper2.put("v33menu", mV33Menu)
                helper.put("v33cache", mV33Cache)
            }

        }
    }

    fun onResume() {
        mViewPager.adapter?.let {
            (mViewPager.adapter as TabPagerAdapter).onResume()
        }
    }

    fun onDestroy() {
//        thread {
//            val content = findViewById(R.id.drawer_layout)
//            content?.let {
//                val bitmap = convertView2Bitmap(content, content.width, content.height)
//                val file = File(owner.filesDir, "bg.png")
//                var os: OutputStream? = null
//                try {
//                    os = FileOutputStream(file)
//                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
//                    bitmap.recycle()
//                    os.close()
//                } catch (ex: Exception) {
//                } finally {
//                    if (os != null) {
//                        try {
//                            os.close()
//                        } catch (ex: Exception) {
//                        }
//                    }
//                    bitmap.recycle()
//                }
//
//            }
//        }
    }

//    fun hideBg() {
//        val vg = findViewById(R.id.drawer_layout) as ViewGroup
//        for (i in 0 until vg.childCount) {
//            vg.getChildAt(i).visibility = View.VISIBLE
//        }
//    }
//
//    fun showBg() {
//        val vg = findViewById(R.id.drawer_layout) as ViewGroup
//        for (i in 0 until vg.childCount) {
//            vg.getChildAt(i).visibility = View.INVISIBLE
//        }
//    }

    fun isAppbarLayoutExpand(): Boolean = mAppbarExpand

    fun convertView2Bitmap(view: View, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun doFloatButton() {
        val position = mTabLayout.selectedTabPosition
        var recyclerView: RecyclerView? = null
        var size = 0
        mViewPager.adapter?.let {
            recyclerView = (mViewPager.adapter as TabPagerAdapter).getViewStub(position) as RecyclerView?
            size = (mViewPager.adapter as TabPagerAdapter).getListSize(position)
        }
        val snake = Snackbar.make(findViewById(R.id.content)!!, "", Snackbar.LENGTH_SHORT)
        if (snake.view is LinearLayout) {
            val vg = snake.view as LinearLayout
            val layout = LayoutInflater.from(owner).inflate(R.layout.layout_quick_jump, vg, false) as ViewGroup

            val local = layout.findViewById(R.id.seek_bar) as SeekBar
            val pos = layout.findViewById(R.id.position) as TextView

            val listener = { v: View ->
                if (position != mTabLayout.selectedTabPosition) {
                    snake.dismiss()
                } else {
                    when (v.id) {
                        R.id.top -> {
                            recyclerView?.let {
                                (recyclerView?.layoutManager as LinearLayoutManager).scrollToPosition(0)
                            }
                        }

                        R.id.mid -> {
                            recyclerView?.let {
                                var jumpPos = Integer.valueOf(pos.text.toString()) - 2
                                if (jumpPos < 0) jumpPos = 0
                                (recyclerView?.layoutManager as LinearLayoutManager).scrollToPosition(jumpPos)
                            }
                        }

                        R.id.bottom -> {
                            recyclerView?.let {
                                (recyclerView?.layoutManager as LinearLayoutManager).scrollToPosition(size - 2)
                            }
                        }
                    }
                    snake.dismiss()
                }
                Unit
            }
            layout.findViewById(R.id.top).setOnClickListener(listener)
            layout.findViewById(R.id.mid).setOnClickListener(listener)
            layout.findViewById(R.id.bottom).setOnClickListener(listener)

            local.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pos.setText(progress.toString())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })
            local.max = size
            if (recyclerView != null) {
                val curPos = (recyclerView?.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                local.progress = curPos
            }
            layout.findViewById(R.id.mid).setOnClickListener(listener)
            vg.addView(layout)
        }
        snake.show()
    }
}