package com.shuzijun.leetcode.plugin.actions.toolbar;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.model.Constant;
import com.shuzijun.leetcode.plugin.model.Find;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.model.Tag;
import com.shuzijun.leetcode.plugin.utils.DataKeys;
import icons.LeetCodeEditorIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author shuzijun
 */
public class FindActionGroup extends ActionGroup implements DumbAware {

    private int i = 0;

    @Override
    public void update(AnActionEvent e) {
        // BGT-safe: read NavigatorAction off the event's (async, EDT-snapshotted)
        // DataContext instead of WindowFactory.getDataContext(), which walks
        // ToolWindowManager/ContentManager/Swing directly and asserts EDT.
        if (e == null) {
            return;
        }
        e.getPresentation().putClientProperty(com.intellij.openapi.actionSystem.ex.ActionUtil.SHOW_TEXT_IN_TOOLBAR, true);
        NavigatorAction navigatorAction = e.getData(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION);
        if (navigatorAction == null) {
            return;
        }

        String id = e.getActionManager().getId(this);
        List<Tag> tags = getTags(id, navigatorAction.getFind());

        if (tags != null && !tags.isEmpty()) {
            for (Tag tag : tags) {
                if (tag.isSelect()) {
                    e.getPresentation().setIcon(LeetCodeEditorIcons.FILTER);
                    return;
                }
            }
        }
        e.getPresentation().setIcon(null);
    }


    @Override
    public AnAction[] getChildren(AnActionEvent anActionEvent) {
        // ponytail: platform may still probe groups via the legacy getChildren(null) path
        // (pre-update-session code); degrade to no children instead of NPE-ing.
        // Same BGT-safety note as update() above: read via the event's DataContext,
        // never via WindowFactory.getDataContext().
        if (anActionEvent == null) {
            return AnAction.EMPTY_ARRAY;
        }
        NavigatorAction navigatorAction = anActionEvent.getData(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION);
        if (navigatorAction == null) {
            return AnAction.EMPTY_ARRAY;
        }

        List<AnAction> anActionList = Lists.newArrayList();
        String id = anActionEvent.getActionManager().getId(this);
        List<Tag> tags = getTags(id, navigatorAction.getFind());

        if (tags != null && !tags.isEmpty()) {
            for (Tag tag : tags) {
                anActionList.add(new FindTagAction(tag.getName(), tag, tags, onlyOne(id), getFilterKey(id)));
            }
        }
        AnAction[] anActions = new AnAction[anActionList.size()];
        anActionList.toArray(anActions);
        return anActions;
    }

    private List<Tag> getTags(String id, Find find) {
        return find.getFilter(getKey(id));
    }

    private boolean onlyOne(String id) {
        if (PluginConstant.LEETCODE_FIND_TAGS.equals(id)) {
            return false;
        }
        if (PluginConstant.LEETCODE_ALL_FIND_TAGS.equals(id)) {
            return false;
        }

        return true;
    }

    private String getFilterKey(String id) {
        String key = getKey(id);
        if (Constant.FIND_TYPE_LISTS.equalsIgnoreCase(key)) {
            return "listId";
        } else if (Constant.FIND_TYPE_CATEGORY.equalsIgnoreCase(key)) {
            return "categorySlug";
        } else if (Constant.CODETOP_FIND_TYPE_COMPANY.equalsIgnoreCase(key)) {
            return "listId";
        } else {
            return key;
        }
    }

    private String getKey(String id) {
        return id.replace(PluginConstant.LEETCODE_FIND_PREFIX, "").replace(PluginConstant.LEETCODE_ALL_FIND_PREFIX, "").replace(PluginConstant.LEETCODE_CODETOP_FIND_PREFIX, "");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // BGT is required to restore #766 search (IntelliJ 2026.x rejects the legacy
        // synchronous getChildren(null) probe that EDT-declared groups are reachable
        // through). It is safe here only because update()/getChildren() no longer touch
        // ToolWindowManager/ContentManager/Swing directly (that was the bug in the prior
        // attempt): they read NavigatorAction via AnActionEvent#getData(), which the
        // platform's PreCachedDataContext resolves by walking the Swing ancestor chain
        // and calling every DataProvider.getData() ONCE on EDT before the async update
        // session ever reaches BGT (see PreCachedDataContext's constructor-time
        // assertEventDispatchThread() + MySink.uiDataSnapshot(DataProvider), which calls
        // provider.getData(key) for every registered DataKey up front). NavigatorTabsPanel
        // is a plain DataProvider (via SimpleToolWindowPanel), so its getData() runs on
        // EDT during that snapshot, and update()/getChildren() on BGT only ever read the
        // already-resolved value — never Swing/ToolWindow state directly.
        return ActionUpdateThread.BGT;
    }
}
