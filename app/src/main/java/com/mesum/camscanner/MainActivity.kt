package com.mesum.camscanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException


class MainActivity : AppCompatActivity() {

    var photoFile: File? = null
    var mCurrentPhotoPath: String? = null
    private val CAMREA_REQUEST_CODE = 1111


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val btn = findViewById<Button>(R.id.button_takepicture)
        btn.setOnClickListener {
            cameraPicker()
        }




    }

    private fun cameraPicker() {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext!!,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                0
            )
        } else {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            // Create the File where the photo should go
            try {
                photoFile = createImageFile()
                Log.i("Mayank", photoFile!!.getAbsolutePath())

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "com.mesum.camscanner.provider",
                        photoFile!!
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(
                        takePictureIntent,
                        CAMREA_REQUEST_CODE
                    )
                    createPdfFromImage(photoFile!!)

                    Log.d("PdfFile", photoFile.toString())


                }
            } catch (ex: java.lang.Exception) {
                // Error occurred while creating the File
                ex.printStackTrace()
            }

        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.absolutePath
        return image
    }


    private fun createPdfFromImage(imageFile: File): File {
        val document = Document()
        val pdfFile = File(getExternalFilesDir(null), "pdfFileName.pdf")
        val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
        writer.open()
        document.open()

        // Create iText Image object from the JPEG image file
        val image = Image.getInstance(imageFile.absolutePath)

        // Scale the image to fit the page
        image.scaleToFit(document.pageSize.width, document.pageSize.height)

        // Add the image to the PDF document
        document.add(image)

        document.close()
        writer.close()
        return pdfFile
    }

    private fun openPdfViewer(pdfFile: File) {
        val pdfUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".provider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(pdfUri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Open PDF"))
    }

    private fun savePdf(pdfFile: File) {
        val destFile = File(getExternalFilesDir(null), "pdfFileName.pdf")
        pdfFile.copyTo(destFile, true)
        Toast.makeText(this, "PDF saved to ${destFile.absolutePath}", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {

            CAMREA_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val bundle = data?.getStringExtra("image_path")
                    val bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath)
                    findViewById<ImageView>(R.id.imageView).setImageBitmap(bitmap)
                    recognizeText(bitmap)
                    Log.d("ImageData","bitmap : ${bitmap.toString()} bundle : ${bundle.toString()}"  )
                }

            }


        }
    }


    private fun recognizeText(bitmap: Bitmap) {
        val textRecognizer = TextRecognizer.Builder(this).build()

        if (!textRecognizer.isOperational) {
            // Check if the text recognition API is available
            Toast.makeText(this, "Text recognition is not available.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a Frame object from the bitmap
        val frame = Frame.Builder().setBitmap(bitmap).build()

        // Use the text recognition API to recognize text from the image
        val textBlocks = textRecognizer.detect(frame)

        // Extract the text from each TextBlock and concatenate them into a single string
        val text = StringBuilder()
        for (i in 0 until textBlocks.size()) {
            val textBlock = textBlocks.valueAt(i)
            text.append(textBlock.getValue())
        }

        // Create a PDF file from the recognized text
        Log.d("TextTest", text.toString())
        createPdfFromText(text.toString())
    }



    private fun createPdfFromText(text: String) {
        // Create a new file for the PDF
        val pdfFile = File(this.filesDir, "text.pdf")

        // Create a PDF document
        val document = PdfDocument()

        // Create a page for the document
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)

        // Create a canvas to draw the text on the page
        val canvas = page.canvas
        val paint = Paint()
        paint.textSize = 12f
        paint.color = Color.BLACK

        // Split the text into lines
        val lines = text.split("\n")

        // Draw each line of text on the canvas
        var y = 0f
        for (line in lines) {
            canvas.drawText(line, 0f, y, paint)
            y += paint.descent() - paint.ascent()
        }

        // Finish the page and add it to the document
        document.finishPage(page)

        // Write the document to the PDF file
        val outputStream = FileOutputStream(pdfFile)
        document.writeTo(outputStream)

        // Close the document and the output stream
        document.close()
        outputStream.close()

        // Share the PDF file with other apps using a FileProvider
        val uri = FileProvider.getUriForFile(this, "com.mesum.camscanner.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooserIntent = Intent.createChooser(intent, "Open PDF")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooserIntent)
    }


}