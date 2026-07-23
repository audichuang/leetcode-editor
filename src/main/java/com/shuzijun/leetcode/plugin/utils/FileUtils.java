package com.shuzijun.leetcode.plugin.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.shuzijun.leetcode.plugin.model.CodeTypeEnum;
import com.shuzijun.leetcode.plugin.model.Constant;
import com.shuzijun.leetcode.plugin.model.LeetcodeEditor;
import com.shuzijun.leetcode.plugin.model.Question;
import com.shuzijun.leetcode.plugin.setting.ProjectConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * @author shuzijun
 */
public class FileUtils {


    public static void saveFile(String path, String body) {
        saveFile(new File(path), body);
    }

    public static void saveFile(File file, String body) {
        try {
            if (body == null) {
                return;
            }
            // mkdirs()/createNewFile()在并发场景下即使是另一线程刚创建成功也会返回false，
            // 所以返回false后要再查一次exists()，避免把这种情况误判为失败
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs() && !file.getParentFile().exists()) {
                LogUtils.LOG.error("创建目录失败:" + file.getParentFile().getPath());
                MessageUtils.showAllWarnMsg("error", PropertiesUtils.getInfo("file.save.failed", file.getPath()));
                return;
            }
            if (!file.exists() && !file.createNewFile() && !file.exists()) {
                LogUtils.LOG.error("创建文件失败:" + file.getPath());
                MessageUtils.showAllWarnMsg("error", PropertiesUtils.getInfo("file.save.failed", file.getPath()));
                return;
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream(file, false)) {
                fileOutputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException io) {
            LogUtils.LOG.error("保存文件错误", io);
            MessageUtils.showAllWarnMsg("error", PropertiesUtils.getInfo("file.save.failed", file.getAbsolutePath()));
        }
    }

    public static String getClearCommentFileBody(File file, CodeTypeEnum codeTypeEnum) {

        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        saveEditDocument(vf);
        StringBuilder code = new StringBuilder();
        try {
            String body = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> FileDocumentManager.getInstance().getDocument(vf).getText());
            if (StringUtils.isNotBlank(body)) {

                // ArrayList 而非 LinkedList：下面兩處用 get(i) 索引存取，LinkedList 的 get 是 O(n) → 整段 O(n²)
                List<String> codeList = new ArrayList<>();
                List<Integer> codeBegins = new ArrayList<>();
                List<Integer> codeEnds = new ArrayList<>();
                int lineCount = 0;
                // 區塊標記是常數，提到迴圈外算一次（原本每行都重新字串串接 + trim）
                String beginMark = trim(codeTypeEnum.getComment() + Constant.SUBMIT_REGION_BEGIN);
                String endMark = trim(codeTypeEnum.getComment() + Constant.SUBMIT_REGION_END);

                String[] lines = body.split("\r\n|\r|\n");
                for (String line : lines) {
                    if (StringUtils.isNotBlank(line) && trim(line).equals(beginMark)) {
                        codeBegins.add(lineCount);
                    } else if (StringUtils.isNotBlank(line) && trim(line).equals(endMark)) {
                        codeEnds.add(lineCount);
                    }
                    codeList.add(line);
                    lineCount++;
                }
                if (codeBegins.size() == codeEnds.size() && codeBegins.size() > 0) {
                    for (int s = 0; s < codeBegins.size(); s++) {
                        for (int i = codeBegins.get(s) + 1; i < codeEnds.get(s); i++) {
                            code.append(codeList.get(i)).append("\n");
                        }
                    }
                } else {
                    Boolean isCode = Boolean.FALSE;
                    for (int i = 0; i < codeList.size(); i++) {
                        String str = codeList.get(i);
                        if (!isCode) {
                            if (StringUtils.isNotBlank(str) && !str.startsWith(codeTypeEnum.getComment())) {
                                isCode = Boolean.TRUE;
                                code.append(str).append("\n");
                            } else {
                                continue;
                            }
                        } else {
                            code.append(str).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.LOG.error("getClearCommentFileBody error",e);
        }
        return code.toString();
    }

    // 維持原字元類別 [\s|\t]（\s 已含 tab、| 為字面 pipe），僅改為預編譯避免每次呼叫重 compile
    private static final Pattern TRIM_PATTERN = Pattern.compile("[\\s|\\t]");

    public static String trim(String str) {
        return TRIM_PATTERN.matcher(str).replaceAll("");
    }

    public static void openFileEditor(File file, Project project) {
        // VFS refresh 放背景緒，避免暫存路徑在慢磁碟/網路掛載時凍住 EDT；EDT 只負責開 editor
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            ApplicationManager.getApplication().invokeLater(() -> {
                OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vf);
                FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
            });
        });
    }


    public static void openFileEditorAndSaveState(File file, Project project, Question question, BiConsumer<LeetcodeEditor,String> consumer,boolean isOpen) {
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        LeetcodeEditor leetcodeEditor = ProjectConfig.getInstance(project).getDefEditor(URLUtils.getLeetcodeHost()+question.getFrontendQuestionId());
        leetcodeEditor.setFrontendQuestionId(URLUtils.getLeetcodeHost()+question.getFrontendQuestionId());
        leetcodeEditor.setTitleSlug(question.getTitleSlug());
        leetcodeEditor.setHost(URLUtils.getLeetcodeHost());
        consumer.accept(leetcodeEditor,vf.getPath());
        ProjectConfig.getInstance(project).addLeetcodeEditor(leetcodeEditor);

        if(isOpen) {
            ApplicationManager.getApplication().invokeLater(() -> {
                OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vf);
                FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
            });
        }

    }

    public static void saveEditDocument(VirtualFile file){
        if (FileDocumentManager.getInstance().isFileModified(file)) {
            try {
                ApplicationManager.getApplication().invokeLaterOnWriteThread((() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        FileDocumentManager.getInstance().saveDocument(FileDocumentManager.getInstance().getDocument(file));
                    });
                }));
            } catch (Throwable ignore) {
                LogUtils.LOG.error("自动保存文件错误", ignore);
            }

        }
    }

    public static String separator() {
        if (File.separator.equals("\\")) {
            return "/";
        } else {
            return "";
        }
    }

}
