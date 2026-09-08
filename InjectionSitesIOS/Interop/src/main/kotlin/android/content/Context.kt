package android.content

import java.io.File

// Only platform plumbing is stubbed. Crypto, JSON, schema and Android codec are real.
class Context {
    val packageName = "com.example.injectionsites"
    val packageManager = PackageManager()
    val filesDir = File(System.getProperty("java.io.tmpdir"), "insofina-interop")
    fun getSharedPreferences(name: String, mode: Int) = Preferences()
    companion object { const val MODE_PRIVATE = 0 }
}
class PackageManager { fun getPackageInfo(name: String, flags: Int) = PackageInfo() }
class PackageInfo { val versionName: String? = "2.0" }
class Preferences { fun edit() = Editor() }
class Editor {
    fun putString(key: String, value: String) = this
    fun putFloat(key: String, value: Float) = this
    fun putLong(key: String, value: Long) = this
    fun remove(key: String) = this
    fun commit(): Boolean = error("Persistence is not emulated by the codec interoperability harness")
}
