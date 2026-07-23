package com.shuzijun.leetcode.plugin.editor.converge;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.shuzijun.leetcode.plugin.editor.ConvergePreview;
import com.shuzijun.leetcode.plugin.manager.NoteManager;
import com.shuzijun.leetcode.plugin.model.LeetcodeEditor;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.utils.URLUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.File;

/**
 * @author shuzijun
 */
public class NotePreview extends UserDataHolderBase implements FileEditor {


    private final Project project;
    private final LeetcodeEditor leetcodeEditor;


    private BorderLayoutPanel myComponent;
    private FileEditor fileEditor;

    private boolean isLoad = false;

    // 使用者只關閉此編輯器分頁時 project 不會 dispose，但 this 已 dispose，async 回呼需額外檢查
    private volatile boolean disposed = false;

    // 多個背景 worker（login reload/submit refresh/快速切換選列）可能亂序完成，
    // 每次發起新背景載入就遞增，callback 套用結果前比對，只有最新一次會生效
    private volatile int generation = 0;

    public NotePreview(Project project, LeetcodeEditor leetcodeEditor) {
        this.project = project;
        this.leetcodeEditor = leetcodeEditor;
    }

    @Override
    public @NotNull JComponent getComponent() {
        if (myComponent == null) {
            myComponent = JBUI.Panels.simplePanel();
            if (isLoad) {
                initComponent();
            }
        }
        return myComponent;
    }


    private void initComponent() {
        isLoad = true;
        int myGen = ++generation;
        NotePreview notePreview = this;
        JBLabel loadingLabel = new JBLabel("Loading...", new com.intellij.ui.AnimatedIcon.Default(), SwingConstants.LEFT);
        ApplicationManager.getApplication().invokeLater(() -> myComponent.addToCenter(loadingLabel));
        // 網路請求在背景執行緒完成，EDT 只負責組裝 UI
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                File fetchedFile = NoteManager.show(leetcodeEditor.getTitleSlug(), project, false);
                // VFS refresh 就地在 BGT 做完，EDT 只組裝 UI，避免慢磁碟/網路掛載凍住 EDT
                VirtualFile fetchedVf = (fetchedFile != null && fetchedFile.exists())
                        ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fetchedFile.toPath()) : null;
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 慢請求期間 editor/project 可能已被關閉/dispose，避免在已 dispose 的 UI 上操作；
                    // generation 不符代表已有更新的載入蓋過本次，捨棄過期結果
                    if (project.isDisposed() || disposed || myGen != generation) {
                        return;
                    }
                    buildComponent(notePreview, fetchedVf, loadingLabel);
                });
            } catch (Exception e) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed() || disposed || myGen != generation) {
                        return;
                    }
                    myComponent.remove(loadingLabel);
                    myComponent.addToCenter(new JBLabel(String.valueOf(e.getMessage())));
                });
            }
        });
    }

    private void buildComponent(NotePreview notePreview, VirtualFile vf, JBLabel loadingLabel) {
        try {
            if (fileEditor != null) {
                Disposer.dispose(fileEditor);
                fileEditor = null;
            }
            if (vf == null) {
                myComponent.addToCenter(new JBLabel("No note"));
            } else {
                // getProviders(Project,VirtualFile) 在 262 已 deprecated，公開替代是 getProviderList
                java.util.List<FileEditorProvider> editorProviders = FileEditorProviderManager.getInstance().getProviderList(project, vf);

                if (!editorProviders.isEmpty()) {
                    fileEditor = editorProviders.get(0).createEditor(project, vf);
                    Disposer.register(notePreview, fileEditor);
                } else {
                    fileEditor = new PsiAwareTextEditorProvider().createEditor(project, vf);
                    Disposer.register(notePreview, fileEditor);
                }
                myComponent.addToCenter(fileEditor.getComponent());
                myComponent.addToTop(createToolbarWrapper(fileEditor.getComponent()));

            }
        } catch (Exception e) {
            myComponent.addToCenter(new JBLabel(e.getMessage()));
        } finally {
            myComponent.remove(loadingLabel);
        }
    }


    private SplitEditorToolbar createToolbarWrapper(JComponent targetComponentForActions) {
        DefaultActionGroup actionGroup = (DefaultActionGroup) ActionManager.getInstance().getAction(PluginConstant.LEETCODE_EDITOR_NOTE);
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("Note" + ActionPlaces.TOOLBAR, actionGroup, true);
        actionToolbar.setTargetComponent(targetComponentForActions);
        SplitEditorToolbar splitEditorToolbar = new SplitEditorToolbar(null, actionToolbar);
        if (URLUtils.leetcodecn.equals(leetcodeEditor.getHost())) {
            splitEditorToolbar.add(new JBLabel("网站已更换新笔记功能,此功能后续同步官网,请备份此版本下的笔记."), 0);
        }
        return splitEditorToolbar;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return null;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
        return PluginConstant.LEETCODE_EDITOR_TAB_VIEW + " Note";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
        if (state instanceof ConvergePreview.TabFileEditorState) {
            if (!isLoad && ((ConvergePreview.TabFileEditorState) state).isLoad()) {
                initComponent();
            } else {
                if (fileEditor != null) {
                    // async=true：EDT 上的 tab 切換不等 VFS refresh 完成，避免慢磁碟/網路掛載凍住 UI
                    RefreshQueue.getInstance().refresh(true, false, null, fileEditor.getFile());
                }
            }
        } else if (state instanceof ConvergePreview.LoginState) {
            ConvergePreview.LoginState loginState = (ConvergePreview.LoginState) state;
            if (isLoad && loginState.isLogin()) {
                if (loginState.isSelect()) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        myComponent.removeAll();
                        myComponent.updateUI();
                        initComponent();
                    });
                } else {
                    isLoad = false;
                }
            }
        }
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public void dispose() {
        disposed = true;
        if (fileEditor != null) {
            Disposer.dispose(fileEditor);
        }
    }

    @Override
    public @Nullable VirtualFile getFile() {
        if (fileEditor != null) {
            return fileEditor.getFile();
        } else {
            return null;
        }
    }
}
