package com.countwearables.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.countwearables.app.R
import com.countwearables.app.data.model.ClothingItem
import com.countwearables.app.databinding.ActivityAddEditItemBinding
import com.countwearables.app.ui.viewmodel.AuthViewModel
import com.countwearables.app.ui.viewmodel.ClothingViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Add/Edit Item Activity - Handles creating and editing clothing items.
 * Includes image capture and gallery selection functionality.
 */
class AddEditItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditItemBinding
    private val authViewModel: AuthViewModel by viewModels()
    private val clothingViewModel: ClothingViewModel by viewModels()

    private var currentItem: ClothingItem? = null
    private var imageUri: Uri? = null
    private var imageFilePath: String = ""

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }

    // Permission launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchGallery()
        } else {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private val imageCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Image captured successfully
            if (imageFilePath.isNotEmpty()) {
                binding.ivItem.setImageURI(Uri.fromFile(File(imageFilePath)))
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                imageUri = it
                // Copy image to app-specific storage
                saveImageToAppStorage(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Check if editing or adding new item
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1)
        if (itemId != -1L) {
            // Edit mode
            binding.btnDelete.visibility = View.VISIBLE
            clothingViewModel.getItemById(itemId)
        }

        setupObservers()
        setupClickListeners()
        setupAutoCompleteFields()
    }

    private fun setupObservers() {
        clothingViewModel.currentItem.observe(this) { item ->
            item?.let {
                currentItem = it
                populateFields(it)
            }
        }

        clothingViewModel.itemResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, R.string.item_saved, Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }

        clothingViewModel.isLoading.observe(this) { isLoading ->
            binding.btnSave.isEnabled = !isLoading
        }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            if (validateAndSaveItem()) {
                // Item saved successfully
            }
        }

        binding.btnDelete.setOnClickListener {
            currentItem?.let { item ->
                clothingViewModel.deleteItem(item.id, item.userId)
                finish()
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }
    }

    private fun setupAutoCompleteFields() {
        // Category dropdown
        val categories = ClothingItem.DEFAULT_CATEGORIES.toTypedArray()
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.editCategory.setAdapter(categoryAdapter)

        // Size dropdown
        val sizes = ClothingItem.DEFAULT_SIZES.toTypedArray()
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sizes)
        binding.editSize.setAdapter(sizeAdapter)
    }

    private fun populateFields(item: ClothingItem) {
        binding.editName.setText(item.name)
        binding.editCategory.setText(item.category, false)
        binding.editQuantity.setText(item.quantity.toString())
        binding.editColor.setText(item.color)
        binding.editSize.setText(item.size, false)
        binding.editNotes.setText(item.notes)

        // Load image if available
        if (item.imagePath.isNotEmpty()) {
            val imageFile = File(item.imagePath)
            if (imageFile.exists()) {
                Glide.with(this)
                    .load(imageFile)
                    .into(binding.ivItem)
            }
        }
    }

    private fun validateAndSaveItem(): Boolean {
        val name = binding.editName.text.toString().trim()
        val category = binding.editCategory.text.toString().trim()
        val quantityStr = binding.editQuantity.text.toString().trim()
        val color = binding.editColor.text.toString().trim()
        val size = binding.editSize.text.toString().trim()
        val notes = binding.editNotes.text.toString().trim()

        // Validate required fields
        if (name.isEmpty()) {
            binding.textInputName.error = getString(R.string.field_required)
            return false
        }
        binding.textInputName.error = null

        if (category.isEmpty()) {
            binding.textInputCategory.error = getString(R.string.field_required)
            return false
        }
        binding.textInputCategory.error = null

        if (quantityStr.isEmpty()) {
            binding.textInputQuantity.error = getString(R.string.field_required)
            return false
        }

        val quantity = quantityStr.toIntOrNull()
        if (quantity == null || quantity < 1) {
            binding.textInputQuantity.error = getString(R.string.quantity_must_be_positive)
            return false
        }
        binding.textInputQuantity.error = null

        val userId = authViewModel.getCurrentUserId()
        if (userId == -1L) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return false
        }

        val item = currentItem?.copy(
            name = name,
            category = category,
            quantity = quantity,
            color = color,
            size = size,
            notes = notes,
            imagePath = imageFilePath
        ) ?: ClothingItem(
            userId = userId,
            name = name,
            category = category,
            quantity = quantity,
            color = color,
            size = size,
            notes = notes,
            imagePath = imageFilePath,
            dateAdded = System.currentTimeMillis()
        )

        if (currentItem != null) {
            // Update existing item
            clothingViewModel.updateItem(item)
        } else {
            // Add new item
            clothingViewModel.addItem(item)
        }

        return true
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkGalleryPermissionAndLaunch() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                launchGallery()
            }
            else -> {
                galleryPermissionLauncher.launch(permission)
            }
        }
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            // Create a file to store the image
            val photoFile = createImageFile()
            if (photoFile != null) {
                imageFilePath = photoFile.absolutePath
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    photoFile
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                imageCaptureLauncher.launch(intent)
            }
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveImageToAppStorage(sourceUri: Uri) {
        try {
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(storageDir, "JPEG_${timeStamp}.jpg")

            contentResolver.openInputStream(sourceUri)?.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            imageFilePath = imageFile.absolutePath
            binding.ivItem.setImageURI(Uri.fromFile(imageFile))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }
}