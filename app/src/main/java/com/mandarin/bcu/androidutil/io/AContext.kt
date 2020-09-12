package com.mandarin.bcu.androidutil.io

import android.app.Activity
import android.util.Log
import com.mandarin.bcu.R
import com.mandarin.bcu.androidutil.StaticStore
import com.mandarin.bcu.androidutil.pack.PackConflict
import common.CommonStatic
import common.io.PackLoader
import common.io.assets.Admin
import common.io.assets.UpdateCheck
import common.pack.Context
import common.pack.Identifier
import common.util.Data
import common.util.stage.Music
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference

class AContext : Context {
    companion object {
        fun check() {
            if(CommonStatic.ctx == null) {
                CommonStatic.ctx = AContext()
            }
        }
    }

    private val stopper = Object()

    var c: WeakReference<Activity?>? = null

    fun updateActivity(a: Activity) {
        c = WeakReference(a)

        synchronized(stopper) {
            stopper.notifyAll()
        }
    }

    override fun noticeErr(e: Exception, t: Context.ErrType, str: String) {
        Log.e("AContext", str)
        e.printStackTrace()

        if(str.contains("failed to load external pack")) {
            val path = str.split("/")

            val list = ArrayList<String>()

            list.add(path[path.size-1])

            PackConflict(PackConflict.ID_CORRUPTED, list, true)
        }

        val wac = c

        if(wac == null) {
            ErrorLogWriter.writeDriveLog(e)
            return
        }

        val a = wac.get()

        if(a == null) {
            if(t == Context.ErrType.FATAL || t == Context.ErrType.ERROR)
                ErrorLogWriter.writeDriveLog(e)

            return
        }

        if(t == Context.ErrType.FATAL || t == Context.ErrType.ERROR)
            ErrorLogWriter.writeLog(e, StaticStore.upload, a)


    }

    override fun getWorkspaceFile(relativePath: String): File {
        val wac = c ?: return File("")

        val a = wac.get() ?: return File("")

        return File(StaticStore.getExternalWorkspace(a)+relativePath)
    }

    override fun getAssetFile(string: String): File {
        val wac = c ?: return File("")

        val a = wac.get() ?: return File("")

        return File(StaticStore.getExternalAsset(a)+string)
    }

    override fun getAuxFile(string: String): File {
        val wac = c ?: return File("")

        val a = wac.get() ?: return File("")

        return File(StaticStore.getExternalPath(a)+string)
    }

    override fun initProfile() {
        UpdateCheck.addRequiredAssets("090901")
    }

    override fun getUserFile(string: String): File {
        val wac = c ?: return File("")

        val a = wac.get() ?: return File("")

        return File(StaticStore.getExternalUser(a)+string)
    }

    override fun confirmDelete(): Boolean {
        return true
    }

    override fun printErr(t: Context.ErrType?, str: String?) {
        if(t != null) {
            if(t == Context.ErrType.DEBUG || t == Context.ErrType.INFO || t == Context.ErrType.NEW) {
                Log.i("AContext", str ?: "")
            } else if(t == Context.ErrType.CORRUPT || t == Context.ErrType.WARN) {
                Log.w("AContext", str ?: "")

                val msg = str ?: return

                if(msg.contains(" has same ID with ")) {
                    val info = msg.split(" has same ID with")

                    if(info.size == 2) {
                        val list = ArrayList<String>()

                        list.add(info[0])
                        list.add(info[1])

                        PackConflict(PackConflict.ID_SAME_ID, list, true)
                    }
                } else if(msg.contains(" core version ")) {
                    val info = msg.replace("Pack ","").replace(" core version (", "\\").replace(") is higher than BCU", "").replace(")", "").split("\\")

                    val list = ArrayList<String>()

                    list.add(info[0])
                    list.add(info[1])

                    PackConflict(PackConflict.ID_UNSUPPORTED_CORE_VERSION, list, true)
                }



            } else if(t == Context.ErrType.ERROR || t == Context.ErrType.FATAL) {
                Log.e("AContext", str ?: "")
            }
        }
    }

    override fun getLangFile(file: String): InputStream? {
        val wac = c ?: return null

        val a = wac.get() ?: return null

        return when(CommonStatic.getConfig().lang) {
            0 -> {
                a.resources.openRawResource(R.raw.proc)
            }
            1 -> {
                a.resources.openRawResource(R.raw.proc_zh)
            }
            2 -> {
                a.resources.openRawResource(R.raw.proc_kr)
            }
            else -> {
                a.resources.openRawResource(R.raw.proc)
            }
        }
    }

    override fun preload(desc: PackLoader.ZipDesc.FileDesc): Boolean {
        if(desc.path.contains(".ogg"))
            return false

        return Admin.preload(desc)
    }

    fun getMusicFile(m: Music) : File {
        synchronized(stopper) {
            while(c == null) {
                stopper.wait()
            }
        }
        val wac = c ?: return File("")
        val a = wac.get() ?: return File("")

        return if(m.id.pack == Identifier.DEF) {
            File(StaticStore.getExternalAsset(a)+"music/"+ Data.trio(m.id.id)+".ogg")
        } else {
            File(StaticStore.dataPath+"music/"+m.id.pack+"-"+Data.trio(m.id.id)+".ogg")
        }
    }

    fun extractImage(path: String) : File? {
        synchronized(stopper) {
            while(c == null) {
                stopper.wait()
            }
        }

        val wac = c ?: return null

        val a = wac.get() ?: return null

        val target = File(path)

        if(!target.exists()) {
            Log.e("AContext::extractImage", "File not existing : ${target.absolutePath}")

            return null
        }

        val parent = target.parentFile?.name ?: return null

        val shared = a.getSharedPreferences(parent, android.content.Context.MODE_PRIVATE)

        if(!shared.contains(path)) {
            Log.e("AContext::extractImage", "Key not existing : $path")

            return null
        }

        val password = shared.getString(path, "") ?: ""

        if(password.isEmpty())
            return null

        return File(StaticStore.decryptPNG(path, password, StaticStore.IV))
    }
}