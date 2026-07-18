package com.wall.mob

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.io.File

class UploadBottomSheet : BottomSheetDialogFragment() {

    private var onUploadSuccess: (() -> Unit)? = null
    private lateinit var selectedVideoPath: TextView
    private lateinit var titleInput: EditText
    private lateinit var descInput: EditText
    private lateinit var uploaderInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var hashtagsInput: EditText
    private lateinit var progressContainer: LinearLayout
    private lateinit var submitButton: Button

    private var selectedFile: File? = null

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val file = uriToFile(requireContext(), it)
                selectedFile = file
                selectedVideoPath.text = "Selected: ${file.name} (${file.length() / 1024 / 1024} MB)"
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load video file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedVideoPath = view.findViewById(R.id.selectedVideoPath)
        titleInput = view.findViewById(R.id.uploadTitleInput)
        descInput = view.findViewById(R.id.uploadDescInput)
        uploaderInput = view.findViewById(R.id.uploadUploaderInput)
        categorySpinner = view.findViewById(R.id.uploadCategorySpinner)
        hashtagsInput = view.findViewById(R.id.uploadHashtagsInput)
        progressContainer = view.findViewById(R.id.uploadProgressContainer)
        submitButton = view.findViewById(R.id.submitUploadButton)

        view.findViewById<ImageButton>(R.id.closeUploadButton).setOnClickListener {
            dismiss()
        }

        view.findViewById<Button>(R.id.selectVideoButton).setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        val categories = arrayOf("entertainment", "music", "sports", "education", "gaming", "news", "other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        repo()?.loggedUserId()?.let { userId ->
            uploaderInput.setText(userId)
            uploaderInput.isEnabled = false
        }

        submitButton.setOnClickListener {
            uploadReel()
        }
    }

    private fun repo(): ReelsRepository? {
        val act = activity as? ReelActivity ?: return null
        return act.getRepo()
    }

    private fun uploadReel() {
        val file = selectedFile
        if (file == null) {
            Toast.makeText(context, "Please select a video file first", Toast.LENGTH_SHORT).show()
            return
        }

        val title = titleInput.text.toString().trim()
        val uploader = uploaderInput.text.toString().trim()
        val category = categorySpinner.selectedItem.toString()
        val desc = descInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val hashtags = hashtagsInput.text.toString().trim().takeIf { it.isNotEmpty() }

        if (title.isEmpty() || uploader.isEmpty()) {
            Toast.makeText(context, "Title and Uploader are required", Toast.LENGTH_SHORT).show()
            return
        }

        val repo = repo() ?: return
        if (!repo.isAdminUser()) {
            Toast.makeText(context, "Admin access is required to upload reels", Toast.LENGTH_SHORT).show()
            return
        }

        progressContainer.visibility = View.VISIBLE
        submitButton.isEnabled = false

        lifecycleScope.launch {
            repo.uploadVideo(
                file = file,
                title = title,
                category = category,
                uploader = uploader,
                description = desc,
                hashtagsCsv = hashtags
            ).onSuccess {
                progressContainer.visibility = View.GONE
                submitButton.isEnabled = true
                Toast.makeText(context, "Upload complete!", Toast.LENGTH_SHORT).show()
                onUploadSuccess?.invoke()
                dismiss()
            }.onFailure { err ->
                progressContainer.visibility = View.GONE
                submitButton.isEnabled = true
                Toast.makeText(context, "Upload failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri) ?: error("Failed to open input stream")
        val tempFile = File.createTempFile("upload_", ".mp4", context.cacheDir)
        tempFile.deleteOnExit()
        tempFile.outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    companion object {
        fun show(fm: androidx.fragment.app.FragmentManager, onSuccess: () -> Unit) {
            val fragment = UploadBottomSheet()
            fragment.onUploadSuccess = onSuccess
            fragment.show(fm, "upload")
        }
    }
}
