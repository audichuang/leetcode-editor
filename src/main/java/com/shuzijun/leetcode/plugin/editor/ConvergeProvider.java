package com.shuzijun.leetcode.plugin.editor;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author shuzijun
 */
public class ConvergeProvider implements AsyncFileEditorProvider, DumbAware {


    @NotNull
    protected final FileEditorProvider[] editorProviders;
    protected final String[] names;

    @NotNull
    private final String myEditorTypeId;

    public ConvergeProvider(@NotNull FileEditorProvider[] editorProviders, @NotNull String[] names) {
        this.editorProviders = editorProviders;
        this.names = names;
        this.myEditorTypeId = "tab-provider[" + Arrays.stream(editorProviders).map(FileEditorProvider::getEditorTypeId).collect(Collectors.joining(";")) + "]";
    }


    @NotNull
    @Override
    public AsyncFileEditorProvider.Builder createEditorAsync(@NotNull final Project project, @NotNull final VirtualFile file) {
        final Builder[] builders = new Builder[editorProviders.length];
        for (int i = 0; i < editorProviders.length; i++) {
            builders[i] = getBuilderFromEditorProvider(editorProviders[i], project, file);
        }
        return new Builder() {
            @Override
            public TextEditor build() {
                FileEditor[] fileEditors = new FileEditor[editorProviders.length];
                try {
                    for (int i = 0; i < builders.length; i++) {
                        fileEditors[i] = builders[i].build();
                    }
                    return createSplitEditor(fileEditors, project, file);
                } catch (RuntimeException | Error e) {
                    // 建構composite本身失敗也要走這裡:已成功的子editor在composite接手前都沒有owner,
                    // 反向dispose已建立的子editor後才重拋,避免project/JCEF資源跨過project close洩漏。
                    for (int i = fileEditors.length - 1; i >= 0; i--) {
                        if (fileEditors[i] != null) {
                            Disposer.dispose(fileEditors[i]);
                        }
                    }
                    throw e;
                }
            }
        };
    }

    protected TextEditor createSplitEditor(@NotNull FileEditor[] fileEditors, Project project, VirtualFile file) {
        return new ConvergePreview(fileEditors, names, project, file);
    }

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return true;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return createEditorAsync(project, file).build();
    }

    @Override
    public @NotNull @NonNls String getEditorTypeId() {
        return myEditorTypeId;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }

    @NotNull
    public static Builder getBuilderFromEditorProvider(@NotNull final FileEditorProvider provider, @NotNull final Project project, @NotNull final VirtualFile file) {
        if (provider instanceof AsyncFileEditorProvider && overridesLegacyCreateEditorAsync(provider)) {
            return ((AsyncFileEditorProvider) provider).createEditorAsync(project, file);
        } else {
            return new Builder() {
                @Override
                public FileEditor build() {
                    return provider.createEditor(project, file);
                }
            };
        }
    }

    /**
     * 262 起平台自家 provider(TextEditorProvider/PsiAwareTextEditorProvider 等)只實作新版
     * suspend createFileEditor,legacy createEditorAsync 的 interface default 直接
     * throw IllegalStateException("Should not be called")(SDK bytecode 驗證)——
     * instanceof AsyncFileEditorProvider 不代表 legacy API 可呼叫。只對「真的 override 了
     * legacy 方法」的 provider(實務上是本 plugin 自己的 ConvergeProvider/ContentProvider)
     * 展開 async prep,其餘一律退回 build() 時 createEditor 的包裝。
     */
    private static boolean overridesLegacyCreateEditorAsync(@NotNull FileEditorProvider provider) {
        try {
            return provider.getClass().getMethod("createEditorAsync", Project.class, VirtualFile.class)
                    .getDeclaringClass() != AsyncFileEditorProvider.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}