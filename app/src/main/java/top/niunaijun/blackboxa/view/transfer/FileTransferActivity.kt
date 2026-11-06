package top.niunaijun.blackboxa.view.transfer

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.FileUtils
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.ActivityFileTransferBinding
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.BaseActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileTransferActivity : BaseActivity() {

    private val binding: ActivityFileTransferBinding by inflate()
    private var currentDir: File = BEnvironment.getVirtualRoot()
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: FileAdapter

    private var pendingExportFile: File? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            var count = 0
            for (uri in uris) {
                val fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}"
                val targetFile = File(currentDir, fileName)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    count++
                } catch (e: Exception) {
                    toast("Failed to import $fileName: ${e.message}")
                }
            }
            toast("Imported $count files from Host")
            refreshFileList()
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && pendingExportFile != null) {
            val file = pendingExportFile!!
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input ->
                        input.copyTo(output)
                    }
                }
                toast("Exported ${file.name} to Host successfully")
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbar(binding.toolbarLayout.toolbar, R.string.filter, true)
        binding.toolbarLayout.toolbar.title = "File Transfer"

        adapter = FileAdapter(fileList, { file ->
            if (file.isDirectory) {
                currentDir = file
                refreshFileList()
            } else {
                showFileActions(file)
            }
        }, { file ->
            showFileActions(file)
            true
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnImportHost.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        binding.btnExportHost.setOnClickListener {
            toast("Select a file from the list to export to Host")
        }

        refreshFileList()
    }

    private fun refreshFileList() {
        binding.tvCurrentPath.text = "Virtual Path: ${currentDir.absolutePath}"
        fileList.clear()
        val files = currentDir.listFiles()
        if (files != null) {
            val dirs = files.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            val regFiles = files.filter { !it.isDirectory }.sortedBy { it.name.lowercase() }
            fileList.addAll(dirs)
            fileList.addAll(regFiles)
        }
        adapter.notifyDataSetChanged()
    }

    private fun showFileActions(file: File) {
        val items = if (file.isDirectory) {
            listOf("Open Folder", "Delete Folder")
        } else {
            listOf("Export to Host", "Delete File")
        }

        MaterialDialog(this).show {
            title(text = file.name)
            listItems(items = items) { _, index, _ ->
                if (index == 0) {
                    if (file.isDirectory) {
                        currentDir = file
                        refreshFileList()
                    } else {
                        pendingExportFile = file
                        exportLauncher.launch(file.name)
                    }
                } else if (index == 1) {
                    if (file.isDirectory) {
                        FileUtils.deleteDir(file)
                    } else {
                        file.delete()
                    }
                    toast("Deleted ${file.name}")
                    refreshFileList()
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun onBackPressed() {
        val root = BEnvironment.getVirtualRoot()
        if (currentDir != root && currentDir.parentFile != null && currentDir.absolutePath.startsWith(root.absolutePath)) {
            currentDir = currentDir.parentFile!!
            refreshFileList()
        } else {
            super.onBackPressed()
        }
    }

    class FileAdapter(
        private val list: List<File>,
        private val onClick: (File) -> Unit,
        private val onLongClick: (File) -> Boolean
    ) : RecyclerView.Adapter<FileAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = list[position]
            val prefix = if (file.isDirectory) "[Folder] " else "[File] "
            holder.text.text = "$prefix ${file.name}"
            holder.itemView.setOnClickListener { onClick(file) }
            holder.itemView.setOnLongClickListener { onLongClick(file) }
        }

        override fun getItemCount(): Int = list.size
    }
}
