package jp.co.abs.filedownloaderkamada2

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import jp.co.abs.filedownloaderkamada2.databinding.ActivityFileDownloaderBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date


class FileDownloaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileDownloaderBinding

    //パス文字列
    private val downloadPath = Environment.getExternalStorageDirectory().path + "/Kamada_Picture"
    private var fileName = ""
    private var notifySuccess = "ダウンロードが完了しました"
    private var notifyFailure = "画像取得に失敗しました"

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileDownloaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val downloadPathFile = File(downloadPath)
        if (!downloadPathFile.exists()) {
            AlertDialog.Builder(this) // FragmentではActivityを取得して生成
                .setTitle("ストレージへの\nアクセス許可")
                .setMessage("")
                .setPositiveButton("許可する") { _, _ ->
                    try {
                        // 保存先のディレクトリが無ければ作成する
                        downloadPathFile.mkdir()
                        Log.d("Log", "new_File:$downloadPathFile")
                    } catch (error: SecurityException) {
                        // ファイルに書き込み用のパーミッションが無い場合など
                        error.printStackTrace()
                    } catch (error: IOException) {
                        // 何らかの原因で誤ってディレクトリを2回作成してしまった場合など
                        error.printStackTrace()
                    } catch (error: Exception) {
                        error.printStackTrace()
                    }
                }
                .setNegativeButton("しない") { _, _ -> finish() }
                .show()
        }
        binding.progressbar.visibility = android.widget.ProgressBar.INVISIBLE

        //ダウンロード開始ボタン
        binding.downloadStart.setOnClickListener {
            hideKeyboard()
            downloadImage()
        }
        //GALLERYボタン
        binding.gallery.setOnClickListener {
            hideKeyboard()
            //ギャラリーに遷移するIntentの作成
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            //ギャラリーへ遷移
            receivePicture.launch(intent)
        }
        //clearボタン
        binding.clear.setOnClickListener {
            binding.imageView.setImageDrawable(null)
            binding.urlEditText.editableText.clear()
        }
        //ダウンロードした画像ボタン
        binding.downloadStill.setOnClickListener {
            // 背景にフォーカスを移す
            if (currentFocus != null) binding.focusParent.requestFocus()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "image/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            receivePicture.launch(intent)
        }
    }

    private fun hideKeyboard() {
        //キーボード非表示
        if (currentFocus != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(
                currentFocus!!.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            // 背景にフォーカスを移す
            binding.focusParent.requestFocus()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentFocus != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            // キーボードを隠す
            imm.hideSoftInputFromWindow(
                currentFocus!!.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            // 背景にフォーカスを移す
            binding.focusParent.requestFocus()
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("SimpleDateFormat")
    private fun downloadImage() {
        val stringUrl: String = binding.urlEditText.text.toString()
        if (stringUrl.isEmpty()) {
            Toast.makeText(applicationContext, notifyFailure, Toast.LENGTH_SHORT).show()
            return
        }
        //launchを呼び出す前にプログレスバーを表示
        binding.progressbar.visibility = android.widget.ProgressBar.VISIBLE
        CoroutineScope(Dispatchers.Default).launch(Dispatchers.IO) {
            try {
                val url = URL(stringUrl)
                val urlCon = url.openConnection() as HttpURLConnection
                // タイムアウト設定
                urlCon.readTimeout = 10000
                urlCon.connectTimeout = 20000
                // リクエストメソッド
                urlCon.requestMethod = "GET"
                // リダイレクトを自動で許可しない設定
                urlCon.instanceFollowRedirects = false
                //画像をダウンロード
                val ism = urlCon.inputStream
                val bmp = BitmapFactory.decodeStream(ism)

                val sdf = SimpleDateFormat("yyyyMMdd_HH:mm:ss")
                val current = sdf.format(Date())
                // 保存先のファイル作成
                fileName = "$current.png"
                val file = File(downloadPath, fileName)
                // ファイルに書き込み
                FileOutputStream(file).use { stream ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }

                // 処理が終わったら、メインスレッドに切り替える。
                withContext(Dispatchers.Main) {
                    // プログレスバーを非表示
                    binding.progressbar.visibility = android.widget.ProgressBar.INVISIBLE
                    binding.imageView.setImageBitmap(bmp)
                    Toast.makeText(applicationContext, notifySuccess, Toast.LENGTH_SHORT).show()
                }

            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
        }
    }

    //ギャラリーから画像受け取り
    private val receivePicture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                // 画像を表示（ギャラリーintentから取得）
                binding.imageView.setImageURI(it.data?.data)
                Toast.makeText(applicationContext, "画像を取得しました", Toast.LENGTH_SHORT).show()
            }
        }
}