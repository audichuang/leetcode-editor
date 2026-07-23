package com.shuzijun.leetcode.plugin.actions.toolbar;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.components.JBPanel;
import com.shuzijun.leetcode.plugin.actions.AbstractAction;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import com.shuzijun.leetcode.plugin.utils.LogUtils;
import com.shuzijun.leetcode.plugin.utils.MessageUtils;
import com.shuzijun.leetcode.plugin.utils.PropertiesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author shuzijun
 */
public class ClearAllAction extends AbstractAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent anActionEvent, Config config) {
        Project project = anActionEvent.getProject();

        // 只有 dialog 需要 EDT；確認結果拿出來後,刪檔一律留在背景緒(這個方法本身就是
        // AbstractAction 的 Task.Backgroundable.run() 呼叫進來的,已經不在 EDT 上)。
        boolean[] confirmed = new boolean[1];
        ApplicationManager.getApplication().invokeAndWait(() -> {
            ClearAllWarningPanel dialog = new ClearAllWarningPanel(project);
            dialog.setTitle("Clear All");
            confirmed[0] = dialog.showAndGet();
        });
        if (!confirmed[0]) {
            return;
        }

        String filePath = PersistentConfig.getInstance().getTempFilePath();
        File file = new File(filePath);
        if (!file.exists() || !file.isDirectory()) {
            MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("clear.success"));
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Clear All", true) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                try {
                    delFile(file, project);
                    MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("clear.success"));
                } catch (ProcessCanceledException pce) {
                    throw pce;
                } catch (Exception ee) {
                    LogUtils.LOG.error("清理文件错误", ee);
                    MessageUtils.getInstance(project).showErrorMsg("error", PropertiesUtils.getInfo("clear.failed"));
                }
            }
        });
    }

    public void delFile(File file, Project project) {
        if (!file.exists()) {
            return;
        }

        List<VirtualFile> knownFiles = new ArrayList<>();
        collectKnownVirtualFiles(file, knownFiles);
        if (!knownFiles.isEmpty()) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                for (VirtualFile vf : knownFiles) {
                    if (fileEditorManager.isFileOpen(vf)) {
                        fileEditorManager.closeFile(vf);
                    }
                }
            });
        }

        // 刪除前先記住根目錄既有的 VirtualFile,刪完再一次非同步 refresh，不逐檔同步 refresh。
        VirtualFile rootVf = LocalFileSystem.getInstance().findFileByIoFile(file);
        deleteRecursively(file);
        if (rootVf != null) {
            RefreshQueue.getInstance().refresh(true, true, null, rootVf);
        }
    }

    private void collectKnownVirtualFiles(File file, List<VirtualFile> result) {
        ProgressManager.checkCanceled();
        VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (vf != null) {
            result.add(vf);
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectKnownVirtualFiles(child, result);
                }
            }
        }
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        ProgressManager.checkCanceled();
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private class ClearAllWarningPanel extends DialogWrapper {

        private JPanel jpanel;

        public ClearAllWarningPanel(@Nullable Project project) {
            super(project, true);
            jpanel = new JBPanel();
            jpanel.add(new JLabel("Clear All File？"));
            jpanel.setMinimumSize(new Dimension(200, 100));
            setModal(true);
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            return jpanel;
        }
    }
}
