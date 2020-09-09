package com.gappein.sdk.ui.view.chatView

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.gappein.sdk.client.ChatClient
import com.gappein.sdk.model.Message
import com.gappein.sdk.model.User
import com.gappein.sdk.ui.R
import com.gappein.sdk.ui.base.ChatBaseView
import com.gappein.sdk.ui.view.chatView.adapter.MessageListAdapter
import com.gappein.sdk.ui.view.chatView.imageviewer.openImage
import com.gappein.sdk.ui.view.util.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_message.*
import java.io.File
import java.io.IOException


class MessageListActivity : AppCompatActivity(), ChatBaseView {

    private var photoFile: File? = null
    private lateinit var adapter: MessageListAdapter
    private val chats = mutableListOf<Message>()

    companion object {
        private const val REQUEST_TAKE_PHOTO = 1
        private const val REQUEST_GALLERY_PHOTO = 2
        private const val CHANNEL_ID = "channelId"
        private const val RECEIVER = "receiver"
        private const val DEFAULT_STRING = ""
        private val EMPTY_USER = User()

        @JvmStatic
        fun buildIntent(context: Context, channelId: String, receiver: User) =
            Intent(context, MessageListActivity::class.java).apply {
                putExtra(CHANNEL_ID, channelId)
                putExtra(RECEIVER, receiver)
            }
    }

    private val channelId by lazy { intent.getStringExtra(CHANNEL_ID) ?: DEFAULT_STRING }
    private val receiver by lazy { intent.getParcelableExtra(RECEIVER) ?: EMPTY_USER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)
        setupUI()
        setupRecyclerView()
        fetchMessage()
        setupSendMessageListener()
    }

    private fun setupUI() {
        toolbar.init {
            channelId
        }
    }

    private fun setupSendMessageListener() {
        buttonSend.setOnClickListener {
            val message = editTextChatBox.text.toString()
            if (message.isNotEmpty()) {
                ChatClient.getInstance().sendMessage(message, receiver.token, {
                    editTextChatBox.text.clear()
                }, {

                })
            }
        }
        toolbar.setOnBackPressed {
            onBackPressed()
        }

        imageButtonAttach.setOnClickListener {
            requestPermissions()
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageListAdapter(chatClient = ChatClient.getInstance(), onImageClick = {
            openImage(this, it)
        })
        recyclerViewMessages.layoutManager = LinearLayoutManager(this@MessageListActivity)
        recyclerViewMessages.adapter = adapter
    }

    private fun fetchMessage() {
        ChatClient.getInstance().getMessages(channelId) {
            chats.run {
                clear()
                addAll(it)
                adapter.addAll(this)
                if (this.isNotEmpty()) {
                    recyclerViewMessages.smoothScrollToPosition(this.size - 1)
                }
            }
        }
    }

    private fun requestPermissions() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.CAMERA
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        dispatchTakePictureIntent();
                    }
                }

                override fun onPermissionRationaleShouldBeShown(p0: MutableList<PermissionRequest>?, token: PermissionToken) {
                    token.continuePermissionRequest();
                }

            })
            .onSameThread()
            .check()
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
            if (photoFile != null) {
                val photoURI: Uri = FileProvider.getUriForFile(this, "Gappein.provider", photoFile)
                this.photoFile = photoFile
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
            }
        }
    }

    //check user gallery upload
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {

            if (requestCode == REQUEST_TAKE_PHOTO) {
                sendImageMessage(photoFile)

            } else if (requestCode == REQUEST_GALLERY_PHOTO) {
                val selectedImage = data?.data
                photoFile = File(getRealPathFromUri(selectedImage))
                sendImageMessage(photoFile)
            }
        }
    }

    private fun sendImageMessage(photo: File?) {
        if (photo != null) {
            val file = ImageCompressor(this).compressToFile(photo)
            file?.toUri()?.let {
                ChatClient.getInstance().sendMessage(it, receiver.token, {
                    progress.hide()
                }, {
                    progress.show()
                }, {
                    progress.hide()
                })
            }
        }
    }

    override fun getClient() = ChatClient.getInstance()

}