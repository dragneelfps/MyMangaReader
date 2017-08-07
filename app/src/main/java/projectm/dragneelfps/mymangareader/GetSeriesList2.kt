package projectm.dragneelfps.mymangareader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.android.synthetic.main.series_list.*
import kotlinx.android.synthetic.main.series_list_item.view.*
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Created by srawa on 8/5/2017.
 */
/*
 * Source2.url is the prefix for downloading the images of the series
 * Source2.series is to get the series
 */
enum class Source2(val url: String, val series: String) {
    Mangajuki("https://mangazuki.co", "https://mangazuki.co/series")
}

class GetSeriesList2 {
    lateinit var source: Source2
    lateinit var context: Activity
    lateinit var cacheDir: File
    lateinit var seriesList: ArrayList<Series>

    constructor(source: Source2, context: Activity) {
        seriesList = ArrayList()
        this.source = source
        this.context = context
        var sdState = Environment.getExternalStorageState()
//        if (!sdState.equals(Environment.MEDIA_MOUNTED)) {                        //If portion Doesnt work .
//            var sdDir = Environment.getExternalStorageDirectory()
//            Log.d("debug", "sdDir: $sdDir")
//            cacheDir = File(sdDir, "data/cache")
//        }

        cacheDir = context.cacheDir
        Log.d("debug", "cacheDir: ${cacheDir.path}")

        if (!cacheDir.exists())
            cacheDir.mkdirs()
    }


    fun populateList() {
        SeriesAsyncTask().execute(source.series)

    }


    fun getBitmap(url: String): Bitmap? {
        val fileName = url.hashCode().toString()
        var f = File(cacheDir, fileName)
        try {
            var bitmap: Bitmap? = BitmapFactory.decodeFile(f.path)
//            Log.d("debug",f.path + " : " + f.absolutePath)
//            Log.d("debug",(bitmap == null).toString())
            if (bitmap != null) {
                Log.d("debug", "found in cache")
                return bitmap
            }
            Log.d("debug", "not found in cache")
            bitmap = pitctureUrlToBitmap(url)
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_btn_speak_now)
                return bitmap
            }
            writeBitmapToFile(f, bitmap)
            return bitmap
        } catch (ex: Exception) {
            ex.printStackTrace()
            Log.d("debug", ex.localizedMessage)
            return null
        } catch (e: OutOfMemoryError) {
            Log.d("debug", "OutOfMemoryError: ${e.localizedMessage}")
            e.printStackTrace()
            return null
        }
    }


    fun writeBitmapToFile(f: File, bitmap: Bitmap) {
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(f)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            out?.close()
        }
    }

    fun pitctureUrlToBitmap(url: String): Bitmap? {
        if (isInternetAvailable()) {
            var UL: URL = URL(Source2.Mangajuki.url + url)
            var connection: HttpURLConnection = UL.openConnection() as HttpURLConnection

            connection.connect()


            if (connection.responseCode == 404) {
                return null
            }
            var stream: InputStream = connection.inputStream
            var bitmap: Bitmap = BitmapFactory.decodeStream(stream)
            return bitmap
        } else {
            return null
        }
    }

    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
        return isConnected
    }


    inner class SeriesAsyncTask : AsyncTask<String, Unit, ArrayList<Series>>() {
        override fun doInBackground(vararg p0: String?): ArrayList<Series> {
            Log.d("debug", "started SeriesAsyncTask:doInBackground")
            var list: ArrayList<Series> = ArrayList()


            var url = p0[0]
            if (isInternetAvailable()) {
                var connection: Connection = Jsoup.connect(url)
                var doc: Document = connection.get()
                var series_list: Elements = doc.getElementsByClass("thumbnail")

                for (series in series_list) {
                    var image: Element = series.getElementsByTag("img").get(0)
                    var anchor: Element = series.getElementsByTag("a").get(0)
                    var image_url = image.attr("src")
                    var link: String = anchor.attr("href")
                    var title: String = (link.substring(link.lastIndexOf("/") + 1)).replace("-", " ")
//                    Log.d("debug","link: $link")
                    var bitmap: Bitmap? = getBitmap(image_url)
                    list.add(Series(bitmap!!, title, link))
                }
                var Url = URL(url)
                var con = Url.openConnection() as HttpURLConnection
                con.connect()
                if (con.responseCode != 404) {
                    var inputStream = con.inputStream
                    var builder = StringBuilder()
                    var br = BufferedReader(InputStreamReader(inputStream))
                    var line: String? = br.readLine()
                    while (line != null) {
                        builder.append(line)
                        line = br.readLine()
                    }
                    var storedHtmlResponse = builder.toString()
                    var prefs = context.getSharedPreferences("PREFS", Context.MODE_PRIVATE)
                    var editor = prefs.edit()
                    editor.putString("storedHtmlResponse", storedHtmlResponse)
                    editor.commit()
                }
            } else {
                var prefs = context.getSharedPreferences("PREFS", Context.MODE_PRIVATE)
                var storedHtmlResponse = prefs.getString("storedHtmlResponse", "none")
                if (storedHtmlResponse.equals("none")) {
                    Log.d("debug", "No Internet Connection")
                    context.no_internet_text_view.visibility = TextView.VISIBLE
                } else {
                    Log.d("debug", storedHtmlResponse.length.toString())
                    var doc = Jsoup.parse(storedHtmlResponse)
                    var series_list: Elements = doc.getElementsByClass("thumbnail")

                    for (series in series_list) {
                        var image: Element = series.getElementsByTag("img").get(0)
                        var anchor: Element = series.getElementsByTag("a").get(0)
                        Log.d("debug", "image: ${image.toString()}")
                        var image_url = image.attr("data-cfsrc")
                        var link: String = anchor.attr("href")
                        var title: String = (link.substring(link.lastIndexOf("/") + 1)).replace("-", " ")
//                    Log.d("debug","link: $link")
                        Log.d("debug", "image_url: $link")
                        var bitmap: Bitmap? = getBitmap(image_url)
                        if (bitmap == null) {
                            bitmap = BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_btn_speak_now)
                        }
                        list.add(Series(bitmap!!, title, link))
                    }
                }
            }

            return list
        }


        override fun onPostExecute(result: ArrayList<Series>) {
            seriesList = result
            context.progressbar.visibility = ProgressBar.GONE
            context.findViewById<GridView>(R.id.series).adapter = SeriesListAdapter(context)
        }
    }

    inner class SeriesListAdapter(context: Context) : BaseAdapter() {
        var inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
            var view: View = inflater.inflate(R.layout.series_list_item, null)
            view.image.setImageBitmap(seriesList[p0].image)
            view.title.text = seriesList[p0].title
            view.setOnClickListener { v ->
                Log.d("debug", seriesList[p0].title)
                var intent: Intent = Intent(context, SeriesDetails::class.java)
                intent.putExtra("link", seriesList[p0].link)
                context.startActivity(intent)
            }
            return view
        }

        override fun getItem(p0: Int): Any {
            return p0
        }

        override fun getItemId(p0: Int): Long {
            return p0.toLong()
        }

        override fun getCount(): Int {
            return seriesList.size
        }
    }
}