package ch.endte.syncmatica.litematica.gui;

import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.Collections;
import java.util.List;

public enum MainMenuButtonType implements IButtonType {

    VIEW_SYNCMATICS("syncmatica.gui.button.view_syncmatics");

    private final String labelKey;

    MainMenuButtonType(final String labelKey) {
        this.labelKey = labelKey;
    }

    @Override
    public IGuiIcon getIcon() {
        return null;
    }

    @Override
    public String getTranslatedKey() {
        return StringUtils.translate(labelKey);
    }

    @Override
    public List<String> getHoverStrings() {
        return Collections.emptyList();
    }

    @Override
    public IButtonActionListener getButtonListener() {
        return null;
    }

    @Override
    public boolean isActive() {
        return false;
    }
}