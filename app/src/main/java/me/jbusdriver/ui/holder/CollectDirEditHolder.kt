package me.jbusdriver.ui.holder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.mvp.bean.AllFirstParentDBCategoryGroup
import me.jbusdriver.mvp.bean.Category
import me.jbusdriver.db.entity.Category as DBCategory


class CollectDirEditHolder(context: Context, parentCategory: Category) : BaseHolder(context) {

    private val delActionsParams = mutableSetOf<DBCategory>()
    private val addActionsParams = mutableSetOf<DBCategory>()
    private val collectDirs by lazy { categoryAdapter.data }


    val view by lazy {
        weakRef.get()?.let { context ->
            context.inflate(R.layout.layout_collect_dir_edit).apply {
                val tvCategoryAdd = findViewById<TextView>(R.id.tv_category_add)
                val llAddCategory = findViewById<LinearLayout>(R.id.ll_add_category)
                val llAddCategoryEdit = findViewById<LinearLayout>(R.id.ll_add_category_edit)
                val tvAddCategoryName = findViewById<EditText>(R.id.tv_add_category_name)
                val tvCategoryAddConfirm = findViewById<Button>(R.id.tv_category_add_confirm)
                val rvCategoryList = findViewById<RecyclerView>(R.id.rv_category_list)

                tvCategoryAdd.setOnClickListener {
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(llAddCategory, "alpha", 1.0f, 0.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "translationY", 60f, 0f).apply {
                                addListener(
                                    object : AnimatorListenerAdapter() {
                                        override fun onAnimationStart(animation: Animator) {
                                            llAddCategoryEdit.visibility = View.VISIBLE
                                        }

                                        override fun onAnimationEnd(animation: Animator) {
                                            llAddCategory.visibility = View.GONE
                                        }
                                    }
                                )
                            })
                        duration = 300
                    }.start()
                }

                tvCategoryAddConfirm.setOnClickListener {
                    val txt = tvAddCategoryName.text.toString().trim()
                    val add = if (txt.isNotBlank()) {
                        if (collectDirs.any { it.name == txt }) {
                            toast("$txt 分类已存在")
                            false
                        } else true
                    } else {
                        toast("请输入收藏夹名称")
                        false
                    }

                    if (add) {
                        val category = DBCategory(
                            pId = parentCategory.id ?: -1,
                            name = txt,
                            tree = "${parentCategory.id}/"
                        )
                        addActionsParams.add(category)
                        categoryAdapter.addData(category)
                        categoryAdapter.notifyItemChanged(categoryAdapter.data.size - 1)
                        tvAddCategoryName.setText("")
                    }

                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(llAddCategory, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "alpha", 1.0f, 0.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "translationY", 0f, -60f).apply {
                                addListener(
                                    object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            llAddCategory.visibility = View.VISIBLE
                                            llAddCategoryEdit.visibility = View.GONE
                                        }
                                    }
                                )
                            }
                        )
                        duration = 300
                    }.start()
                }

                rvCategoryList.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = categoryAdapter
                    categoryAdapter.setOnItemChildClickListener { _, view, position ->
                        when (view.id) {
                            R.id.tv_category_delete -> {
                                categoryAdapter.data.getOrNull(position)?.let {
                                    if (it.id in (1..10)) return@setOnItemChildClickListener
                                    categoryAdapter.data.removeAt(position)
                                    categoryAdapter.notifyItemRemoved(position)
                                    delActionsParams.add(it)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        } ?: error("CollectDirEditHolder can not inflate view for context is null ")
    }

    private var categoryAdapterRef: BaseQuickAdapter<DBCategory, BaseViewHolder>? = null

    private val categoryAdapter: BaseQuickAdapter<DBCategory, BaseViewHolder> by lazy {
        val exclude = AllFirstParentDBCategoryGroup.mapNotNull { it.value.id }
        object : BaseQuickAdapter<DBCategory, BaseViewHolder>(R.layout.layout_collect_dir_edit_item, null) {
            override fun convert(holder: BaseViewHolder, item: DBCategory) {
                holder.setText(R.id.tv_category_name, item.name)
                    .setVisible(R.id.tv_category_delete, item.id !in exclude)
                holder.itemView.findViewById<View>(R.id.tv_category_delete)?.setOnClickListener {
                    categoryAdapterRef?.data?.getOrNull(holder.adapterPosition)?.let { cat ->
                        if (cat.id in (1..10)) return@setOnClickListener
                        categoryAdapterRef?.data?.removeAt(holder.adapterPosition)
                        categoryAdapterRef?.notifyItemRemoved(holder.adapterPosition)
                        delActionsParams.add(cat)
                    }
                }
            }
        }.also { categoryAdapterRef = it }
    }

    fun showDialogWithData(data: Collection<DBCategory>, callback: (Set<DBCategory>, Set<DBCategory>) -> Unit) {
        delActionsParams.clear()
        addActionsParams.clear()
        categoryAdapter.setList(data.toList())

        val dialog = MaterialDialog(view.context)
        dialog.setContentView(view)
        dialog.setOnDismissListener {
            callback.invoke(delActionsParams, addActionsParams)
        }
        dialog.show()
    }
}
