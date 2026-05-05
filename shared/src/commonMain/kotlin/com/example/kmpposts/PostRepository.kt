class PostRepository(
    private val apiService: PostApiService = PostApiService()
) {
    suspend fun getPosts(
        page: Int,
        limit: Int,
        userId: Int? = null
    ): List<Post> {
        return apiService.getPosts(page = page, limit = limit, userId = userId)
    }
}