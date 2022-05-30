package com.burakdemir.artgallery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.burakdemir.artgallery.databinding.ActivityGalleryBinding
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayOutputStream

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var activityResultLauncher:ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher:ActivityResultLauncher<String>
    var selectedBitmap: Bitmap?=null
    private lateinit var database:SQLiteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityGalleryBinding.inflate(layoutInflater)
        val view=binding.root
        setContentView(view)

        database = this.openOrCreateDatabase("Arts", Context.MODE_PRIVATE,null)


        registerLauncher()
        val intent =intent

        val info=intent.getStringExtra("info")
        if (info.equals("new")){
            binding.artNameText.setText("")
            binding.artistNameText.setText("")
            binding.yearText.setText("")
            binding.selectImageView.setImageResource(R.drawable.ic_launcher_background)
            binding.saveButton.visibility=View.VISIBLE
        }
        else{
            binding.saveButton.visibility=View.INVISIBLE
            val selectedId=intent.getIntExtra("id",1)
            val cursor=database.rawQuery("SELECT * FROM arts WHERE id =? ", arrayOf(selectedId.toString()))
            val artNameIx=cursor.getColumnIndex("artname")
            val artistNameIx=cursor.getColumnIndex("artistname")
            val year=cursor.getColumnIndex("year")
            val imageIx=cursor.getColumnIndex("image")

            while(cursor.moveToNext()){
                binding.artNameText.setText(cursor.getString(artNameIx))
                binding.artistNameText.setText(cursor.getString(artistNameIx))
                binding.yearText.setText(cursor.getString(year))
                val byteArray=cursor.getBlob(imageIx)
                val bitmap=BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
                binding.selectImageView.setImageBitmap(bitmap)
            }
            cursor.close()

        }
    }


    fun saveButtonClicked(view: View) {
        val artName=binding.artNameText.text.toString()
        val artistName=binding.artistNameText.text.toString()
        val year= binding.yearText.text.toString()

        if(selectedBitmap!=null){
            val smallBitmap=createSmallBitmap(selectedBitmap!!,300)

            val outputStream=ByteArrayOutputStream()
            smallBitmap.compress(Bitmap.CompressFormat.PNG,50,outputStream)
            val byteArray=outputStream.toByteArray()

            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS  arts(id INTEGER PRIMARY KEY,artName VARCHAR,year VARCHAR,image BLOB)")
                val sqlString="INSERT INTO arts (artname,artistname,year,image) VALUES(?,?,?,?)"
                val statement=database.compileStatement(sqlString)
                statement.bindString(1,artName)
                statement.bindString(2,artistName)
                statement.bindString(3,year)
                statement.bindBlob(4,byteArray)
                statement.execute()
            }
            catch (e:Exception){
                e.printStackTrace()
            }

            val intent=Intent(GalleryActivity@this,MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }
    private fun createSmallBitmap(image:Bitmap,maxSize:Int):Bitmap{
        var height=image.height
        var width=image.width
        val ratio=height.toDouble()/width.toDouble()
        if(ratio>=1){
            //portrait
            height=maxSize
            val scaledWidth=height/ratio
            width=scaledWidth.toInt()
        }
        else{
            //landscape
            width=maxSize
            val scaledHeight=width*ratio
            height=scaledHeight.toInt()
        }
        return Bitmap.createScaledBitmap(image,width,height,true)
    }
    fun selectImage(view: View) {

        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)){
                Snackbar.make(view,"Permission Required For Gallery",Snackbar.LENGTH_INDEFINITE).setAction("Give Permission"){
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }.show()
            }
            else{
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }else{
            val intentToGallery= Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intentToGallery)
        }

    }
    private fun registerLauncher(){
        activityResultLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result->

            if(result.resultCode== RESULT_OK){
                val intentFromResult=result.data
                if (intentFromResult!=null){
                    val imageData=intentFromResult.data
                    if(imageData!=null){
                        try {
                            if(Build.VERSION.SDK_INT>=28) {
                                val source = ImageDecoder.createSource(
                                    this@GalleryActivity.contentResolver,
                                    imageData
                                )
                                selectedBitmap=ImageDecoder.decodeBitmap(source)
                                binding.selectImageView.setImageBitmap(selectedBitmap)
                            }
                            else{
                                selectedBitmap=MediaStore.Images.Media.getBitmap(contentResolver,imageData)
                                binding.selectImageView.setImageBitmap(selectedBitmap)
                            }
                        }
                        catch (e:Exception){
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){ result->
            if(result){
                val intentToGallery= Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }
            else{
                Toast.makeText(this@GalleryActivity,"Permission Denied",Toast.LENGTH_LONG).show()
            }

        }
    }
}