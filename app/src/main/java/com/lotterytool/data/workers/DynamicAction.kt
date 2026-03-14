package com.lotterytool.data.workers

import com.lotterytool.data.repository.DynamicInfoRepository
import com.lotterytool.data.repository.actionRepository.FollowRepository
import com.lotterytool.data.repository.actionRepository.LikeRepository
import com.lotterytool.data.repository.actionRepository.ReplyRepository
import com.lotterytool.data.repository.actionRepository.RepostRepository
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.action.ActionEntity
import com.lotterytool.utils.FetchResult
import com.lotterytool.utils.ReplyMessage
import com.lotterytool.utils.RepostContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random

class DynamicAction @Inject constructor(
    private val dynamicInfoRepository: DynamicInfoRepository,
    private val actionDao: ActionDao,
    private val followRepository: FollowRepository,
    private val likeRepository: LikeRepository,
    private val replyRepository: ReplyRepository,
    private val repostRepository: RepostRepository,
) {
    /**
     * 批量执行所有动态的操作（转发、点赞、评论、关注）。
     *
     * @param forceRefresh true 时跳过"已有 action"检查，强制重新执行所有动作（重试场景）。
     *                     false 时保持原有行为，已有 action 记录的动态直接跳过。
     */
    suspend fun allAction(
        articleId: Long,
        cookie: String,
        csrf: String,
        forceRefresh: Boolean = false,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ) {
        val successfulIds = dynamicInfoRepository.getSuccessfulDynamicIds(articleId)
        val total = successfulIds.size

        if (total == 0) {
            onProgress(0, 0)
            return
        }

        for ((index, dynamicId) in successfulIds.withIndex()) {
            if (!currentCoroutineContext().isActive) break

            // forceRefresh=true 时跳过此检查，强制重新执行动作
            if (!forceRefresh) {
                val existingAction = actionDao.getActionById(dynamicId)
                if (existingAction != null) {
                    onProgress(index + 1, total)
                    continue
                }
            }

            try {
                performAction(
                    dynamicId,
                    cookie,
                    csrf,
                    content = RepostContent.getRandom(),
                    message = ReplyMessage.getRandom()
                )
                onProgress(index + 1, total)

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onProgress(index + 1, total)
            }

            if (index < total - 1) {
                delay(Random.nextLong(1000, 2000))
            }
        }

        withContext(NonCancellable) {
            onProgress(total, total)
        }
    }

    /**
     * 执行单个动态的全套动作并存库。
     */
    suspend fun performAction(
        dynamicId: Long,
        cookie: String,
        csrf: String,
        content: String,
        message: String
    ): FetchResult<Unit> = withContext(NonCancellable) {
        return@withContext try {
            val info = dynamicInfoRepository.getInfoById(dynamicId)
                ?: return@withContext FetchResult.Error("未找到动态详情")

            val existingAction = actionDao.getActionById(dynamicId)

            fun shouldExecute(currentRes: String?): Boolean {
                val idempotentErrors = listOf("成功", "已经关注用户，无法重复关注")
                return currentRes == null || currentRes !in idempotentErrors
            }

            // --- 1. 转发 ---
            val repostRes = if (shouldExecute(existingAction?.repostResult)) {
                when (val res = repostRepository.executeRepost(cookie, csrf, dynamicId, content)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.repostResult ?: "成功"
            }

            delay(Random.nextLong(2000, 3000))

            // --- 2. 点赞 ---
            val likeRes = if (shouldExecute(existingAction?.likeResult)) {
                when (val res = likeRepository.executeLike(cookie, csrf, dynamicId)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.likeResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // --- 3. 评论 ---
            val replyRes = if (shouldExecute(existingAction?.replyResult)) {
                when (val res = replyRepository.executeReply(cookie, 11, info.rid, message, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.replyResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // --- 4. 关注 ---
            val followRes = if (shouldExecute(existingAction?.followResult)) {
                when (val res = followRepository.executeFollow(cookie, info.uid, 1, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.followResult ?: "成功"
            }

            // --- 5. 存库 ---
            val actionEntity = ActionEntity(
                dynamicId = dynamicId,
                repostResult = repostRes,
                likeResult = likeRes,
                replyResult = replyRes,
                followResult = followRes
            )
            actionDao.insertAction(actionEntity)

            val realError = listOf(repostRes, likeRes, replyRes, followRes)
                .find { it != "成功" && it != "已经关注用户，无法重复关注" }

            if (realError != null) FetchResult.Error(realError) else FetchResult.Success(Unit)

        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "执行动作时发生异常")
        }
    }
}