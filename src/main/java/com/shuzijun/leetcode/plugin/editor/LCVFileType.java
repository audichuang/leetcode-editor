package com.shuzijun.leetcode.plugin.editor;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import icons.LeetCodeEditorIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.StandardCharsets;

public class LCVFileType extends LanguageFileType {
    public static final LCVFileType INSTANCE = new LCVFileType();

    private LCVFileType() {
        super(LCVLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return PluginConstant.LEETCODE_EDITOR_VIEW+"Doc";
    }

    @NotNull
    @Override
    public String getDescription() {
        return PluginConstant.LEETCODE_EDITOR_VIEW;
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return PluginConstant.LEETCODE_EDITOR_VIEW;
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return LeetCodeEditorIcons.LCV;
    }

    @Override
    public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
        return StandardCharsets.UTF_8.name();
    }
}