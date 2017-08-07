package projectm.dragneelfps.mymangareader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.jakewharton.disklrucache.DiskLruCache
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
 * Source.url is the prefix for downloading the images of the series
 * Source.series is to get the series
 */
enum class Source(val url: String,val series: String){
    Mangajuki("https://mangazuki.co","https://mangazuki.co/series")
}

class GetSeriesList(var source: Source, var context: Activity) {

    var seriesList : ArrayList<Series> = ArrayList()
    var downloadedFlag : Boolean = false
    lateinit var mDiskLruCache : DiskLruCache
    var mDiskCacheLock = Object()
    var mDiskCacheStarting = true
    var DISK_CACHE_SIZE = 1024*1024*10L
    var IO_BUFFER_SIZE = 8*1024
    var DISK_CACHE_SUBDIR = "series_list_cache_dir"

    fun populateList() {
        var cacheDir = getDiskCacheDir(context,DISK_CACHE_SUBDIR)
        InitDiskCache().execute(cacheDir)

    }


    inner class InitDiskCache : AsyncTask<File,Unit,Unit>(){
        override fun doInBackground(vararg params: File?) {
            synchronized(mDiskCacheLock){
                if(BuildConfig.DEBUG)
                    Log.d("debug","initializing cache ...")
                var cacheDir = params[0]
                mDiskLruCache = DiskLruCache.open(cacheDir,1,1,DISK_CACHE_SIZE)
                mDiskCacheStarting = false
                mDiskCacheLock.notifyAll()
            }
            Log.d("debug","starting SeriesAsyncTask")
            SeriesAsyncTask().execute(source.series)
        }
    }


    fun getDiskCacheDir(context:Context,uniqueName:String): File{
        var cachePath = context.cacheDir.path
        return File(cachePath + File.separator + uniqueName)
    }


    fun getSeriesListFromCache(key: String): SeriesListCache?{
        synchronized(mDiskCacheLock){
            while (mDiskCacheStarting){
                mDiskCacheLock.wait()
            }
            var result : SeriesListCache
            var snapshot: DiskLruCache.Snapshot? = null
            try{
                snapshot = mDiskLruCache.get(key)
                if(snapshot==null) return null
                val input : InputStream? = snapshot.getInputStream(0)
                if(input!=null){
                    val objectStream = ObjectInputStream(input)
                    return objectStream.readObject() as SeriesListCache
                }
            }catch(e: Exception){
                e.printStackTrace()
            } finally {
                snapshot?.close()
                return null
            }
        }
        return null
    }

    fun writetoFile(seriesListCache: SeriesListCache, diskLruCacheEditor: DiskLruCache.Editor) : Boolean{
        Log.d("debug","here")
        var out : ObjectOutputStream? = null
        try{
            out = ObjectOutputStream(diskLruCacheEditor.newOutputStream(0))
            out.writeObject(seriesListCache)
            out.flush()
            out.close()

            Log.d("debug","written to file")
            return true
        }catch (e: Exception){
            Log.d("debug",e.toString())
            out?.close()
            return false
        }
    }

    fun putSeriesListToDiskCache(key:String,seriesListCache: SeriesListCache){
        var editor : DiskLruCache.Editor? = null
        try{
            editor = mDiskLruCache.edit(key)
            if(editor == null) {
                Log.d("debug","Could not find in cache")
                return
            }
            if(writetoFile(seriesListCache,editor)){
                mDiskLruCache.flush()
                editor.commit()
                Log.d("debug","put on disk cache")
            }

        }catch (e: Exception){
            Log.d( "cache_test_DISK_", "ERROR on: image put on disk cache " + key )
            editor?.abort()
        }
    }

    inner class SeriesAsyncTask : AsyncTask<String,Unit,ArrayList<Series>>(){
        override fun doInBackground(vararg p0: String?): ArrayList<Series> {
            Log.d("debug","started SeriesAsyncTask:doInBackground")
            var list: ArrayList<Series> = ArrayList()
            var cacheKey = "cache_key_for_series_list"
            var cacheStored = getSeriesListFromCache(cacheKey)
            Log.d("debug",if(cacheStored == null) "its null" else "no")

            if(cacheStored == null || cacheStored.cacheDate.before(Date()) ){

                var url =  p0[0]
                var connection: Connection = Jsoup.connect(url)
                var doc: Document = connection.get()
                var series_list: Elements = doc.getElementsByClass("thumbnail")

                for (series in series_list) {
                    var image: Element = series.getElementsByTag("img").get(0)
                    var anchor: Element = series.getElementsByTag("a").get(0)
                    var image_url = image.attr("src")
                    var link: String = anchor.attr("href")
                    var title: String = (link.substring(link.lastIndexOf("/")+1)).replace("-"," ")
                    var bitmap: Bitmap = downloadBitmap(image_url)
                    list.add(Series(bitmap, title, link))
                }
                var date  = Date()
                var seriesListCache = SeriesListCache(date,list)
                putSeriesListToDiskCache(cacheKey,seriesListCache)
                return list
            }else{
                Log.d("debug","Found in cache")
                return cacheStored.seriesList
            }
        }

        override fun onPostExecute(result: ArrayList<Series>) {
            downloadedFlag = true
            seriesList = result
            context.progressbar.visibility = ProgressBar.GONE
            context.findViewById<GridView>(R.id.series).adapter =  SeriesListAdapter(context)
        }

        fun downloadBitmap(url: String): Bitmap {
            var UL: URL = URL(Source.Mangajuki.url + url)
            var connection: HttpURLConnection = UL.openConnection() as HttpURLConnection
            var currentTime  = System.currentTimeMillis()
            var expires = connection.getHeaderFieldDate("Expires",currentTime)
            var lastModified = connection.getHeaderFieldDate("Last-Modified",currentTime)
            if(lastModified < currentTime)
            {}
            else{

            }
            connection.connect()
            if (connection.responseCode == 404) {
                return BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_background)
            }
            var stream: InputStream = connection.inputStream
            var bitmap: Bitmap = BitmapFactory.decodeStream(stream)
            return bitmap
        }
    }
    inner class SeriesListAdapter(context: Context) : BaseAdapter(){
        var inflater : LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
            var view : View = inflater.inflate(R.layout.series_list_item,null)
            view.image.setImageBitmap(seriesList[p0].image)
            view.title.text = seriesList[p0].title
            view.setOnClickListener { v ->
                Log.d("debug",seriesList[p0].title)
                var intent: Intent = Intent(context,SeriesDetails::class.java)
                intent.putExtra("link",seriesList[p0].link)
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