package com.lotterytool.data.workers

import com.lotterytool.data.repository.actionRepository.FollowRepository
import com.lotterytool.data.repository.actionRepository.LikeRepository
import com.lotterytool.data.repository.actionRepository.ReplyRepository
import com.lotterytool.data.repository.actionRepository.RepostRepository
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.action.ActionEntity
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
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

class DynamicAction @Inject constructor(
    private val dynamicInfoDao: DynamicInfoDao,
    private val actionDao: ActionDao,
    private val followRepository: FollowRepository,
    private val likeRepository: LikeRepository,
    private val replyRepository: ReplyRepository,
    private val repostRepository: RepostRepository,
) {
    /**
     * 模仿 processAndStoreAllDynamics 的逻辑
     * 批量执行所有动态的操作（转发、点赞、评论、关注）
     */
    suspend fun allAction(
        articleId: Long,
        cookie: String,
        csrf: String,
        onProgress: suspend (current: Int, total: Int, error: String?) -> Unit
    ) {
        val successfulIds = dynamicInfoDao.getSuccessfulDynamicIds(articleId)
        val total = successfulIds.size

        if (total == 0) {
            onProgress(0, 0, "没有可执行的动态")
            return
        }

        for ((index, dynamicId) in successfulIds.withIndex()) {
            if (!currentCoroutineContext().isActive) {
                break
            }

            // 获取已经处理的
            val existingAction = actionDao.getActionById(dynamicId)
            if (existingAction != null) {
                onProgress(index + 1, total, null)
                continue
            }

            try {
                val result = performAction(
                    dynamicId,
                    cookie,
                    csrf,
                    content = RepostContent.getRandom(),
                    message = ReplyMessage.getRandom()
                )

                if (result is FetchResult.Error) {
                    onProgress(index + 1, total, "error")
                } else {
                    onProgress(index + 1, total, null)
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onProgress(index + 1, total, "error")
            }

            if (index < total - 1) {
                delay(3000)
            }
        }

        withContext(NonCancellable) {
            onProgress(total, total, null)
        }
    }

    /**
     * 执行单个动态的全套动作并存库
     */
    suspend fun performAction(
        dynamicId: Long,
        cookie: String,
        csrf: String,
        content: String,
        message: String
    ): FetchResult<Unit> = withContext(NonCancellable) {
        // 使用 NonCancellable 保证：一旦开始执行某个 ID 的操作，
        // 即使 App 被划掉，也会坚持跑完 转发->延迟->点赞->延迟->评论->延迟->关注->存库 的全过程。
        return@withContext try {
            val info = dynamicInfoDao.getInfoById(dynamicId)
                ?: return@withContext FetchResult.Error("未找到动态详情")

            // 1. 执行转发
            val repostRes =
                when (val res = repostRepository.executeRepost(cookie, csrf, dynamicId, content)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }

            // 注意：在 NonCancellable 块内的 delay 也是不会被取消的
            delay(3000)

            // 2. 执行点赞
            val likeRes = when (val res = likeRepository.executeLike(cookie, csrf, dynamicId)) {
                is FetchResult.Success -> "成功"
                is FetchResult.Error -> res.message
            }

            delay(3000)

            // 3. 执行评论 (使用详情解析阶段存下的 rid)
            val replyRes =
                when (val res = replyRepository.executeReply(cookie, 11, info.rid, message, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }

            delay(3000)

            // 4. 执行关注 (使用详情解析阶段存下的 uid)
            val followRes =
                when (val res = followRepository.executeFollow(cookie, info.uid, 1, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }

            delay(3000)

            // 5. 将这一整套动作的结果存入数据库
            val actionEntity = ActionEntity(
                dynamicId = dynamicId,
                repostResult = repostRes,
                likeResult = likeRes,
                replyResult = replyRes,
                followResult = followRes
            )
            actionDao.insertAction(actionEntity)

            FetchResult.Success(Unit)
        } catch (e: Exception) {
            // 如果是由于网络中断或其他硬错误导致，返回错误
            FetchResult.Error(e.localizedMessage ?: "执行动作时发生异常")
        }
    }
}