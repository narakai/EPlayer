package com.eplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_media_player.*


class MediaPlayerActivity : AppCompatActivity(), View.OnClickListener, SeekBar.OnSeekBarChangeListener,
    SurfaceHolder.Callback {

    companion object {
        private const val PATH = "path"
    }

    private lateinit var path: String
    private lateinit var eMediaPlayer: EasyMediaPlayer
    private var mProgress = 0
    private var screenOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private var isPlayComplete = false //播放是否完成


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
//        }
        //supportActionBar!!.hide()
        path = intent.getStringExtra(PATH)
        initPlayer()
        initView()
    }


    //初始化播放器
    private fun initPlayer() {

        if (TextUtils.isEmpty(path)) return

        eMediaPlayer = EasyMediaPlayer()
        //设置播放源路径
        try {
            eMediaPlayer.setDataSource(path)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        //监听尺寸的改变
        eMediaPlayer.setOnVideoSizeChangedListener { mediaPlayer, width, height ->
            LogUtil.d("视频size改变=" + width + "--" + height)
        }

        //设置准备监听
        eMediaPlayer.setOnPreparedListener {
            //开始播放
            eMediaPlayer.start()
            //UI线程执行
            runOnUiThread {
                //改变Surfaceview的宽高
                //changeSurfaceSize()
                //播放进度相关
                tv_current_position.setText(
                    StringUtils.generateStandardTime(Math.max(eMediaPlayer.getCurrentPosition(), 0))
                )
                tv_duration.setText(StringUtils.generateStandardTime(Math.max(eMediaPlayer.getDuration(), 0)))
                seekbar.setMax(Math.max(eMediaPlayer.getDuration(), 0).toInt())
                seekbar.setProgress(Math.max(eMediaPlayer.getCurrentPosition(), 0).toInt())
            }

        }

        //设置错误监听
        eMediaPlayer.setOnErrorListener { mp, what, extra ->
            Log.d("tag", "发生错误：$what,$extra")
            false
        }

        //完成监听
        eMediaPlayer.setOnCompletionListener {
            isPlayComplete = true
            iv_pause_play.setImageResource(R.mipmap.iv_replay)
        }

        //播放进度监听
        eMediaPlayer.setOnCurrentPositionListener { current, duration ->
            runOnUiThread {
                if (eMediaPlayer.isPlaying) {
                    //设置当前播放时间
                    tv_current_position.setText(StringUtils.generateStandardTime(current))
                    //播放进度
                    seekbar.setProgress(current.toInt())
                }
            }
        }

        //准备播放
        try {
            //设置播放器参数，指定视频解码器名称为h264_mediacodec
            eMediaPlayer.setOption(EasyMediaPlayer.OPT_CATEGORY_PLAYER, "vcodec", "h264_mediacodec")
            eMediaPlayer.prepareAsync()

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    //初始化view
    private fun initView() {
        screenOrientation = resources.configuration.orientation
        iv_pause_play.setOnClickListener(this)
        seekbar.setOnSeekBarChangeListener(this)
        //横竖屏切换
        iv_orientation.setImageResource(if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) R.mipmap.iv_land else R.mipmap.iv_port)
        iv_orientation.setOnClickListener(this)

        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                layout_operation.visibility =
                    if (layout_operation.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            true
        }
    }

    override fun onPause() {
        super.onPause()
        eMediaPlayer.pause()
    }

    override fun onResume() {
        super.onResume()
        eMediaPlayer.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        eMediaPlayer.stop()
        eMediaPlayer.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //屏幕方向变化
        if (newConfig.orientation != screenOrientation) {
            screenOrientation = newConfig.orientation
            iv_orientation.setImageResource(if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) R.mipmap.iv_land else R.mipmap.iv_port)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        LogUtil.d("Surface的宽高=" + width + "----" + height)
        LogUtil.d("屏幕宽高="+Utils.getScreenWidth(this)+"---"+Utils.getScreenHeight(this))
        eMediaPlayer.surfaceChange(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {}

    override fun surfaceCreated(holder: SurfaceHolder?) {
        //设置播放器的渲染界面,这个surfaceview会传递到NDK底层
        eMediaPlayer.setDisplay(surfaceView.holder)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            mProgress = progress
            seekBar.setProgress(progress)
            //设置当前播放时间
            tv_current_position.setText(StringUtils.generateStandardTime(mProgress.toLong()))
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        //改变视频播放进度
        eMediaPlayer.seekTo(mProgress.toFloat())
        if (isPlayComplete){
            iv_pause_play.setImageResource(R.drawable.ic_player_pause)
            isPlayComplete = false
        }
    }

    override fun onClick(v: View) {
        //播放暂停
        if (v.id == R.id.iv_pause_play) {
            if (eMediaPlayer.isPlaying && !isPlayComplete) { //暂停
                eMediaPlayer.pause()
                iv_pause_play.setImageResource(R.drawable.ic_player_play)
            } else if (!isPlayComplete) { //播放
                eMediaPlayer.resume()
                iv_pause_play.setImageResource(R.drawable.ic_player_pause)
            } else if (isPlayComplete) { //重播
                eMediaPlayer.seekTo(0f)
                isPlayComplete = false
                iv_pause_play.setImageResource(R.drawable.ic_player_pause)
            }
        }

        //横竖屏切换
        if (v.id == R.id.iv_orientation) {
            requestedOrientation =
                if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_egl, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var filter: Filter = Filter.NONE
        when (item.itemId) {
            R.id.action_original -> filter = Filter.NONE
            R.id.action_blackwhite -> filter = Filter.GRAY
            R.id.action_cooltone -> filter = Filter.COOL
            R.id.action_dim -> filter = Filter.BLUR
            R.id.action_warmtone -> filter = Filter.WARM
        }
        eMediaPlayer.setFilter(filter.mType, filter.mData)
        return true
    }

    //滤镜类型
    enum class Filter(val mType: Int, val mData: FloatArray) {
        NONE(0, floatArrayOf(0.0f, 0.0f, 0.0f)),
        GRAY(1, floatArrayOf(0.299f, 0.587f, 0.114f)),
        COOL(2, floatArrayOf(0.0f, 0.0f, 0.1f)),
        WARM(2, floatArrayOf(0.1f, 0.1f, 0.0f)),
        BLUR(3, floatArrayOf(0.006f, 0.004f, 0.002f));
    }

}
