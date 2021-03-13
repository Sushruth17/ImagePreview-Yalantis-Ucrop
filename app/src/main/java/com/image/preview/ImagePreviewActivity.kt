package com.image.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider.getUriForFile
import com.yalantis.ucrop.UCrop
import kotlinx.android.synthetic.main.activity_image_preview.*
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.OutputStream

class ImagePreviewActivity : AppCompatActivity() {

    companion object{
        private const val IMAGE_CAPTURE_CODE = 1001
        private const val IMAGE_PICK_CODE = 1002
        private const val CAMERA_PERMISSION_CODE = 1003
        private const val GALLERY_PERMISSION_CODE = 1004
    }

    var fileName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        camera_btn.setOnClickListener {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_DENIED ||
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_DENIED){
                    val permission = arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ActivityCompat.requestPermissions(this, permission, CAMERA_PERMISSION_CODE)
                }
                else{
                    openCamera()
                }
        }

        upload_img.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
                val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(this, permission, GALLERY_PERMISSION_CODE)
            }
            else{
                openGallery()
            }
        }
    }

    private fun openGallery(){
        val intent = Intent("android.intent.action.GET_CONTENT")
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        fileName = System.currentTimeMillis().toString() + ".jpg"
        intent.putExtra(MediaStore.EXTRA_OUTPUT, getCacheImagePath(fileName))
        intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
        startActivityForResult(intent, IMAGE_CAPTURE_CODE)
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        //called when user presses ALLOW or DENY from Permission Request Popup
        when(requestCode){
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    openCamera()
                } else {
                    //permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            GALLERY_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    openGallery()
                } else {
                    //permission from popup was denied
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            IMAGE_CAPTURE_CODE -> {
                if (resultCode == RESULT_OK) {
                    getCacheImagePath(fileName)?.let { openCropActivity(it) }
                } else {
                    setResultCancelled()
                }
            }

            IMAGE_PICK_CODE -> {
                if (resultCode == RESULT_OK) {
                    val imageUri = data?.data
                    imageUri?.let { openCropActivity(it) }
                } else {
                    setResultCancelled()
                }
            }

            UCrop.REQUEST_CROP -> {
                if (resultCode == RESULT_OK) {
                    handleUCropResult(data)
                } else {
                    setResultCancelled()
                }
            }

            UCrop.RESULT_ERROR -> {
                val cropError = data?.let { UCrop.getError(it) }
                Log.e("Error", "Crop error: $cropError")
                setResultCancelled()
            }

            else -> setResultCancelled()
        }
    }

    private fun getCacheImagePath(fileName: String): Uri? {
        val path = File(externalCacheDir, "camera")
        if (!path.exists()) path.mkdirs()
        val image = File(path, fileName)
        return getUriForFile(
            this,
            "$packageName.provider",
            image
        )
    }

    private fun setResultCancelled() {
        val intent = Intent()
        setResult(Activity.RESULT_CANCELED, intent)
    }


    private fun openCropActivity(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, queryName(contentResolver, sourceUri)))
        val options = UCrop.Options()
        UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .start(this)
    }

    private fun handleUCropResult(data: Intent?) {
        if (data == null) {
            setResultCancelled()
            return
        }
        tv_image_preview.visibility = View.GONE
        val resultUri = UCrop.getOutput(data)
        resultUri?.let { imagePath ->
            image_view.setImageURI(imagePath)
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imagePath)
            saveToGallery(this, bitmap)
        }
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap) {
        val folderName = "Image Preview App"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName")
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val directory = File(Environment.getExternalStorageDirectory().toString() + separator + folderName)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = System.currentTimeMillis().toString() + ".png"
            val file = File(directory, fileName)
            saveImageToStream(bitmap, FileOutputStream(file))
            val values = contentValues()
            values.put(MediaStore.Images.Media.DATA, file.absolutePath)
            // .DATA is deprecated in API 29
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    private fun contentValues() : ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("Recycle")
    private fun queryName(resolver: ContentResolver, uri: Uri): String {
        val returnCursor: Cursor = resolver.query(uri, null, null, null, null)!!
        val nameIndex: Int = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name: String = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }

}