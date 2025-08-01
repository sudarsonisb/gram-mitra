package com.example.grammitra.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import java.io.File
import java.io.InputStream

data class ChatMessage(
    val text: String? = null,
    val isUser: Boolean = false,
    val imageBitmap: ImageBitmap? = null
)

@Composable
fun AskQuestionScreen(
    navController: NavController,
    runGemmaLocally: (String, String) -> String = { msg, model -> "This is a dummy reply for [$model]: $msg" }
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val chatHistory = remember { mutableStateListOf<ChatMessage>() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // --- Model Selector ---
    val modelOptions = listOf("Gemma-3n Base", "Gemma-3n Instruct", "Gemma-3n Fine-tuned")
    var selectedModel by remember { mutableStateOf(modelOptions.first()) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // --- Photo/Capture Logic ---
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    // Pick from gallery (Android 13+)
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            bitmap?.let { img ->
                chatHistory.add(ChatMessage(imageBitmap = img.asImageBitmap(), isUser = true))
            }
        }
    }
    // Pick from gallery (Android 12 and below)
    val legacyImagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            bitmap?.let { img ->
                chatHistory.add(ChatMessage(imageBitmap = img.asImageBitmap(), isUser = true))
            }
        }
    }
    // Take a photo using camera
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                val stream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
                bitmap?.let { img ->
                    chatHistory.add(ChatMessage(imageBitmap = img.asImageBitmap(), isUser = true))
                }
            }
        }
    }
    // For camera file creation
    fun createImageFile(): File {
        val dir = context.cacheDir
        return File.createTempFile("camera_", ".jpg", dir)
    }

    // --- Voice Placeholder ---
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            chatHistory.add(ChatMessage(text = "[Voice message: voice recognition coming soon]", isUser = true))
        }
    }

    var showImageSourceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- Model Selector Dropdown ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(10.dp))
            Box {
                OutlinedButton(onClick = { modelDropdownExpanded = true }) {
                    Text(selectedModel)
                }
                DropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    modelOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedModel = option
                                modelDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // ---- Chat List ----
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
            reverseLayout = false
        ) {
            items(chatHistory.size) { index ->
                ChatBubble(chatHistory[index])
                Spacer(Modifier.height(6.dp))
            }
        }

        // ---- Input Bar ----
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 2.dp, start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    showImageSourceDialog = true
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Camera/Gallery", tint = MaterialTheme.colorScheme.primary)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (input.text.isEmpty()) {
                                Text(
                                    "Type a message...",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
                Button(
                    onClick = {
                        if (input.text.isNotBlank()) {
                            chatHistory.add(ChatMessage(text = input.text, isUser = true))
                            val userMsg = input.text
                            input = TextFieldValue("")
                            keyboardController?.hide()
                            val botReply = runGemmaLocally(userMsg, selectedModel)
                            chatHistory.add(ChatMessage(text = botReply, isUser = false))
                        }
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }

    // ---- Dialog for Choosing Image Source ----
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            confirmButton = {},
            title = { Text("Select image source") },
            text = {
                Column {
                    TextButton(onClick = {
                        // Gallery
                        showImageSourceDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            legacyImagePickerLauncher.launch("image/*")
                        }
                    }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pick from Gallery")
                    }
                    TextButton(onClick = {
                        // Camera
                        showImageSourceDialog = false
                        val photoFile = createImageFile()
                        val photoUri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            photoFile
                        )
                        cameraImageUri = photoUri
                        cameraLauncher.launch(photoUri)
                    }) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Take Photo")
                    }
                }
            }
        )
    }
}

// --- ChatBubble and Avatar composables remain unchanged ---

@Composable
fun ChatBubble(message: ChatMessage) {
    val userBubbleColor = Color(0xFFE8F5E9)
    val botBubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val bubbleShape = RoundedCornerShape(18.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            BotAvatar()
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier
                .widthIn(max = 280.dp)
        ) {
            if (message.text != null) {
                Surface(
                    color = if (message.isUser) userBubbleColor else botBubbleColor,
                    shape = bubbleShape,
                    tonalElevation = 2.dp,
                    modifier = Modifier.shadow(1.dp, bubbleShape)
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(14.dp),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = if (!message.isUser) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
            if (message.imageBitmap != null) {
                Image(
                    bitmap = message.imageBitmap,
                    contentDescription = "User image",
                    modifier = Modifier
                        .padding(8.dp)
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
        if (message.isUser) {
            Spacer(Modifier.width(8.dp))
            UserAvatar()
        }
    }
}

@Composable
fun BotAvatar() {
    Surface(
        shape = CircleShape,
        color = Color(0xFFCCE6FF),
        modifier = Modifier.size(36.dp),
        shadowElevation = 3.dp
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = "Bot",
            tint = Color(0xFF1565C0),
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
fun UserAvatar() {
    Surface(
        shape = CircleShape,
        color = Color(0xFFFFF9C4),
        modifier = Modifier.size(36.dp),
        shadowElevation = 3.dp
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "User",
            tint = Color(0xFFFBC02D),
            modifier = Modifier.padding(6.dp)
        )
    }
}
