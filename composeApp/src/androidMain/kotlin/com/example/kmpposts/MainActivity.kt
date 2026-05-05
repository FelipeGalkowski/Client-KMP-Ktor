package com.example.kmpposts.android

import Post
import PostRepository
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ─── ViewModel ───────────────────────────────────────────────
class PostsViewModel : ViewModel() {

    private val repository = PostRepository()
    private val limit = 10

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var currentPage = 1
    private var currentUserId: Int? = null
    private var hasMoreItems = true

    init {
        loadPosts(reset = true)
    }

    fun loadPosts(reset: Boolean = false, userId: Int? = currentUserId) {
        if (reset) {
            currentPage = 1
            hasMoreItems = true
            currentUserId = userId
            _posts.value = emptyList()
        }

        if (_isLoading.value || _isLoadingMore.value || !hasMoreItems) return

        viewModelScope.launch {
            if (currentPage == 1) _isLoading.value = true
            else _isLoadingMore.value = true

            _errorMessage.value = null

            try {
                val result = repository.getPosts(
                    page = currentPage,
                    limit = limit,
                    userId = currentUserId
                )
                if (result.isEmpty()) {
                    hasMoreItems = false
                } else {
                    _posts.value = _posts.value + result
                    currentPage++
                    if (result.size < limit) hasMoreItems = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar posts: ${e.message}"
            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    fun loadMore() {
        if (!_isLoading.value && !_isLoadingMore.value && hasMoreItems) {
            loadPosts()
        }
    }

    fun filterByUser(userId: Int?) {
        loadPosts(reset = true, userId = userId)
    }
}

// ─── Activity ────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PostsScreen()
            }
        }
    }
}

// ─── Tela Principal ──────────────────────────────────────────
@Composable
fun PostsScreen(viewModel: PostsViewModel = viewModel()) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val listState = rememberLazyListState()
    var userIdInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Detecta quando chegou perto do final
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1976D2))
                .padding(16.dp)
        ) {
            Text(
                text = "📋 Posts JSONPlaceholder",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Campo de filtro por userId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = userIdInput,
                onValueChange = { userIdInput = it.filter { c -> c.isDigit() } },
                label = { Text("Filtrar por User ID") },
                placeholder = { Text("Ex: 1, 2, 3...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.filterByUser(userIdInput.toIntOrNull())
                    }
                ),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.filterByUser(userIdInput.toIntOrNull())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("Buscar")
            }

            if (userIdInput.isNotEmpty()) {
                OutlinedButton(onClick = {
                    userIdInput = ""
                    focusManager.clearFocus()
                    viewModel.filterByUser(null)
                }) {
                    Text("✕")
                }
            }
        }

        // Estados
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ $errorMessage", color = Color.Red)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadPosts(reset = true) }) {
                            Text("Tentar novamente")
                        }
                    }
                }
            }

            posts.isEmpty() && !isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum post encontrado.", color = Color.Gray)
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(posts) { _, post ->
                        PostCard(post = post)
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Card de Post ─────────────────────────────────────────────
@Composable
fun PostCard(post: Post) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Post #${post.id}",
                    fontSize = 12.sp,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "User ${post.userId}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = post.title.replaceFirstChar { it.uppercase() },
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = post.body,
                fontSize = 13.sp,
                color = Color(0xFF555555),
                lineHeight = 18.sp
            )
        }
    }
}