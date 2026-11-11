package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumThreadPageResult

/**
 * 敏感论坛内容关键字（如「性息」）：默认隐藏标题/分类名命中的板块、chip 与帖子，
 * 在设置页长按「顯示論壇」可切换显示。过滤仅发生在 ViewModel 展示边界，缓存数据保持原始。
 */
internal val SENSITIVE_FORUM_KEYWORDS = setOf("性息")

internal fun String?.containsSensitiveForumKeyword(): Boolean =
    this != null && SENSITIVE_FORUM_KEYWORDS.any { contains(it) }

/** 隐藏论坛首页标题命中的板块；分组被清空时一并移除。 */
internal fun ForumHomeData.withoutSensitiveBoards(): ForumHomeData =
    copy(
        boardGroups = boardGroups.mapNotNull { group ->
            val boards = group.boards.filterNot { it.name.containsSensitiveForumKeyword() }
            when {
                boards.size == group.boards.size -> group
                boards.isEmpty() -> null
                else -> group.copy(boards = boards)
            }
        }
    )

/** 隐藏帖子列表分类名命中的 chip 与帖子。 */
internal fun ForumThreadPageResult.withoutSensitiveThreads(): ForumThreadPageResult =
    copy(
        threads = threads.filterNot { it.typeName.containsSensitiveForumKeyword() },
        typeFilters = typeFilters.filterNot { it.name.containsSensitiveForumKeyword() }
    )

/** 被隐藏分类的 typeId 集合，用于判断当前选中的 chip 是否需要回退「全部」。 */
internal fun ForumThreadPageResult.sensitiveTypeIds(): Set<Int> =
    typeFilters.filter { it.name.containsSensitiveForumKeyword() }.map { it.typeId }.toSet()
